/*
 * Copyright 2026 Hitesh Soliwal and Contributors to the Synexia Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.m3.collections.guava.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import org.junit.Test;

/** Partial AVL topology, exported-count lifetime, frozen-oracle and callback contracts. */
public final class M3TreeMultisetArrayContractTest {
  private static final String PACKAGE = "com.m3.collections.guava.collect.";

  @Test
  public void avlTopologyUsesPrimitiveLanesAndReusesClearedSlots() throws Exception {
    TreeMultiset<Integer> tree = TreeMultiset.create();
    Object arena = field(tree, "rootReference");
    for (Class<?> nested : TreeMultiset.class.getDeclaredClasses()) {
      assertFalse("No per-element node class", nested.getSimpleName().equals("AvlNode"));
    }
    for (Field member : arena.getClass().getDeclaredFields()) {
      if (!Modifier.isStatic(member.getModifiers())) {
        assertTrue(member.toString(), member.getType().isPrimitive() || member.getType().isArray());
        if (member.getType().isArray() && !member.getType().getComponentType().isPrimitive()) {
          assertTrue(
              Arrays.asList("elements", "exportedCounts", "activeCursors")
                  .contains(member.getName()));
        }
      }
    }
    assertTrue(field(arena, "elements") instanceof Object[]);
    assertTrue(field(arena, "elemCount") instanceof int[]);
    assertTrue(field(arena, "totalCount") instanceof long[]);
    assertTrue(field(arena, "height") instanceof byte[]);
    assertTrue(field(arena, "generation") instanceof long[]);
    SortedMultiset<Integer> view = tree.tailMultiset(100, BoundType.CLOSED);
    assertSame(arena, field(view, "rootReference"));
    for (int i = 0; i < 4096; i++) {
      tree.add(i, i % 7 + 1);
      if (i % 127 == 0) {
        assertArena(tree);
      }
    }
    assertArena(tree);
    int capacity = ((Object[]) field(arena, "elements")).length;
    List<Integer> shuffled = new ArrayList<>(tree.elementSet());
    Collections.shuffle(shuffled, new Random(0x4d33545245454cL));
    for (int i = 0; i < shuffled.size(); i++) {
      tree.setCount(shuffled.get(i), 0);
      if (i % 73 == 0) {
        assertArena(tree);
      }
    }
    assertArena(tree);
    assertTrue(tree.isEmpty());
    for (int cycle = 0; cycle < 20; cycle++) {
      for (int i = 0; i < 1024; i++) {
        tree.add(i + 10000, i % 5 + 1);
      }
      assertArena(tree);
      tree.clear();
      assertArena(tree);
      assertEquals(capacity, ((Object[]) field(arena, "elements")).length);
    }
    tree.add(1, Integer.MAX_VALUE);
    tree.add(2, Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, tree.size());
    assertThrows(IllegalArgumentException.class, () -> tree.add(1, 1));
    assertEquals(Integer.MAX_VALUE, tree.count(1));
    assertArena(tree);
  }

  @Test
  public void retainedEntryKeepsOriginalElementAndReattachesOnlyByComparator() throws Exception {
    Comparator<Token> byRank = Comparator.comparingInt(value -> value.rank);
    TreeMultiset<Token> tree = TreeMultiset.create(byRank);
    Token original = new Token(5, "original");
    tree.add(original, 3);
    Multiset.Entry<Token> held = tree.firstEntry();
    SortedMultiset<Token> view = tree.headMultiset(new Token(8, "bound"), BoundType.CLOSED);
    Multiset.Entry<Token> viewHeld = view.firstEntry();
    tree.setCount(new Token(5, "equal-query"), 9);
    assertEquals(9, held.getCount());
    tree.remove(new Token(5, "delete-query"), Integer.MAX_VALUE);
    assertEquals(0, held.getCount());
    for (int i = 0; i < 1000; i++) {
      tree.add(new Token(i + 20, "reuse"), 1);
    }
    assertSame(original, held.getElement());
    assertEquals(0, held.getCount());
    Token replacement = new Token(5, "replacement");
    tree.add(replacement, 17);
    assertSame(replacement, tree.firstEntry().getElement());
    assertSame(original, held.getElement());
    assertSame(original, viewHeld.getElement());
    assertEquals(17, held.getCount());
    assertEquals(17, viewHeld.getCount());
    tree.clear();
    tree.add(new Token(5, "after-clear"), 23);
    assertSame(original, held.getElement());
    assertEquals(23, held.getCount());
    Multiset.Entry<Token> polled = tree.pollFirstEntry();
    tree.add(new Token(5, "after-poll"), 4);
    assertEquals(23, polled.getCount());
    assertEquals(4, held.getCount());
    assertArena(tree);
  }

  @Test
  public void mutationsAndSharedRangesMatchFrozenOriginal() throws Exception {
    List<Comparator<Integer>> comparators =
        Arrays.asList(
            Comparator.naturalOrder(),
            Comparator.reverseOrder(),
            Comparator.nullsFirst(Comparator.naturalOrder()),
            (left, right) -> Integer.compare(Math.floorDiv(left, 3), Math.floorDiv(right, 3)));
    try (URLClassLoader original = originalLoader()) {
      for (int flavor = 0; flavor < comparators.size(); flavor++) {
        Object expected = create(original, comparators.get(flavor));
        Object actual = create(getClass().getClassLoader(), comparators.get(flavor));
        Object[] expectedViews = {
          expected,
          bound(expected, true, 0, true),
          bound(expected, false, -9, false),
          bound(expected, true, 24, false)
        };
        Object[] actualViews = {
          actual,
          bound(actual, true, 0, true),
          bound(actual, false, -9, false),
          bound(actual, true, 24, false)
        };
        Random random = new Random(0x4d33545245454dL + flavor);
        List<Object[]> held = new ArrayList<>();
        for (int step = 0; step < 2500; step++) {
          int selected = random.nextInt(expectedViews.length);
          Integer element = random.nextInt(21) == 0 ? null : random.nextInt(81) - 40;
          int amount = new int[] {-1, 0, 1, 3, 8, Integer.MAX_VALUE}[random.nextInt(6)];
          int oldCount = random.nextInt(5);
          int operation = random.nextInt(10);
          if (operation == 7 && step % 97 != 0) {
            operation = 4;
          }
          assertEquals(
              "operation " + flavor + ":" + step,
              mutate(expectedViews[selected], operation, element, amount, oldCount),
              mutate(actualViews[selected], operation, element, amount, oldCount));
          if (step % 8 == 0) {
            for (int i = 0; i < expectedViews.length; i++) {
              assertEquals(snapshot(expectedViews[i]), snapshot(actualViews[i]));
              assertEquals(
                  ((Collection<?>) expectedViews[i]).size(),
                  ((Collection<?>) actualViews[i]).size());
            }
          }
          if (step % 71 == 0) {
            Iterator<?> a = entries(expectedViews[selected]).iterator();
            Iterator<?> b = entries(actualViews[selected]).iterator();
            if (a.hasNext()) {
              held.add(new Object[] {a.next(), b.next()});
            }
          }
          if (step % 31 == 0) {
            assertArena((TreeMultiset<?>) actual);
            for (Object[] pair : held) {
              assertSame(
                  invoke(pair[0], "Multiset$Entry", "getElement"),
                  invoke(pair[1], "Multiset$Entry", "getElement"));
              assertEquals(
                  invoke(pair[0], "Multiset$Entry", "getCount"),
                  invoke(pair[1], "Multiset$Entry", "getCount"));
            }
          }
        }
        assertEquals(snapshot(expected), snapshot(actual));
        assertArena((TreeMultiset<?>) actual);
      }
    }
  }

  @Test
  public void comparatorFailureTraceAndReentryMatchOriginal() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (int operation = 0; operation < 4; operation++) {
        for (int failureAt = 1; failureAt <= 7; failureAt++) {
          TrackingComparator expectedComparator = new TrackingComparator();
          TrackingComparator actualComparator = new TrackingComparator();
          Object expected = populated(original, expectedComparator, 8, 4, 12, 2, 6, 10, 14);
          Object actual =
              populated(getClass().getClassLoader(), actualComparator, 8, 4, 12, 2, 6, 10, 14);
          expectedComparator.arm(failureAt);
          actualComparator.arm(failureAt);
          int key = new int[] {5, 10, 20, 6}[operation];
          assertEquals(
              mutate(expected, operation, key, 3, 1), mutate(actual, operation, key, 3, 1));
          assertEquals(expectedComparator.trace, actualComparator.trace);
          expectedComparator.arm(0);
          actualComparator.arm(0);
          assertEquals(snapshot(expected), snapshot(actual));
          assertEquals(((Collection<?>) expected).size(), ((Collection<?>) actual).size());
          assertArena((TreeMultiset<?>) actual);
        }
      }
      for (int mode = 0; mode < 5; mode++) {
        assertEquals(
            reentrantScenario(original, mode),
            reentrantScenario(getClass().getClassLoader(), mode));
      }
    }
  }

  @Test
  public void retainedDetachedCountSurvivesRejectedReentryAndSlotReuse() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      List<Object> expected = detachedCountScenario(original);
      assertEquals(
          Arrays.asList("java.util.ConcurrentModificationException", 0, 3, 7, 3), expected);
      assertEquals(expected, detachedCountScenario(getClass().getClassLoader()));
    }
  }

  @Test
  public void exportedCountObjectsAreSharedAndReleasedFromTheOwner() throws Exception {
    TreeMultiset<Integer> tree = TreeMultiset.create();
    Object arena = field(tree, "rootReference");
    for (int i = 0; i < 64; i++) {
      tree.add(i, 1);
    }
    assertEquals(0, nonNull((Object[]) field(arena, "exportedCounts")));
    Multiset.Entry<Integer> held = tree.firstEntry();
    Object firstState =
        Arrays.stream((Object[]) field(arena, "exportedCounts"))
            .filter(value -> value != null)
            .findFirst()
            .get();
    assertEquals(1, nonNull((Object[]) field(arena, "exportedCounts")));
    assertEquals(held, tree.firstEntry());
    assertSame(
        firstState,
        Arrays.stream((Object[]) field(arena, "exportedCounts"))
            .filter(value -> value != null)
            .findFirst()
            .get());
    for (Field member : firstState.getClass().getDeclaredFields()) {
      assertTrue(member.getType().isPrimitive());
    }
    List<Integer> visited = new ArrayList<>();
    tree.forEachEntry((element, count) -> visited.add(element));
    assertEquals(64, visited.size());
    assertEquals(1, nonNull((Object[]) field(arena, "exportedCounts")));
    List<Multiset.Entry<Integer>> exported = new ArrayList<>(tree.entrySet());
    assertEquals(64, nonNull((Object[]) field(arena, "exportedCounts")));
    tree.remove(0, Integer.MAX_VALUE);
    assertEquals(63, nonNull((Object[]) field(arena, "exportedCounts")));
    assertEquals(0, held.getCount());
    tree.clear();
    assertEquals(0, nonNull((Object[]) field(arena, "exportedCounts")));
    assertEquals(0, nonNull((Object[]) field(arena, "elements")));
    for (Multiset.Entry<Integer> entry : exported) {
      assertEquals(0, entry.getCount());
    }

    TrackingComparator comparator = new TrackingComparator();
    TreeMultiset<Integer> exceptional = TreeMultiset.create(comparator);
    Object exceptionalArena = field(exceptional, "rootReference");
    List<Multiset.Entry<Integer>> detached = new ArrayList<>();
    for (int cycle = 0; cycle < 128; cycle++) {
      exceptional.add(cycle, 1);
      Multiset.Entry<Integer> entry = exceptional.firstEntry();
      comparator.callback = exceptional::clear;
      int key = cycle;
      assertThrows(java.util.ConcurrentModificationException.class, () -> exceptional.add(key, 3));
      assertEquals(3, entry.getCount());
      assertEquals(0, exceptional.size());
      assertEquals(0, nonNull((Object[]) field(exceptionalArena, "exportedCounts")));
      assertEquals(0, nonNull((Object[]) field(exceptionalArena, "elements")));
      detached.add(entry);
    }
    for (Multiset.Entry<Integer> entry : detached) {
      assertEquals(3, entry.getCount());
    }
    assertEquals(2, field(exceptionalArena, "used"));
    assertEquals(8, ((Object[]) field(exceptionalArena, "elements")).length);
    for (Field member : exceptionalArena.getClass().getDeclaredFields()) {
      assertFalse(member.getName().startsWith("detached"));
    }
    assertArena(exceptional);

    // Discarded callback-created cursors are held only to the end of this one operation.
    TrackingComparator scopedComparator = new TrackingComparator();
    TreeMultiset<Integer> scoped = TreeMultiset.create(scopedComparator);
    scoped.add(2, 1);
    scoped.add(1, 1);
    scoped.add(3, 1);
    Object scopedArena = field(scoped, "rootReference");
    scopedComparator.callbackLeft = scopedComparator.callbackRight = 2;
    scopedComparator.callback =
        () -> {
          for (int cursor = 0; cursor < 32; cursor++) {
            assertNotNull(scoped.entrySet().iterator());
          }
          try {
            assertEquals(32, field(scopedArena, "activeCursorCount"));
            assertEquals(32, ((Object[]) field(scopedArena, "activeCursors")).length);
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
          scoped.clear();
        };
    assertEquals(0, scoped.count(2));
    assertEquals(null, field(scopedArena, "activeCursors"));
    assertEquals(0, field(scopedArena, "activeCursorCount"));
    assertEquals(0, nonNull((Object[]) field(scopedArena, "elements")));
    assertEquals(0, nonNull((Object[]) field(scopedArena, "exportedCounts")));
    assertArena(scoped);
  }

  @Test
  public void sameRootResurrectionAndRepeatedRetirementKeepOriginalPartialWrites()
      throws Exception {
    try (URLClassLoader original = originalLoader()) {
      List<Object> expected = reattachedScenario(original, false);
      assertEquals(Arrays.asList(0, 5, 3, 3, Arrays.asList("2=1", "3=1"), 6, 3, 3), expected);
      assertEquals(expected, reattachedScenario(getClass().getClassLoader(), false));
      assertEquals(
          reattachedScenario(original, true),
          reattachedScenario(getClass().getClassLoader(), true));
    }
  }

  @Test
  public void rangeEndpointCallbacksKeepTheirDetachedCursorThroughSlotReuse() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (boolean descending : new boolean[] {false, true}) {
        for (boolean reuse : new boolean[] {false, true}) {
          List<Object> expected = rangeCursorScenario(original, descending, reuse);
          assertEquals("java.lang.NullPointerException", expected.get(0));
          assertEquals(reuse ? 3 : 0, expected.get(1));
          assertEquals(
              expected, rangeCursorScenario(getClass().getClassLoader(), descending, reuse));
        }
        List<Object> orphaned = orphanedCursorScenario(original, descending);
        assertEquals(Arrays.asList(descending ? 3 : 1, 3), orphaned.get(0));
        assertEquals(0, orphaned.get(1));
        assertEquals(orphaned, orphanedCursorScenario(getClass().getClassLoader(), descending));
        for (boolean mixed : new boolean[] {false, true}) {
          List<Object> chain = detachedChainScenario(original, descending, mixed);
          assertEquals(mixed ? 10 : 0, chain.get(chain.size() - 1));
          assertEquals(
              chain, detachedChainScenario(getClass().getClassLoader(), descending, mixed));
        }
      }
      List<List<Object>> nestedExpected =
          Arrays.asList(
              Arrays.asList(0, "java.lang.NullPointerException", 0),
              Arrays.asList(1, "10=0", "15=0", "20=1", "30=1", "returned", 2),
              Arrays.asList(
                  "java.util.ConcurrentModificationException", "1=0", "2=3", "3=0", "returned", 0),
              Arrays.asList(ProbeFailure.class.getName(), "java.lang.NullPointerException", 0));
      for (int mode = 0; mode < nestedExpected.size(); mode++) {
        List<Object> expected = nestedCursorScenario(original, mode);
        assertEquals(nestedExpected.get(mode), expected);
        assertEquals(expected, nestedCursorScenario(getClass().getClassLoader(), mode));
      }
    }
  }

  private static int nonNull(Object[] values) {
    int count = 0;
    for (Object value : values) {
      if (value != null) {
        count++;
      }
    }
    return count;
  }

  private static List<Object> reattachedScenario(ClassLoader loader, boolean repeated)
      throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree = populated(loader, comparator, 2, 1, 3);
    Object held = invoke(tree, "SortedMultiset", "firstEntry");
    List<Object> result = new ArrayList<>();
    Runnable mutation =
        () -> {
          try {
            comparator.callbackLeft = comparator.callbackRight = 1;
            comparator.callback =
                () -> {
                  try {
                    assertEquals(1, mutate(tree, 1, 1, Integer.MAX_VALUE, 0));
                  } catch (Exception failure) {
                    throw new AssertionError(failure);
                  }
                };
            result.add(mutate(tree, 0, 1, 3, 0));
            result.add(((Collection<?>) tree).size());
            result.add(mutate(tree, 4, 1, 0, 0));
            result.add(invoke(held, "Multiset$Entry", "getCount"));
            if (repeated) {
              result.add(mutate(tree, 1, 1, Integer.MAX_VALUE, 0));
              result.add(invoke(held, "Multiset$Entry", "getCount"));
            }
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        };
    if (repeated) {
      ObjIntConsumer<Integer> action =
          (element, count) -> {
            if (element == 1) {
              mutation.run();
            }
          };
      assertEquals(
          null,
          invoke(tree, "Multiset", "forEachEntry", new Class<?>[] {ObjIntConsumer.class}, action));
    } else {
      mutation.run();
    }
    List<String> visited = new ArrayList<>();
    ObjIntConsumer<Integer> visitor = (element, count) -> visited.add(element + "=" + count);
    assertEquals(
        null,
        invoke(tree, "Multiset", "forEachEntry", new Class<?>[] {ObjIntConsumer.class}, visitor));
    result.add(visited);
    assertEquals(0, mutate(tree, 0, 4, 1, 0));
    result.add(((Collection<?>) tree).size());
    result.add(mutate(tree, 4, 1, 0, 0));
    result.add(invoke(held, "Multiset$Entry", "getCount"));
    if (!repeated) {
      // A later ordinary clear must release the root-only resurrected node too.
      ((Collection<?>) tree).clear();
      assertEquals(3, invoke(held, "Multiset$Entry", "getCount"));
      if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
        Object arena = field(tree, "rootReference");
        assertEquals(0, nonNull((Object[]) field(arena, "elements")));
        assertEquals(0, nonNull((Object[]) field(arena, "exportedCounts")));
      }
    }
    return result;
  }

  private static List<Object> rangeCursorScenario(
      ClassLoader loader, boolean descending, boolean reuse) throws Exception {
    Object[] owner = new Object[1];
    boolean[] armed = {false};
    boolean[] cleared = {false};
    int[] boundComparisons = {0};
    List<String> comparisons = new ArrayList<>();
    Comparator<Integer> comparator =
        (left, right) -> {
          if (armed[0]) {
            comparisons.add(left + ":" + right);
            if (!cleared[0]) {
              cleared[0] = true;
              ((Collection<?>) owner[0]).clear();
            }
            if (left == 2 && right == (descending ? 1 : 3)) {
              boundComparisons[0]++;
              if (reuse && boundComparisons[0] == 2) {
                try {
                  for (int key = 40; key <= 42; key++) {
                    assertEquals(0, mutate(owner[0], 0, key, 1, 0));
                  }
                } catch (Exception failure) {
                  throw new AssertionError(failure);
                }
              }
            }
          }
          return Integer.compare(left, right);
        };
    owner[0] = populated(loader, comparator, 2, 1, 3);
    Object view = bound(bound(owner[0], false, 1, true), true, 3, true);
    armed[0] = true;
    Object outcome;
    try {
      outcome =
          entrySnapshot(invoke(view, "SortedMultiset", descending ? "lastEntry" : "firstEntry"));
    } catch (RuntimeException failure) {
      outcome = failure.getClass().getName();
    }
    armed[0] = false;
    return Arrays.asList(
        outcome,
        ((Collection<?>) owner[0]).size(),
        snapshot(owner[0]),
        boundComparisons[0],
        comparisons);
  }

  private static List<Object> orphanedCursorScenario(ClassLoader loader, boolean descending)
      throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree = populated(loader, comparator, 2, 1, 3);
    int key = descending ? 3 : 1;
    comparator.callbackLeft = comparator.callbackRight = key;
    comparator.callback =
        () -> {
          try {
            assertEquals(1, mutate(tree, 1, key, Integer.MAX_VALUE, 0));
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        };
    assertEquals(0, mutate(tree, 0, key, 3, 0));
    Object view = bound(tree, descending, key, true);
    comparator.arm(0);
    comparator.callback = () -> ((Collection<?>) tree).clear();
    Object entry = invoke(view, "SortedMultiset", descending ? "lastEntry" : "firstEntry");
    Object snapshot = entrySnapshot(entry);
    if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      Object arena = field(tree, "rootReference");
      assertEquals(0, nonNull((Object[]) field(arena, "elements")));
      assertEquals(0, nonNull((Object[]) field(arena, "exportedCounts")));
    }
    return Arrays.asList(
        snapshot, ((Collection<?>) tree).size(), new ArrayList<>(comparator.trace));
  }

  private static List<Object> detachedChainScenario(
      ClassLoader loader, boolean descending, boolean mixed) throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree = populated(loader, comparator, 2, 1, 3);
    int first = descending ? 3 : 1;
    Object view = bound(tree, descending, first, true);
    comparator.callbackLeft = comparator.callbackRight = first;
    comparator.callback =
        () -> {
          try {
            Object removed = bound(tree, !descending, mixed ? 2 : descending ? 0 : 4, false);
            ((Collection<?>) removed).clear();
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        };
    List<Object> result = new ArrayList<>();
    entries(view)
        .forEach(
            entry -> {
              try {
                Object element = invoke(entry, "Multiset$Entry", "getElement");
                Object count = invoke(entry, "Multiset$Entry", "getCount");
                result.add(element + "=" + count);
                if (mixed && Integer.valueOf(first).equals(element)) {
                  assertEquals(1, mutate(tree, 2, 2, 8, 0));
                  assertEquals(0, mutate(tree, 0, descending ? 0 : 4, 1, 0));
                }
              } catch (Exception failure) {
                throw new AssertionError(failure);
              }
            });
    result.add("returned");
    result.add(((Collection<?>) tree).size());
    if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      assertArena((TreeMultiset<?>) tree);
    }
    return result;
  }

  private static List<Object> nestedCursorScenario(ClassLoader loader, int mode) throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree =
        mode == 1
            ? populated(loader, comparator, 20, 10, 30)
            : populated(loader, comparator, 2, 1, 3);
    List<Iterator<?>> captured = new ArrayList<>();
    Object observed = mode == 2 ? bound(tree, false, 1, true) : tree;
    Object removed = mode == 2 ? bound(tree, true, 4, false) : tree;
    comparator.callbackLeft = comparator.callbackRight = mode == 1 ? 20 : 2;
    comparator.callback =
        () -> {
          if (mode == 2) {
            comparator.callbackLeft = comparator.callbackRight = 1;
            comparator.callback = () -> ((Collection<?>) removed).clear();
          }
          try {
            captured.add(entries(observed).iterator());
            if (mode == 1) {
              assertEquals(0, mutate(tree, 0, 15, 1, 0));
              assertEquals(1, mutate(tree, 1, 10, Integer.MAX_VALUE, 0));
              assertEquals(1, mutate(tree, 1, 15, Integer.MAX_VALUE, 0));
            } else if (mode != 2) {
              ((Collection<?>) tree).clear();
            }
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
          if (mode == 3) {
            throw new ProbeFailure();
          }
        };
    List<Object> result = new ArrayList<>();
    try {
      result.add(
          invoke(
              tree,
              "Multiset",
              mode == 2 ? "add" : "count",
              mode == 2 ? new Class<?>[] {Object.class, int.class} : new Class<?>[] {Object.class},
              mode == 2 ? new Object[] {2, 3} : new Object[] {mode == 1 ? 20 : 2}));
    } catch (RuntimeException failure) {
      result.add(failure.getClass().getName());
    }
    if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      Object arena = field(tree, "rootReference");
      assertEquals(null, field(arena, "activeCursors"));
      assertEquals(0, field(arena, "activeCursorCount"));
    }
    try {
      captured
          .get(0)
          .forEachRemaining(
              entry -> {
                try {
                  result.add(
                      invoke(entry, "Multiset$Entry", "getElement")
                          + "="
                          + invoke(entry, "Multiset$Entry", "getCount"));
                } catch (Exception failure) {
                  throw new AssertionError(failure);
                }
              });
      result.add("returned");
    } catch (RuntimeException failure) {
      result.add(failure.getClass().getName());
    }
    result.add(((Collection<?>) tree).size());
    if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      assertArena((TreeMultiset<?>) tree);
    }
    return result;
  }

  private static List<Object> detachedCountScenario(ClassLoader loader) throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree = populated(loader, comparator, 8);
    Object held = invoke(tree, "SortedMultiset", "firstEntry");
    comparator.callback = () -> ((Collection<?>) tree).clear();
    String rejection = "no rejection";
    try {
      rejection =
          "returned "
              + invoke(tree, "Multiset", "add", new Class<?>[] {Object.class, int.class}, 8, 3);
    } catch (RuntimeException failure) {
      rejection = failure.getClass().getName();
    }
    int emptySize = ((Collection<?>) tree).size();
    Object detachedCount = invoke(held, "Multiset$Entry", "getCount");
    assertEquals(
        0, invoke(tree, "Multiset", "add", new Class<?>[] {Object.class, int.class}, 8, 7));
    Object liveCount = invoke(tree, "Multiset", "count", new Class<?>[] {Object.class}, 8);
    Object retainedCount = invoke(held, "Multiset$Entry", "getCount");
    return Arrays.asList(rejection, emptySize, detachedCount, liveCount, retainedCount);
  }

  @Test
  public void visitorMutationUsesDeferredLinksAndReleasesRetiredPayload() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (int mode = 0; mode < 4; mode++) {
        assertEquals(
            visitorScenario(original, mode), visitorScenario(getClass().getClassLoader(), mode));
      }
    }
  }

  @Test
  public void comparatorRangeSnapshotsAndEntrySerializationRemainCompatible() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (boolean oldWriter : new boolean[] {false, true}) {
        ClassLoader writer = oldWriter ? original : getClass().getClassLoader();
        ClassLoader reader = oldWriter ? getClass().getClassLoader() : original;
        Object tree = create(writer, Comparator.nullsFirst(Comparator.naturalOrder()));
        for (int i = 0; i < 40; i++) {
          assertEquals(0, mutate(tree, 0, i, i + 1, 0));
        }
        assertEquals(0, mutate(tree, 0, null, 3, 0));
        Object head = bound(tree, true, 20, false);
        Object[] copy = (Object[]) read(write(new Object[] {tree, head}), reader);
        assertEquals(snapshot(tree), snapshot(copy[0]));
        assertEquals(snapshot(head), snapshot(copy[1]));
        assertEquals(((Collection<?>) tree).size(), ((Collection<?>) copy[0]).size());
        // The original custom wire form deserializes each range as an independent snapshot.
        assertEquals(6, mutate(copy[0], 2, 5, 0, 0));
        assertEquals(6, invoke(copy[1], "Multiset", "count", new Class<?>[] {Object.class}, 5));
        if (reader == getClass().getClassLoader()) {
          assertArena((TreeMultiset<?>) copy[0]);
          assertArena((TreeMultiset<?>) copy[1]);
        }
        Object poll = invoke(tree, "SortedMultiset", "pollLastEntry");
        Object pollCopy = read(write(poll), reader);
        assertEquals(entrySnapshot(poll), entrySnapshot(pollCopy));
      }
      Object expected = populated(original, null, 1, 2, 3);
      Object actual = populated(getClass().getClassLoader(), null, 1, 2, 3);
      for (String method :
          new String[] {"firstEntry", "descendingMultiset", "elementSet", "entrySet"}) {
        Object expectedView = invoke(expected, "SortedMultiset", method);
        Object actualView = invoke(actual, "SortedMultiset", method);
        assertEquals(writeFailure(expectedView), writeFailure(actualView));
      }
    }
  }

  private static Object reentrantScenario(ClassLoader loader, int mode) throws Exception {
    TrackingComparator comparator = new TrackingComparator();
    Object tree = populated(loader, comparator, 8, 4, 12, 2, 6, 10, 14);
    comparator.arm(0);
    comparator.callback =
        () -> {
          try {
            if (mode == 0) {
              assertEquals(1, mutate(tree, 0, 2, 5, 0));
            } else if (mode == 1) {
              for (int key = 20; key < 100; key++) {
                assertEquals(0, mutate(tree, 0, key, 1, 0));
              }
            } else if (mode == 2) {
              assertEquals(1, invoke(tree, "Multiset", "count", new Class<?>[] {Object.class}, 6));
            } else {
              ((Collection<?>) tree).clear();
            }
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        };
    Object result = mutate(tree, 0, mode == 4 ? 17 : 8, 1, 0);
    if (mode >= 3 && loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      assertArena((TreeMultiset<?>) tree);
    }
    return Arrays.asList(
        result, new ArrayList<>(comparator.trace), snapshot(tree), ((Collection<?>) tree).size());
  }

  private static Object visitorScenario(ClassLoader loader, int mode) throws Exception {
    Object tree = populated(loader, null, 0, 1, 2, 3, 4, 5, 6);
    List<Object> visited = new ArrayList<>();
    ObjIntConsumer<Integer> visitor =
        (element, count) -> {
          visited.add(Arrays.asList(element, count));
          try {
            if (mode == 0) {
              assertEquals(1, mutate(tree, 2, element, 0, 0));
            } else if (mode == 1 && element == 0) {
              assertEquals(1, mutate(tree, 2, 0, 0, 0));
              assertEquals(1, mutate(tree, 2, 1, 0, 0));
              for (int key = 50; key < 150; key++) {
                assertEquals(0, mutate(tree, 0, key, 2, 0));
              }
            } else if (mode == 2 && element == 0) {
              ((Collection<?>) tree).clear();
            } else if (mode == 3 && element == 2) {
              assertEquals(1, mutate(tree, 2, element, 0, 0));
              throw new ProbeFailure();
            }
          } catch (RuntimeException failure) {
            throw failure;
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        };
    Object outcome;
    try {
      assertEquals(
          null,
          invoke(tree, "Multiset", "forEachEntry", new Class<?>[] {ObjIntConsumer.class}, visitor));
      outcome = "completed";
    } catch (RuntimeException failure) {
      outcome = failure.getClass().getName();
    }
    if (loader == M3TreeMultisetArrayContractTest.class.getClassLoader()) {
      assertArena((TreeMultiset<?>) tree);
    }
    return Arrays.asList(outcome, visited, snapshot(tree), ((Collection<?>) tree).size());
  }

  private static Object mutate(
      Object target, int operation, Object element, int amount, int expectedCount)
      throws Exception {
    try {
      switch (operation) {
        case 0:
          return invoke(
              target, "Multiset", "add", new Class<?>[] {Object.class, int.class}, element, amount);
        case 1:
          return invoke(
              target,
              "Multiset",
              "remove",
              new Class<?>[] {Object.class, int.class},
              element,
              amount);
        case 2:
          return invoke(
              target,
              "Multiset",
              "setCount",
              new Class<?>[] {Object.class, int.class},
              element,
              amount);
        case 3:
          return invoke(
              target,
              "Multiset",
              "setCount",
              new Class<?>[] {Object.class, int.class, int.class},
              element,
              expectedCount,
              amount);
        case 4:
          return invoke(target, "Multiset", "count", new Class<?>[] {Object.class}, element);
        case 5:
          return entrySnapshot(invoke(target, "SortedMultiset", "pollFirstEntry"));
        case 6:
          return entrySnapshot(invoke(target, "SortedMultiset", "pollLastEntry"));
        case 7:
          ((Collection<?>) target).clear();
          return null;
        case 8:
          return ((Set<?>) invoke(target, "Multiset", "elementSet")).remove(element);
        case 9:
          Iterator<?> iterator = entries(target).iterator();
          if (!iterator.hasNext()) {
            return null;
          }
          Object before = entrySnapshot(iterator.next());
          iterator.remove();
          return before;
        default:
          throw new AssertionError(operation);
      }
    } catch (RuntimeException failure) {
      String detail = failure.getMessage();
      // HotSpot may omit enhanced null-dereference text after compiling a hot throw site.
      // The same trace is also qualified with -XX:-OmitStackTraceInFastThrow; keep owner
      // validation messages exact while comparing the portable exception type and effects.
      if (failure instanceof NullPointerException
          && (detail == null || detail.startsWith("Cannot "))) {
        detail = "VM null dereference";
      }
      return Arrays.asList("exception", failure.getClass().getName(), detail);
    }
  }

  private static Object populated(ClassLoader loader, Comparator<?> comparator, Integer... values)
      throws Exception {
    Object tree = create(loader, comparator);
    for (Integer value : values) {
      assertEquals(
          0, invoke(tree, "Multiset", "add", new Class<?>[] {Object.class, int.class}, value, 1));
    }
    return tree;
  }

  private static Object create(ClassLoader loader, Comparator<?> comparator) throws Exception {
    return Class.forName(PACKAGE + "TreeMultiset", true, loader)
        .getMethod("create", Comparator.class)
        .invoke(null, comparator);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object bound(Object multiset, boolean head, Object endpoint, boolean closed)
      throws Exception {
    Class<?> boundType =
        Class.forName(PACKAGE + "BoundType", true, multiset.getClass().getClassLoader());
    Object type = Enum.valueOf((Class) boundType, closed ? "CLOSED" : "OPEN");
    return invoke(
        multiset,
        "SortedMultiset",
        head ? "headMultiset" : "tailMultiset",
        new Class<?>[] {Object.class, boundType},
        endpoint,
        type);
  }

  private static Set<?> entries(Object target) throws Exception {
    return (Set<?>) invoke(target, "Multiset", "entrySet");
  }

  private static List<Object> snapshot(Object target) throws Exception {
    List<Object> result = new ArrayList<>();
    Iterator<?> iterator = entries(target).iterator();
    while (iterator.hasNext()) {
      assertTrue("Traversal must terminate", result.size() < 10000);
      result.add(entrySnapshot(iterator.next()));
    }
    return result;
  }

  private static Object entrySnapshot(Object entry) throws Exception {
    return entry == null
        ? null
        : Arrays.asList(
            invoke(entry, "Multiset$Entry", "getElement"),
            invoke(entry, "Multiset$Entry", "getCount"));
  }

  private static void assertArena(TreeMultiset<?> tree) throws Exception {
    Object arena = field(tree, "rootReference");
    Object[] elements = (Object[]) field(arena, "elements");
    int[] counts = (int[]) field(arena, "elemCount");
    int[] distinct = (int[]) field(arena, "distinctElements");
    long[] totals = (long[]) field(arena, "totalCount");
    byte[] heights = (byte[]) field(arena, "height");
    int[] left = (int[]) field(arena, "left");
    int[] right = (int[]) field(arena, "right");
    int[] pred = (int[]) field(arena, "pred");
    int[] succ = (int[]) field(arena, "succ");
    int[] next = (int[]) field(arena, "freeNext");
    int used = (Integer) field(arena, "used");
    int root = (Integer) field(arena, "root");
    assertEquals(0, field(arena, "activeOperations"));
    assertEquals(null, field(arena, "activeCursors"));
    assertEquals(0, field(arena, "activeCursorCount"));
    assertEquals(-1, field(arena, "retired"));
    boolean[] live = new boolean[used];
    long[] aggregate = subtree(root, live, counts, distinct, totals, heights, left, right);
    assertEquals(tree.elementSet().size(), aggregate[0]);
    assertEquals(tree.size(), Math.min(Integer.MAX_VALUE, aggregate[1]));
    int previous = 0;
    int linked = 0;
    for (int slot = succ[0]; slot != 0; slot = succ[slot]) {
      assertTrue("Linked traversal terminates", ++linked < used);
      assertTrue(live[slot]);
      assertEquals(previous, pred[slot]);
      previous = slot;
    }
    assertEquals(previous, pred[0]);
    assertEquals(aggregate[0], linked);
    boolean[] free = new boolean[used];
    int freeCount = 0;
    for (int slot = (Integer) field(arena, "free"); slot != -1; slot = next[slot]) {
      assertTrue(slot > 0 && slot < used);
      assertFalse("Free-list cycle", free[slot]);
      assertFalse(live[slot]);
      free[slot] = true;
      freeCount++;
      assertEquals(0, counts[slot]);
      assertEquals(null, elements[slot]);
      assertEquals(-1, left[slot]);
      assertEquals(-1, right[slot]);
      assertEquals(-1, pred[slot]);
      assertEquals(-1, succ[slot]);
    }
    assertEquals(used - 1, linked + freeCount);
    List<Object> payload = new ArrayList<>();
    for (int slot = succ[0]; slot != 0; slot = succ[slot]) {
      payload.add(elements[slot]);
    }
    assertEquals(new ArrayList<>(tree.elementSet()), payload);
  }

  private static long[] subtree(
      int slot,
      boolean[] live,
      int[] counts,
      int[] distinct,
      long[] totals,
      byte[] heights,
      int[] left,
      int[] right) {
    if (slot == -1) {
      return new long[] {0, 0, 0};
    }
    assertTrue(slot > 0 && slot < live.length);
    assertFalse("Tree cycle or duplicate child", live[slot]);
    live[slot] = true;
    assertTrue(counts[slot] > 0);
    long[] a = subtree(left[slot], live, counts, distinct, totals, heights, left, right);
    long[] b = subtree(right[slot], live, counts, distinct, totals, heights, left, right);
    assertTrue(Math.abs(a[2] - b[2]) <= 1);
    assertEquals(1 + a[0] + b[0], distinct[slot]);
    assertEquals(counts[slot] + a[1] + b[1], totals[slot]);
    assertEquals(1 + Math.max(a[2], b[2]), heights[slot]);
    return new long[] {distinct[slot], totals[slot], heights[slot]};
  }

  private static Object invoke(Object target, String owner, String name) throws Exception {
    return invoke(target, owner, name, new Class<?>[0]);
  }

  private static Object invoke(
      Object target, String owner, String name, Class<?>[] types, Object... args) throws Exception {
    Method method =
        Class.forName(PACKAGE + owner, true, target.getClass().getClassLoader())
            .getMethod(name, types);
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw (Error) cause;
    }
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static URLClassLoader originalLoader() throws Exception {
    String directory = System.getProperty("m3.baseline.classes");
    assertNotNull("Independently compiled original classes are required", directory);
    assertTrue(new File(directory).isDirectory());
    List<URL> urls = new ArrayList<>();
    urls.add(new File(directory).toURI().toURL());
    for (String entry :
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
            .split(File.pathSeparator)) {
      if (entry.endsWith(".jar")) {
        urls.add(new File(entry).toURI().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private static byte[] write(Object object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(object);
    }
    return bytes.toByteArray();
  }

  private static String writeFailure(Object object) throws Exception {
    try {
      assertTrue(write(object).length > 0);
      return "serializable";
    } catch (java.io.NotSerializableException failure) {
      return failure.getClass().getName();
    }
  }

  private static Object read(byte[] bytes, ClassLoader loader) throws Exception {
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes)) {
          @Override
          protected Class<?> resolveClass(ObjectStreamClass descriptor)
              throws ClassNotFoundException {
            return Class.forName(descriptor.getName(), false, loader);
          }
        }) {
      return input.readObject();
    }
  }

  private static final class Token {
    final int rank;
    final String identity;

    Token(int rank, String identity) {
      this.rank = rank;
      this.identity = identity;
    }
  }

  private static final class ProbeFailure extends RuntimeException {
    ProbeFailure() {
      super("comparator or visitor stop");
    }
  }

  private static final class TrackingComparator implements Comparator<Integer> {
    final List<String> trace = new ArrayList<>();
    int failureAt;
    Runnable callback;
    Integer callbackLeft;
    Integer callbackRight;

    void arm(int failureAt) {
      trace.clear();
      this.failureAt = failureAt;
    }

    @Override
    public int compare(Integer left, Integer right) {
      trace.add(left + ":" + right);
      if (callback != null
          && (callbackLeft == null || (callbackLeft.equals(left) && callbackRight.equals(right)))) {
        Runnable action = callback;
        callback = null;
        action.run();
      }
      if (failureAt > 0 && trace.size() == failureAt) {
        throw new ProbeFailure();
      }
      return Integer.compare(left, right);
    }
  }
}

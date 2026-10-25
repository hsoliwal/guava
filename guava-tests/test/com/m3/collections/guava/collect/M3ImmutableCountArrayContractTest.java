/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** Retained-owner, transition, frozen-oracle and serialization discriminators. */
public final class M3ImmutableCountArrayContractTest {
  private static final String PACKAGE = "com.m3.collections.guava.collect.";

  @Test
  public void regularImmutableOwnerRetainsPrimitiveCountsWithoutEntryNodes() throws Exception {
    ImmutableMultiset.Builder<Integer> builder = ImmutableMultiset.builder();
    for (int i = 0; i < 400; i++) {
      builder.addCopies(i, i + 1);
    }
    ImmutableMultiset<Integer> result = builder.build();
    assertTrue(result instanceof RegularImmutableMultiset);
    ObjectCountHashMap<?> contents = (ObjectCountHashMap<?>) field(result, "contents");
    assertFalse(contents.usesHashFloodingResistance());
    assertTrue(field(contents, "keys") instanceof Object[]);
    assertTrue(field(contents, "values") instanceof int[]);
    assertTrue(field(contents, "entries") instanceof long[]);
    assertTrue(field(contents, "table") instanceof int[]);
    for (Field member : RegularImmutableMultiset.class.getDeclaredFields()) {
      assertFalse("No retained entry array", member.getType() == Multiset.Entry[].class);
      assertFalse("No object collision table", member.getName().equals("hashTable"));
    }
    for (Class<?> nested : RegularImmutableMultiset.class.getDeclaredClasses()) {
      assertFalse("No retained collision node", nested.getSimpleName().equals("NonTerminalEntry"));
    }
    Multiset.Entry<Integer> held = result.entrySet().asList().get(77);
    builder.setCount(77, 0).addCopies(400, 9);
    assertEquals(Integer.valueOf(77), held.getElement());
    assertEquals(78, held.getCount());
    assertEquals(400, result.elementSet().size());
    assertEquals(78, result.count(77));
    assertEquals(0, result.count(null));
    assertImmutableMutationRejected(result);
  }

  @Test
  public void floodingTransitionPreservesCountEntriesIteratorsAndDenseSlotRepair()
      throws Exception {
    LinkedHashMultiset<CollisionKey> multiset = LinkedHashMultiset.create();
    for (int i = 0; i < 9; i++) {
      multiset.add(new CollisionKey(i), i + 1);
    }
    ObjectCountHashMap<?> owner = (ObjectCountHashMap<?>) field(multiset, "backingMap");
    assertFalse(owner.usesHashFloodingResistance());
    Multiset.Entry<CollisionKey> held = multiset.entrySet().iterator().next();
    Iterator<CollisionKey> stale = multiset.iterator();
    multiset.add(new CollisionKey(9), 10);
    assertTrue(owner.usesHashFloodingResistance());
    assertThrows(ConcurrentModificationException.class, stale::next);
    multiset.setCount(new CollisionKey(0), 0);
    assertEquals(0, held.getCount());
    multiset.add(new CollisionKey(0), 23);
    assertEquals(23, held.getCount());
    assertEquals(new CollisionKey(0), new ArrayList<>(multiset.elementSet()).get(9));
    assertDenseSlots(owner);
    Iterator<Multiset.Entry<CollisionKey>> entries = multiset.entrySet().iterator();
    while (entries.hasNext()) {
      entries.next();
      entries.remove();
      assertDenseSlots(owner);
    }
    assertTrue(multiset.isEmpty());
    multiset.add(null, 2);
    assertEquals(2, multiset.count(null));
    multiset.clear();
    assertFalse(owner.usesHashFloodingResistance());
    multiset.add(new CollisionKey(1), 8);
    assertEquals(8, multiset.count(new CollisionKey(1)));
    assertDenseSlots(owner);
  }

  @Test
  public void linkedPrimitiveCountOwnerMatchesMapAcrossFloodedSlotMutations() {
    ObjectCountLinkedHashMap<CollisionKey> actual = ObjectCountLinkedHashMap.create();
    Map<CollisionKey, Integer> expected = new LinkedHashMap<>();
    for (int i = 0; i < 80; i++) {
      CollisionKey key = new CollisionKey(i);
      actual.put(key, i + 1);
      expected.put(key, i + 1);
    }
    assertTrue(actual.usesHashFloodingResistance());
    Random random = new Random(0x4D33494D4D434E54L);
    for (int step = 0; step < 5000; step++) {
      CollisionKey key = random.nextInt(12) == 0 ? null : new CollisionKey(random.nextInt(90));
      int count = random.nextInt(500) + 1;
      if (random.nextBoolean()) {
        Integer previous = expected.put(key, count);
        assertEquals(previous == null ? 0 : previous.intValue(), actual.put(key, count));
      } else {
        Integer previous = expected.remove(key);
        assertEquals(previous == null ? 0 : previous.intValue(), actual.remove(key));
      }
      actual.ensureCapacity(expected.size() + 7);
      assertDenseSlots(actual);
      List<CollisionKey> ordered = new ArrayList<>();
      for (int index = actual.firstIndex(); index != -1; index = actual.nextIndex(index)) {
        CollisionKey retained = actual.getKey(index);
        ordered.add(retained);
        assertEquals(expected.get(retained).intValue(), actual.getValue(index));
      }
      assertEquals(new ArrayList<>(expected.keySet()), ordered);
      assertEquals(expected.size(), actual.size());
    }
  }

  @Test
  public void immutableBuildersMatchOriginalAcrossCountsRemovalReuseAndOverflow() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      Object expected = builder(original);
      Object actual = builder(getClass().getClassLoader());
      Random random = new Random(0x4D33494D4D55544CL);
      List<Object> previous = new ArrayList<>();
      List<List<Object>> snapshots = new ArrayList<>();
      for (int step = 0; step < 3000; step++) {
        Object key = random.nextInt(27) == 0 ? null : new CollisionKey(random.nextInt(45));
        int amount = new int[] {0, 1, 3, 9, -1, Integer.MAX_VALUE}[random.nextInt(6)];
        String method = random.nextBoolean() ? "addCopies" : "setCount";
        assertEquals(
            "builder result at " + step,
            edit(expected, method, key, amount),
            edit(actual, method, key, amount));
        if (step % 19 == 0) {
          Object expectedResult = invoke(expected, "ImmutableMultiset$Builder", "build");
          Object actualResult = invoke(actual, "ImmutableMultiset$Builder", "build");
          assertEquals(snapshot(expectedResult), snapshot(actualResult));
          assertEquals(
              ((Collection<?>) expectedResult).size(), ((Collection<?>) actualResult).size());
          assertEquals(expectedResult.hashCode(), actualResult.hashCode());
          previous.add(actualResult);
          snapshots.add(snapshot(actualResult));
        }
      }
      for (int i = 0; i < previous.size(); i++) {
        assertEquals(snapshots.get(i), snapshot(previous.get(i)));
      }
    }
  }

  @Test
  public void immutableFloodingKeepsExistingJdkOwnerAndReadsSourceEntriesOnce() {
    List<Multiset.Entry<CollisionKey>> source = new ArrayList<>();
    int[] reads = new int[40];
    for (int i = 0; i < reads.length; i++) {
      final int id = i;
      source.add(
          new Multisets.AbstractEntry<CollisionKey>() {
            @Override
            public CollisionKey getElement() {
              return new CollisionKey(id);
            }

            @Override
            public int getCount() {
              reads[id]++;
              return id + 1;
            }
          });
    }
    ImmutableMultiset<CollisionKey> result = RegularImmutableMultiset.create(source);
    assertTrue(result instanceof JdkBackedImmutableMultiset);
    for (int i = 0; i < reads.length; i++) {
      assertEquals(1, reads[i]);
      assertEquals(i + 1, result.count(new CollisionKey(i)));
    }
    assertEquals(0, result.count(new CollisionKey(1000)));
    assertSame(result, ImmutableMultiset.copyOf(result));
  }

  @Test
  public void immutableViewsAndCountsSerializeAcrossOriginalAndCandidateBothWays()
      throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (boolean flooded : new boolean[] {false, true}) {
        for (boolean originalWriter : new boolean[] {false, true}) {
          ClassLoader writer = originalWriter ? original : getClass().getClassLoader();
          ClassLoader reader = originalWriter ? getClass().getClassLoader() : original;
          Object builder = builder(writer);
          for (int i = 0; i < 64; i++) {
            String key = flooded ? collisionString(i) : "key-" + i;
            assertSame(
                builder,
                invoke(
                    builder,
                    "ImmutableMultiset$Builder",
                    "addCopies",
                    new Class<?>[] {Object.class, int.class},
                    key,
                    i + 1));
          }
          Object multiset = invoke(builder, "ImmutableMultiset$Builder", "build");
          Object entries = invoke(multiset, "Multiset", "entrySet");
          Object elements = invoke(multiset, "Multiset", "elementSet");
          Object[] copy =
              (Object[]) read(write(new Object[] {multiset, entries, elements}), reader);
          assertEquals(snapshot(multiset), snapshot(copy[0]));
          assertEquals(new ArrayList<>((Set<?>) elements), new ArrayList<>((Set<?>) copy[2]));
          assertEquals(((Collection<?>) entries).size(), ((Collection<?>) copy[1]).size());
          assertEquals(multiset.hashCode(), copy[0].hashCode());
        }
      }
    }
  }

  private static void assertImmutableMutationRejected(Multiset<Integer> mutationView) {
    assertThrows(UnsupportedOperationException.class, () -> mutationView.add(7, 1));
  }

  private static void assertDenseSlots(ObjectCountHashMap<?> owner) {
    for (int i = 0; i < owner.size(); i++) {
      assertEquals(i, owner.indexOf(owner.getKey(i)));
      assertTrue(owner.getValue(i) > 0);
    }
  }

  private static Object edit(Object builder, String method, Object key, int count)
      throws Exception {
    try {
      assertSame(
          builder,
          invoke(
              builder,
              "ImmutableMultiset$Builder",
              method,
              new Class<?>[] {Object.class, int.class},
              key,
              count));
      return null;
    } catch (IllegalArgumentException | NullPointerException exception) {
      return Arrays.asList(exception.getClass().getName(), exception.getMessage());
    }
  }

  private static List<Object> snapshot(Object multiset) throws Exception {
    List<Object> snapshot = new ArrayList<>();
    for (Object entry : (Set<?>) invoke(multiset, "Multiset", "entrySet")) {
      snapshot.add(invoke(entry, "Multiset$Entry", "getElement"));
      snapshot.add(invoke(entry, "Multiset$Entry", "getCount"));
    }
    return snapshot;
  }

  private static Object builder(ClassLoader loader) throws Exception {
    return Class.forName(PACKAGE + "ImmutableMultiset", true, loader)
        .getMethod("builder")
        .invoke(null);
  }

  private static Object invoke(Object target, String owner, String method) throws Exception {
    return invoke(target, owner, method, new Class<?>[0]);
  }

  private static Object invoke(
      Object target, String owner, String method, Class<?>[] types, Object... args)
      throws Exception {
    Method selected =
        Class.forName(PACKAGE + owner, true, target.getClass().getClassLoader())
            .getMethod(method, types);
    try {
      return selected.invoke(target, args);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw (Error) cause;
    }
  }

  private static Object field(Object target, String name) throws Exception {
    for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
      try {
        Field member = owner.getDeclaredField(name);
        member.setAccessible(true);
        return member.get(target);
      } catch (NoSuchFieldException ignored) {
        // Continue to the shared package-private owner.
      }
    }
    throw new NoSuchFieldException(name);
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

  private static String collisionString(int bits) {
    StringBuilder value = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      value.append(((bits >>> i) & 1) == 0 ? "Aa" : "BB");
    }
    return value.toString();
  }

  private static final class CollisionKey {
    private final int id;

    CollisionKey(int id) {
      this.id = id;
    }

    @Override
    public int hashCode() {
      return 7;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof CollisionKey && id == ((CollisionKey) other).id;
    }

    @Override
    public String toString() {
      return "collision-" + id;
    }
  }
}

/*
 * Copyright (C) 2026 Hitesh Soliwal and contributors
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import junit.framework.TestCase;

/** Contract and parent-owned storage proof for both Guava flavors. */
public final class LinkedListMultimapArrayLinksTest extends TestCase {

  private static Object[][] entryBoundaries(LinkedListMultimap<?, ?> map) throws Exception {
    Field field = LinkedListMultimap.class.getDeclaredField("entryBoundaries");
    field.setAccessible(true);
    return (Object[][]) field.get(map);
  }

  private static void assertNoEntryHandles(LinkedListMultimap<?, ?> map) throws Exception {
    Object[][] boundaries = entryBoundaries(map);
    if (boundaries != null) {
      for (Object[] page : boundaries) {
        if (page != null) {
          for (Object boundary : page) {
            assertNull("read-only value iteration must not materialize Node entries", boundary);
          }
        }
      }
    }
  }

  public void testNormalValueUsageAllocatesNoPerMappingNodes() throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(128);
    for (int index = 0; index < 12000; index++) {
      map.put(index % 83, index);
    }
    assertEquals(12000, map.size());
    assertEquals(83, map.keySet().size());
    assertNull("entry boundary array must remain unallocated on normal paths",
        entryBoundaries(map));
    assertNoEntryHandles(map);
    int sum = 0;
    for (Integer value : map.values()) {
      sum += value;
    }
    assertEquals(71994000, sum);
    assertEquals(145, map.get(0).size());
    assertNoEntryHandles(map);
    assertNull("value iteration must not allocate the entry-boundary array",
        entryBoundaries(map));

    Field linkedNext = LinkedListMultimap.class.getDeclaredField("nextAll");
    Field linkedSibling = LinkedListMultimap.class.getDeclaredField("nextForKey");
    linkedNext.setAccessible(true);
    linkedSibling.setAccessible(true);
    assertTrue(linkedNext.get(map) instanceof int[]);
    assertTrue(linkedSibling.get(map) instanceof int[]);
    map.clear();
    assertEquals(0, map.size());
    assertNoEntryHandles(map);
  }

  public void testEscapedEntryCacheUsesOnlyTouchedPages() throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create();
    for (int index = 0; index < 12000; index++) {
      map.put(index % 31, index);
    }
    assertNull("a value-only workload must not allocate entry pages", entryBoundaries(map));

    Map.Entry<Integer, Integer> first = map.entries().get(0);
    Object[][] pages = entryBoundaries(map);
    assertNotNull(pages);
    assertTrue("the top-level page index must be smaller than the value storage",
        pages.length < map.size());
    int materializedPages = 0;
    for (Object[] page : pages) {
      if (page != null) {
        materializedPages++;
        assertEquals(64, page.length);
      }
    }
    assertEquals("only the touched page may be allocated", 1, materializedPages);
    assertSame(first, map.entries().get(0));

    for (int index = 12000; index < 24000; index++) {
      map.put(index % 31, index);
    }
    assertSame("entry identity survives backing-array growth", first, map.entries().get(0));
    Map.Entry<Integer, Integer> last = map.entries().get(map.size() - 1);
    assertEquals(Integer.valueOf(23999), last.getValue());
    int allocatedPages = 0;
    for (Object[] page : entryBoundaries(map)) {
      if (page != null) {
        allocatedPages++;
      }
    }
    assertEquals("only the two accessed pages should be retained", 2, allocatedPages);

    map.get(0).remove(0);
    assertEquals(Integer.valueOf(0), first.getValue());
    first.setValue(-1);
    assertEquals(Integer.valueOf(-1), first.getValue());
    map.clear();
    assertNull(entryBoundaries(map));
    assertEquals(Integer.valueOf(-1), first.getValue());
  }

  public void testContainsValueScansPrimitiveSlotsForBuiltinValues() throws Exception {
    LinkedListMultimap<Integer, Object> map = LinkedListMultimap.create();
    map.put(1, 10);
    map.put(2, null);
    map.put(1, "value");
    map.put(3, Boolean.TRUE);
    assertNull(entryBoundaries(map));
    assertTrue(map.containsValue(Integer.valueOf(10)));
    assertTrue(map.containsValue(null));
    assertTrue(map.containsValue("value"));
    assertTrue(map.containsValue(Boolean.TRUE));
    assertFalse(map.containsValue(Integer.valueOf(44)));
    assertFalse(map.containsValue("missing"));
    assertNull("direct containsValue must not allocate entry boundaries",
        entryBoundaries(map));
  }

  public void testContainsValueOpaqueEqualityRetainsCallbackOrder() throws Exception {
    LinkedListMultimap<String, Object> map = LinkedListMultimap.create();
    Object first = new Object();
    Object target = new Object();
    map.put("left", first);
    map.put("right", target);
    map.put("left", new Object());

    List<Object> arguments = new ArrayList<>();
    Object query = new Object() {
      @Override
      public boolean equals(Object candidate) {
        arguments.add(candidate);
        return candidate == target;
      }
      @Override
      public int hashCode() {
        return 17;
      }
    };
    assertTrue(map.containsValue(query));
    assertEquals(Arrays.asList(first, target), arguments);
    assertNull("opaque equality cannot materialize entry boundaries",
        entryBoundaries(map));
  }

  public void testSerializationWritesSlotValuesWithoutMaterializingEntryBoundaries()
      throws Exception {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    Integer shared = Integer.valueOf(1000);
    map.put("a", shared);
    map.put(null, null);
    map.put("b", shared);
    map.put("a", 25);
    assertNull("no entry projection allocated before serialization", entryBoundaries(map));
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
      stream.writeObject(map);
      stream.flush();
      serialized = bytes.toByteArray();
    }
    assertNull("serialization must not create entry boundary array", entryBoundaries(map));
    LinkedListMultimap<?, ?> restored;
    try (ObjectInputStream stream = new ObjectInputStream(
        new ByteArrayInputStream(serialized))) {
      restored = (LinkedListMultimap<?, ?>) stream.readObject();
    }
    assertNull("deserialization must not project public entries", entryBoundaries(restored));
    assertEquals(Arrays.asList(shared, null, shared, 25), restored.values());
    assertSame("shared references must remain shared after serialization",
        restored.values().get(0), restored.values().get(2));
    assertEquals(Arrays.asList("a", null, "b", "a"),
        new ArrayList<>(restored.keys()));
  }

  public void testEscapedEntriesTrackUpdatesThenDetachOnSlotRecycling() throws Exception {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("a", 1);
    map.put("b", 2);
    map.put("a", 3);
    Map.Entry<String, Integer> escaped = map.entries().get(0);
    assertNotNull(entryBoundaries(map));
    assertEquals(Integer.valueOf(1), escaped.setValue(11));
    assertEquals(Arrays.asList(11, 3), map.get("a"));
    assertEquals(Integer.valueOf(11), map.get("a").set(0, 12));
    assertEquals(Integer.valueOf(12), escaped.getValue());
    assertEquals(Integer.valueOf(12), map.get("a").remove(0));
    assertEquals(Integer.valueOf(12), escaped.getValue());
    assertEquals(Integer.valueOf(12), escaped.setValue(99));
    map.put("a", 77);  // Reuses the removed physical slot.
    assertEquals(Integer.valueOf(99), escaped.getValue());
    assertEquals(Arrays.asList(3, 77), map.get("a"));
    assertEquals("a", escaped.getKey());
    map.clear();
    assertEquals(Integer.valueOf(99), escaped.getValue());
    escaped.setValue(-8);
    assertEquals(Integer.valueOf(-8), escaped.getValue());
  }

  public void testGlobalSiblingAndReverseIterationOrder() {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("a", 1);
    map.put("b", 2);
    map.put("a", 3);
    map.put("c", 5);
    map.get("a").add(1, 4);
    assertEquals(Arrays.asList(1, 2, 4, 3, 5), map.values());
    assertEquals(Arrays.asList("a", "b", "a", "a", "c"),
        new ArrayList<>(map.keys()));
    assertEquals(Arrays.asList(1, 4, 3), map.get("a"));

    ListIterator<Map.Entry<String, Integer>> iterator = map.entries().listIterator(4);
    assertEquals(Integer.valueOf(5), iterator.next().getValue());
    assertTrue(iterator.hasPrevious());
    assertEquals(Integer.valueOf(5), iterator.previous().getValue());
    assertEquals(Integer.valueOf(3), iterator.previous().getValue());
    iterator.remove();
    assertEquals(Arrays.asList(1, 2, 4, 5), map.values());
    assertEquals(Arrays.asList("a", "b", "c"), new ArrayList<>(map.keySet()));
    assertEquals(Arrays.asList(1, 4), map.get("a"));
  }

  public void testValuesIteratorDoesNotNeedEntryProjections() throws Exception {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("a", 1);
    map.put("b", 2);
    map.put("a", 3);
    ListIterator<Integer> iterator = map.values().listIterator(1);
    assertEquals(Integer.valueOf(2), iterator.next());
    iterator.set(20);
    assertEquals(Integer.valueOf(20), iterator.previous());
    iterator.remove();
    assertEquals(Arrays.asList(1, 3), map.values());
    assertFalse(map.containsKey("b"));
    assertNoEntryHandles(map);
    try {
      iterator.add(9);
      fail("values iterator add must be unsupported");
    } catch (UnsupportedOperationException expected) {
      // Preserve the Guava TransformedListIterator contract.
    }
  }

  public void testClearDetachesEntriesAndSerializationRestoresPrimitiveOwner()
      throws Exception {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put(null, 1);
    map.put("x", null);
    map.put(null, 3);
    Map.Entry<String, Integer> escaped = map.entries().get(1);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(map);
    }
    LinkedListMultimap<?, ?> copy;
    try (ObjectInputStream in = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (LinkedListMultimap<?, ?>) in.readObject();
    }
    assertEquals(map.entries(), copy.entries());
    assertEquals(map.values(), copy.values());
    assertEquals(map.keySet(), copy.keySet());
    map.clear();
    assertEquals(null, escaped.getValue());
    escaped.setValue(55);
    assertEquals(Integer.valueOf(55), escaped.getValue());
    map.put("new", 66);
    assertEquals(Arrays.asList(66), map.values());
    assertNoEntryHandles(map);
  }

  private static int nthSlot(List<Map.Entry<Integer, Integer>> entries,
      Integer key, int index) {
    int seen = 0;
    for (int position = 0; position < entries.size(); position++) {
      if (java.util.Objects.equals(entries.get(position).getKey(), key)
          && seen++ == index) {
        return position;
      }
    }
    return entries.size();
  }

  private static int countFor(List<Map.Entry<Integer, Integer>> entries, Integer key) {
    int count = 0;
    for (Map.Entry<Integer, Integer> entry : entries) {
      if (java.util.Objects.equals(entry.getKey(), key)) {
        count++;
      }
    }
    return count;
  }

  private static void verify(LinkedListMultimap<Integer, Integer> map,
      List<Map.Entry<Integer, Integer>> oracle) {
    assertEquals(oracle.size(), map.size());
    List<Integer> values = new ArrayList<>();
    LinkedHashSet<Integer> keys = new LinkedHashSet<>();
    for (Map.Entry<Integer, Integer> entry : oracle) {
      values.add(entry.getValue());
      keys.add(entry.getKey());
    }
    assertEquals(values, map.values());
    assertEquals(new ArrayList<>(keys), new ArrayList<>(map.keySet()));
    for (Integer key : keys) {
      List<Integer> byKey = new ArrayList<>();
      for (Map.Entry<Integer, Integer> entry : oracle) {
        if (java.util.Objects.equals(key, entry.getKey())) {
          byKey.add(entry.getValue());
        }
      }
      assertEquals(byKey, map.get(key));
    }
  }

  public void testEntryIdentityAndValueUpdatesAcrossForwardAndReverseIteration() {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("first", 10);
    map.put("second", 20);
    map.put("first", 30);
    ListIterator<Map.Entry<String, Integer>> entries = map.entries().listIterator();
    Map.Entry<String, Integer> first = entries.next();
    assertSame(first, entries.previous());
    assertSame(first, entries.next());
    assertEquals(Integer.valueOf(10), first.getValue());

    map.values().set(0, 11);
    assertEquals(Integer.valueOf(11), first.getValue());
    map.get("first").set(0, 12);
    assertEquals(Integer.valueOf(12), first.getValue());
    first.setValue(13);
    assertEquals(Integer.valueOf(13), map.values().get(0));

    entries.remove();
    assertEquals(Arrays.asList(20, 30), map.values());
    assertEquals(Integer.valueOf(13), first.getValue());
    first.setValue(99);
    map.put("third", 40);
    assertEquals(Integer.valueOf(99), first.getValue());
    assertEquals(Arrays.asList(20, 30, 40), map.values());
  }

  public void testEscapedEntryPreservesHistoricalStreamDescriptor()
      throws Exception {
    assertEquals(-2359543990797351600L,
        java.io.ObjectStreamClass.lookup(LinkedListMultimap.Node.class).getSerialVersionUID());
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("key", 18);
    Map.Entry<String, Integer> escaped = map.entries().get(0);
    map.get("key").set(0, 19);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(escaped);
    }
    Object restored;
    try (ObjectInputStream in = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = in.readObject();
    }
    assertEquals(escaped, restored);
    assertEquals(Integer.valueOf(19), ((Map.Entry<?, ?>) restored).getValue());
    map.removeAll("key");
    escaped.setValue(21);
    assertEquals(Integer.valueOf(21), escaped.getValue());
  }

  public void testRandomizedArraySlotLinksMatchGlobalAndPerKeyReference() {
    Random random = new Random(0x4d334e4f444553L);
    for (int seed = 0; seed < 16; seed++) {
      LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create();
      List<Map.Entry<Integer, Integer>> oracle = new ArrayList<>();
      for (int step = 0; step < 3500; step++) {
        Integer key = random.nextInt(18) == 0 ? null : random.nextInt(23);
        Integer value = random.nextInt(13) == 0 ? null : random.nextInt(91);
        int operation = random.nextInt(8);
        int count = countFor(oracle, key);
        if ((operation <= 2 || oracle.isEmpty()) && oracle.size() < 128) {
          map.put(key, value);
          oracle.add(new AbstractMap.SimpleEntry<>(key, value));
        } else if (operation == 3 && oracle.size() < 128) {
          int index = random.nextInt(count + 1);
          map.get(key).add(index, value);
          oracle.add(index == count ? oracle.size() : nthSlot(oracle, key, index),
              new AbstractMap.SimpleEntry<>(key, value));
        } else if (operation == 4 && count > 0) {
          int index = random.nextInt(count);
          int position = nthSlot(oracle, key, index);
          assertEquals(oracle.remove(position).getValue(), map.get(key).remove(index));
        } else if (operation == 5 && count > 0) {
          int index = random.nextInt(count);
          int position = nthSlot(oracle, key, index);
          Integer previous = oracle.get(position).setValue(value);
          assertEquals(previous, map.get(key).set(index, value));
        } else if (operation == 6 && !oracle.isEmpty()) {
          int position = random.nextInt(oracle.size());
          ListIterator<Map.Entry<Integer, Integer>> iterator =
              map.entries().listIterator(position);
          assertEquals(oracle.get(position), iterator.next());
          iterator.remove();
          oracle.remove(position);
        } else if (operation == 7 && (step & 63) == 0) {
          map.clear();
          oracle.clear();
        }
        if ((step & 15) == 0) {
          verify(map, oracle);
        }
      }
      verify(map, oracle);
    }
  }
}

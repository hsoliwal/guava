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
package com.google.common.collect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import junit.framework.TestCase;

/** Distinct-key storage and live-view contracts, shared by both Guava flavors. */
public final class LinkedListMultimapKeyLanesTest extends TestCase {

  private static Object field(Object owner, String name) throws Exception {
    Field field = LinkedListMultimap.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  public void testEmptyDirectoryAndKeyLanesAreDeferred() throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(Integer.MAX_VALUE);
    assertNull(field(map, "keyToKeyList"));
    assertEquals(0, ((int[]) field(map, "groupHeads")).length);
    assertFalse(map.containsKey(1));
    assertEquals(Collections.emptyList(), map.get(1));
    assertEquals(Collections.emptyList(), map.removeAll(1));
    assertEquals(0, map.keySet().size());
    map.clear();
    assertNull(field(map, "keyToKeyList"));
    try {
      LinkedListMultimap.create(-1);
      fail();
    } catch (IllegalArgumentException expected) {
      assertEquals("Expected size must be >= 0", expected.getMessage());
    }
  }

  public void testDistinctKeysUsePrimitiveMetadataWithoutEntryProjections() throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create();
    for (int index = 0; index < 10000; index++) {
      map.put(index % 997, index);
    }
    assertTrue(field(map, "keyToKeyList") instanceof ObjectCountHashMap);
    assertTrue(field(map, "groupHeads") instanceof int[]);
    assertTrue(field(map, "groupTails") instanceof int[]);
    assertTrue(field(map, "groupCounts") instanceof int[]);
    assertNull(field(map, "entryBoundaries"));
    for (Class<?> type : LinkedListMultimap.class.getDeclaredClasses()) {
      assertFalse("no object per distinct key", type.getSimpleName().equals("KeyList"));
    }
    for (int key = 0; key < 997; key++) {
      assertEquals((9999 - key) / 997 + 1, map.get(key).size());
    }
    assertNull(field(map, "entryBoundaries"));
  }

  public void testRemovedGroupsAreReusedAndViewsStayLive() throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(0);
    List<Integer> live = map.get(7);
    int capacity = 0;
    for (int cycle = 0; cycle < 3000; cycle++) {
      map.put(7, cycle);
      map.put(11, -cycle);
      map.put(7, cycle + 1);
      assertEquals(Arrays.asList(cycle, cycle + 1), live);
      assertEquals(Arrays.asList(cycle, cycle + 1), map.removeAll(7));
      assertTrue(live.isEmpty());
      map.removeAll(11);
      if (cycle == 0) {
        capacity = ((int[]) field(map, "groupHeads")).length;
      }
      assertEquals(capacity, ((int[]) field(map, "groupHeads")).length);
      assertTrue(map.isEmpty());
    }
    live.add(42);
    assertEquals(Arrays.asList(42), map.get(7));
    assertEquals(Arrays.asList(7), new ArrayList<>(map.keySet()));
  }

  public void testDirectoryCompactionCannotMoveGroupOwnership() {
    LinkedListMultimap<Integer, String> map = LinkedListMultimap.create(1);
    for (int key = 0; key < 700; key++) {
      map.put(key, "a" + key);
      map.put(key, "b" + key);
    }
    for (int key = 0; key < 700; key += 2) {
      assertEquals(Arrays.asList("a" + key, "b" + key), map.removeAll(key));
    }
    for (int key = 1; key < 700; key += 2) {
      map.get(key).add(1, "middle");
      assertEquals(Arrays.asList("a" + key, "middle", "b" + key), map.get(key));
    }
    for (int key = 0; key < 700; key += 2) {
      map.put(key, "new" + key);
    }
    for (int key = 0; key < 700; key += 2) {
      assertEquals(Arrays.asList("new" + key), map.get(key));
    }
  }

  public void testNullKeysAndEscapedEntriesSurviveGroupRecycling() {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put(null, null);
    map.put("b", 2);
    map.put(null, 3);
    Map.Entry<String, Integer> entry = map.entries().get(0);
    map.get(null).set(0, 7);
    assertEquals(Integer.valueOf(7), entry.getValue());
    assertEquals(Arrays.asList(7, 3), map.removeAll(null));
    map.put("c", 99);
    map.put(null, 4);
    assertEquals(Integer.valueOf(7), entry.setValue(8));
    assertEquals(Integer.valueOf(8), entry.getValue());
    assertEquals(Arrays.asList(4), map.get(null));
    map.clear();
    assertEquals(Integer.valueOf(8), entry.getValue());
    map.put(null, null);
    assertTrue(map.containsEntry(null, null));
  }

  public void testCollisionFallbackPreservesGroupsAndEqualKeyLookup() {
    LinkedListMultimap<CollisionKey, Integer> map = LinkedListMultimap.create();
    for (int key = 0; key < 128; key++) {
      map.put(new CollisionKey(key), key);
      map.put(new CollisionKey(key), -key);
    }
    for (int key = 0; key < 128; key += 3) {
      assertEquals(Arrays.asList(key, -key), map.removeAll(new CollisionKey(key)));
    }
    for (int key = 0; key < 128; key++) {
      List<Integer> expected = key % 3 == 0 ? Collections.emptyList() : Arrays.asList(key, -key);
      assertEquals(expected, map.get(new CollisionKey(key)));
    }
  }

  public void testKeyIteratorRemoveAndDistinctKeyInvalidation() {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("a", 1);
    map.put("b", 2);
    map.put("a", 3);
    Iterator<String> iterator = map.keySet().iterator();
    assertEquals("a", iterator.next());
    iterator.remove();
    assertEquals("b", iterator.next());
    iterator.remove();
    assertFalse(iterator.hasNext());
    map.put("x", 1);
    Iterator<String> invalidated = map.keySet().iterator();
    map.put("new", 2);
    try {
      invalidated.hasNext();
      fail();
    } catch (ConcurrentModificationException expected) {
      // Adding a distinct key still invalidates the old iterator.
    }
  }

  public void testSerializationReconstructsPrimitiveGroups() throws Exception {
    LinkedListMultimap<CollisionKey, Integer> map = LinkedListMultimap.create();
    for (int key = 0; key < 40; key++) {
      map.put(new CollisionKey(key), key);
      map.put(new CollisionKey(key), -key);
    }
    map.put(null, null);
    byte[] bytes;
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
      stream.writeObject(map);
      stream.flush();
      bytes = buffer.toByteArray();
    }
    LinkedListMultimap<?, ?> restored;
    try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      restored = (LinkedListMultimap<?, ?>) stream.readObject();
    }
    // Inspect deferred state before keys() traversal requests entry projections.
    assertTrue(field(restored, "keyToKeyList") instanceof ObjectCountHashMap);
    assertNull(field(restored, "entryBoundaries"));
    assertEquals(map, restored);
    assertEquals(map.values(), restored.values());
    assertEquals(new ArrayList<>(map.keys()), new ArrayList<>(restored.keys()));
  }

  public void testRandomOperationsAgainstIndependentOrderedPairList() {
    for (int seed = 0; seed < 8; seed++) {
      Random random = new Random(0x51A7L + seed);
      LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(0);
      List<Map.Entry<Integer, Integer>> oracle = new ArrayList<>();
      for (int step = 0; step < 2000; step++) {
        Integer key = random.nextInt(15) == 0 ? null : random.nextInt(31);
        Integer value = random.nextInt(15) == 0 ? null : random.nextInt(99);
        List<Integer> before = valuesFor(oracle, key);
        switch (random.nextInt(7)) {
          case 0:
            map.put(key, value);
            oracle.add(new SimpleEntry<>(key, value));
            break;
          case 1:
            assertEquals(before, map.removeAll(key));
            oracle.removeIf(entry -> Objects.equals(entry.getKey(), key));
            break;
          case 2:
            int position = random.nextInt(before.size() + 1);
            map.get(key).add(position, value);
            oracle.add(position == before.size() ? oracle.size() :
                entryIndex(oracle, key, position), new SimpleEntry<>(key, value));
            break;
          case 3:
            if (!before.isEmpty()) {
              int index = random.nextInt(before.size());
              assertEquals(before.get(index), map.get(key).remove(index));
              oracle.remove(entryIndex(oracle, key, index));
            }
            break;
          case 4:
            if (!before.isEmpty()) {
              int index = random.nextInt(before.size());
              assertEquals(before.get(index), map.get(key).set(index, value));
              oracle.get(entryIndex(oracle, key, index)).setValue(value);
            }
            break;
          case 5:
            List<Integer> replacement = Arrays.asList(value, null);
            assertEquals(before, map.replaceValues(key, replacement));
            int existing = before.size();
            int shared = Math.min(existing, replacement.size());
            for (int index = 0; index < shared; index++) {
              oracle.get(entryIndex(oracle, key, index)).setValue(replacement.get(index));
            }
            for (int index = existing - 1; index >= shared; index--) {
              oracle.remove(entryIndex(oracle, key, index));
            }
            for (int index = shared; index < replacement.size(); index++) {
              oracle.add(new SimpleEntry<>(key, replacement.get(index)));
            }
            break;
          default:
            if (random.nextInt(32) == 0) {
              map.clear();
              oracle.clear();
            }
        }
        assertEquals(oracle.size(), map.size());
        assertEquals(oracle, map.entries());
        LinkedHashSet<Integer> keys = new LinkedHashSet<>();
        for (Map.Entry<Integer, Integer> entry : oracle) {
          keys.add(entry.getKey());
        }
        assertEquals(new ArrayList<>(keys), new ArrayList<>(map.keySet()));
        assertEquals(valuesFor(oracle, key), map.get(key));
        assertEquals(!valuesFor(oracle, key).isEmpty(), map.containsKey(key));
      }
    }
  }

  private static List<Integer> valuesFor(
      List<Map.Entry<Integer, Integer>> entries, Integer key) {
    List<Integer> values = new ArrayList<>();
    for (Map.Entry<Integer, Integer> entry : entries) {
      if (Objects.equals(entry.getKey(), key)) {
        values.add(entry.getValue());
      }
    }
    return values;
  }

  private static int entryIndex(
      List<Map.Entry<Integer, Integer>> entries, Integer key, int ordinal) {
    for (int index = 0; index < entries.size(); index++) {
      if (Objects.equals(entries.get(index).getKey(), key) && ordinal-- == 0) {
        return index;
      }
    }
    throw new AssertionError("missing oracle entry");
  }

  private static final class CollisionKey implements Serializable {
    private static final long serialVersionUID = 1;
    private final int value;

    CollisionKey(int value) {
      this.value = value;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof CollisionKey && ((CollisionKey) other).value == value;
    }
  }
}

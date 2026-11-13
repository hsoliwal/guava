/*
 * Copyright (C) 2026 Hitesh Soliwal and contributors to the Synexia Project
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import junit.framework.TestCase;

/** Contract and retained-storage tests for the array-owned distinct-key directory. */
public final class LinkedListMultimapArrayDirectoryTest extends TestCase {

  private static final class CollisionKey {
    private final int id;

    CollisionKey(int id) {
      this.id = id;
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof CollisionKey && id == ((CollisionKey) other).id;
    }
  }

  private static Object directory(LinkedListMultimap<?, ?> map) throws Exception {
    Field field = LinkedListMultimap.class.getDeclaredField("keyToKeyList");
    field.setAccessible(true);
    return field.get(map);
  }

  private static Object privateField(Object owner, String fieldName) throws Exception {
    Field field = owner.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(owner);
  }

  public void testDistinctKeysUsePrimitiveIndexedArraysWithoutRetainedHashMapEntries()
      throws Exception {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(1024);
    for (int i = 0; i < 1024; i++) {
      map.put(i, i);
      map.put(i, i + 1024);
    }
    Object owner = directory(map);
    assertTrue(owner instanceof ArrayKeyDirectory);
    assertNull(privateField(owner, "fallback"));
    assertTrue(privateField(owner, "keys") instanceof Object[]);
    assertTrue(privateField(owner, "values") instanceof Object[]);
    assertTrue(privateField(owner, "hashes") instanceof int[]);
    assertTrue(privateField(owner, "index") instanceof int[]);
    assertEquals(1024, map.keySet().size());
    assertEquals(2048, map.size());

    Iterator<Integer> order = map.keySet().iterator();
    for (int i = 0; i < 1024; i++) {
      assertEquals(Integer.valueOf(i), order.next());
      assertEquals(Arrays.asList(i, i + 1024), map.get(i));
    }
    assertFalse(order.hasNext());

    for (int i = 0; i < 1024; i += 2) {
      assertEquals(Arrays.asList(i, i + 1024), map.removeAll(i));
    }
    for (int i = 0; i < 1024; i += 2) {
      assertTrue(map.put(i, -i));
      assertEquals(Collections.singletonList(-i), map.get(i));
    }
    assertEquals(1024, map.keySet().size());
    assertNull(privateField(owner, "fallback"));
    map.clear();
    assertEquals(0, map.size());
    assertEquals(0, map.keySet().size());
    assertNull(privateField(owner, "fallback"));
    map.put(77, 88);
    assertEquals(Collections.singletonList(88), map.get(77));
  }

  public void testCollisionFallbackNullKeysAndEntryViewsRetainSemantics() throws Exception {
    LinkedListMultimap<CollisionKey, String> map = LinkedListMultimap.create();
    map.put(null, "null-a");
    for (int i = 0; i < 64; i++) {
      map.put(new CollisionKey(i), "v" + i);
      map.put(new CollisionKey(i), "w" + i);
    }
    map.put(null, "null-b");
    assertEquals(130, map.size());
    assertEquals(Arrays.asList("null-a", "null-b"), map.get(null));
    Object owner = directory(map);
    assertNotNull("flooding must use the existing JDK map fallback",
        privateField(owner, "fallback"));
    for (int i = 0; i < 64; i++) {
      assertEquals(Arrays.asList("v" + i, "w" + i), map.get(new CollisionKey(i)));
    }
    List<Map.Entry<CollisionKey, String>> entries = map.entries();
    assertEquals("null-a", entries.get(0).getValue());
    Map.Entry<CollisionKey, String> escaped = entries.get(1);
    escaped.setValue("changed");
    assertEquals("changed", entries.get(1).getValue());
    assertEquals(Arrays.asList("null-a", "null-b"), map.get(null));
    assertEquals(Arrays.asList("changed", "w0"), map.get(new CollisionKey(0)));
    assertEquals(Arrays.asList("v1", "w1"), map.removeAll(new CollisionKey(1)));
    assertFalse(map.containsKey(new CollisionKey(1)));
    assertEquals(128, map.size());
  }

  public void testRandomizedMutationsMatchIndependentPerKeyLists() {
    LinkedListMultimap<Integer, Integer> map = LinkedListMultimap.create(2);
    Map<Integer, List<Integer>> oracle = new HashMap<>();
    Random random = new Random(0x4d334b4559444952L);
    for (int step = 0; step < 12000; step++) {
      Integer key = random.nextInt(16) == 0 ? null : Integer.valueOf(random.nextInt(37));
      Integer value = random.nextInt(8) == 0 ? null : Integer.valueOf(random.nextInt(200) - 100);
      List<Integer> expected = oracle.get(key);
      int op = random.nextInt(10);
      if (op < 4) {
        map.put(key, value);
        if (expected == null) {
          expected = new ArrayList<>();
          oracle.put(key, expected);
        }
        expected.add(value);
      } else if (op == 4) {
        List<Integer> removed = map.removeAll(key);
        List<Integer> prior = oracle.remove(key);
        assertEquals(prior == null ? Collections.emptyList() : prior, removed);
      } else if (op == 5) {
        int index = expected == null ? 0 : random.nextInt(expected.size() + 1);
        map.get(key).add(index, value);
        if (expected == null) {
          expected = new ArrayList<>();
          oracle.put(key, expected);
        }
        expected.add(index, value);
      } else if (op == 6 && expected != null) {
        int index = random.nextInt(expected.size());
        assertEquals(expected.remove(index), map.get(key).remove(index));
        if (expected.isEmpty()) {
          oracle.remove(key);
        }
      } else if (op == 7 && expected != null) {
        int index = random.nextInt(expected.size());
        assertEquals(expected.set(index, value), map.get(key).set(index, value));
      } else if (op == 8 && (step & 127) == 0) {
        oracle.clear();
        map.clear();
      } else if (op == 9) {
        List<Integer> values = Arrays.asList(value, value);
        List<Integer> prior = expected == null ? Collections.emptyList()
            : new ArrayList<>(expected);
        assertEquals(prior, map.replaceValues(key, values));
        oracle.put(key, new ArrayList<>(values));
      }
      int count = 0;
      for (List<Integer> values : oracle.values()) {
        count += values.size();
      }
      assertEquals("size step=" + step, count, map.size());
      assertEquals(oracle.size(), map.keySet().size());
      for (Integer probe : oracle.keySet()) {
        assertEquals(oracle.get(probe), map.get(probe));
        assertTrue(map.containsKey(probe));
      }
      if ((step & 63) == 0) {
        for (int i = 0; i < 37; i++) {
          assertEquals(oracle.containsKey(i), map.containsKey(i));
        }
      }
    }
  }

  public void testSerializationAndKeyHeadRemovalKeepGlobalOrder() throws Exception {
    LinkedListMultimap<String, Integer> map = LinkedListMultimap.create();
    map.put("a", 1);
    map.put("b", 2);
    map.put("a", 3);
    map.get("a").remove(0);
    assertEquals(Arrays.asList("b", "a"), new ArrayList<>(map.keySet()));
    assertEquals(Arrays.asList(2, 3), map.values());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(map);
    }
    LinkedListMultimap<?, ?> copy;
    try (ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (LinkedListMultimap<?, ?>) in.readObject();
    }
    assertEquals(map.entries(), copy.entries());
    assertEquals(map.keySet(), copy.keySet());
    assertEquals(map.size(), copy.size());
    assertTrue(directory(copy) instanceof ArrayKeyDirectory);
  }

  public void testNegativeExpectedSizeStillRejected() {
    try {
      LinkedListMultimap.create(-1);
      fail("negative expectedKeys accepted");
    } catch (IllegalArgumentException expected) {
      // Expected constructor contract.
    }
  }
}

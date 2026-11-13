/*
 * Copyright (C) 2026 The Guava Contributors
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

import junit.framework.TestCase;

/** Contract for clearing only live array metadata in the dense count-map owner. */
public final class ObjectCountHashMapActivePrefixClearTest extends TestCase {
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

  public void testSparseFloodedClearPreservesUnusedEntrySentinels() {
    ObjectCountHashMap<CollisionKey> map = ObjectCountHashMap.createWithExpectedSize(1 << 18);
    for (int i = 0; i < 20; i++) {
      assertEquals(0, map.put(new CollisionKey(i), i + 1));
    }
    assertTrue("Collision overflow must use the existing flood-resistant fallback",
        map.usesHashFloodingResistance());
    assertTrue(map.entries.length > map.size());
    assertEquals(20, map.size());

    map.clear();
    assertEquals(0, map.size());
    assertFalse(map.usesHashFloodingResistance());
    assertAllEntrySlotsEmpty(map);

    for (int i = 0; i < 20; i++) {
      assertEquals(0, map.get(new CollisionKey(i)));
      assertEquals(0, map.put(new CollisionKey(i), i + 1));
    }
    assertEquals(20, map.size());
    map.clear();
    assertAllEntrySlotsEmpty(map);
  }

  public void testUnusedCapacityAndCompactionRemainClearAfterReuse() {
    ObjectCountHashMap<String> map = ObjectCountHashMap.createWithExpectedSize(2);
    map.ensureCapacity(1 << 16);
    assertTrue(map.entries.length >= (1 << 16));
    for (int cycle = 0; cycle < 100; cycle++) {
      for (int i = 0; i < 37; i++) {
        assertEquals(0, map.put("key-" + i, i + 1));
      }
      for (int i = 0; i < 19; i++) {
        assertEquals(i + 1, map.remove("key-" + i));
      }
      map.clear();
      assertEquals(0, map.size());
      assertAllEntrySlotsEmpty(map);
    }
  }

  public void testDenseCapacityClearDoesNotChangeEntryShape() {
    ObjectCountHashMap<Integer> map = ObjectCountHashMap.createWithExpectedSize(16);
    for (int i = 0; i < 16; i++) {
      map.put(i, i + 1);
    }
    assertEquals(map.entries.length, map.size());
    map.clear();
    assertAllEntrySlotsEmpty(map);
  }

  private static void assertAllEntrySlotsEmpty(ObjectCountHashMap<?> map) {
    for (int i = 0; i < map.entries.length; i++) {
      if (map.entries[i] != -1L) {
        fail("entry metadata at " + i);
      }
    }
    for (int i = 0; i < map.keys.length; i++) {
      if (map.keys[i] != null) {
        fail("retained key at " + i);
      }
      if (map.values[i] != 0) {
        fail("retained count at " + i);
      }
    }
  }
}

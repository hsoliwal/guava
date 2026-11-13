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

import java.util.ArrayList;
import java.util.Arrays;
import junit.framework.TestCase;

/** Invariants of both bimap bucket tables across repeated empty clear. */
public final class HashBiMapEmptyClearTest extends TestCase {

  public void testLargeEmptyBimapRetainsInsertionAndInverseSemantics() {
    HashBiMap<Integer, String> map = HashBiMap.create(1 << 16);
    for (int cycle = 0; cycle < 40; cycle++) {
      map.clear();
      assertTrue(map.isEmpty());
      assertEquals(0, map.size());
      assertNull(map.put(cycle, "value-" + cycle));
      assertEquals("value-" + cycle, map.get(cycle));
      assertEquals(Integer.valueOf(cycle), map.inverse().get("value-" + cycle));
      assertEquals("value-" + cycle, map.remove(cycle));
      assertNull(map.inverse().get("value-" + cycle));
      assertEquals(0, map.size());
    }
    map.put(2, "two");
    map.put(1, "one");
    assertEquals(Arrays.asList(2, 1), new ArrayList<>(map.keySet()));
    map.clear();
    map.put(3, "three");
    map.put(4, "four");
    assertEquals(Arrays.asList(3, 4), new ArrayList<>(map.keySet()));
    assertEquals(Integer.valueOf(3), map.inverse().get("three"));
    assertEquals(Integer.valueOf(4), map.inverse().get("four"));
  }

  public void testEmptyFloodedTablesRetainDelegateAndInverseIndex() {
    HashBiMap<Integer, String> map = HashBiMap.create(2048);
    assertNull(map.put(1, "one"));
    map.convertToHashFloodingResistantImplementation();
    assertTrue(map.isUsingHashFloodingResistance());
    assertEquals("one", map.remove(1));
    assertTrue(map.isEmpty());
    for (int i = 0; i < 20; i++) {
      map.clear();
      assertTrue(map.isUsingHashFloodingResistance());
      assertEquals(0, map.size());
      assertNull(map.put(i, "v-" + i));
      assertEquals(Integer.valueOf(i), map.inverse().get("v-" + i));
      assertEquals("v-" + i, map.remove(i));
      assertEquals(0, map.size());
    }
  }

  public void testNullKeyValueAndForcePutAfterEmptyClear() {
    HashBiMap<String, String> map = HashBiMap.create(4096);
    map.clear();
    assertNull(map.put(null, "N"));
    assertNull(map.put("X", null));
    assertEquals("N", map.get(null));
    assertEquals("X", map.inverse().get(null));
    map.clear();
    assertNull(map.forcePut("first", "same"));
    assertNull(map.forcePut("second", "same"));
    assertFalse(map.containsKey("first"));
    assertEquals("same", map.get("second"));
    assertEquals("second", map.inverse().get("same"));
  }
}

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

/** Regression for repeated clear of an allocated, already empty CompactHashMap. */
public final class CompactHashMapEmptyClearTest extends TestCase {

  public void testPreallocatedEmptyTablesRemainUsableAfterRepeatedClears() {
    for (int expectedSize : new int[] {1, 256, 1024, 8192, 131072}) {
      CompactHashMap<Integer, String> map = CompactHashMap.createWithExpectedSize(expectedSize);
      map.allocArrays();
      assertFalse(map.needsAllocArrays());
      for (int i = 0; i < 20; i++) {
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.needsAllocArrays());
        assertNull(map.put(i, "value-" + i));
        assertEquals("value-" + i, map.get(i));
        assertEquals("value-" + i, map.remove(i));
        assertEquals(0, map.size());
      }
      assertNull(map.put(9999, "after-clear"));
      assertEquals("after-clear", map.get(9999));
      map.clear();
      assertNull(map.get(9999));
      assertEquals(0, map.size());
    }
  }

  public void testFloodDelegateStillResetsToLazyArrays() {
    CompactHashMap<Integer, String> map = CompactHashMap.create();
    map.put(1, "value");
    map.convertToHashFloodingResistantImplementation();
    assertNotNull(map.delegateOrNull());
    assertEquals("value", map.remove(1));
    assertTrue(map.isEmpty());
    map.clear();
    assertTrue(map.needsAllocArrays());
    assertNull(map.delegateOrNull());
    assertNull(map.put(2, "after-delegate-clear"));
    assertEquals("after-delegate-clear", map.get(2));
  }

  public void testLinkedSubclassRetainsOriginalClearBehavior() {
    CompactLinkedHashMap<Integer, String> map =
        CompactLinkedHashMap.createWithExpectedSize(8192);
    map.put(1, "one");
    map.put(2, "two");
    assertEquals("one", map.remove(1));
    assertEquals("two", map.remove(2));
    for (int i = 0; i < 20; i++) {
      map.clear();
      assertEquals(0, map.size());
    }
    map.put(3, "three");
    map.put(4, "four");
    assertEquals(2, map.size());
    assertEquals("three", map.get(3));
    assertEquals("four", map.get(4));
  }
}

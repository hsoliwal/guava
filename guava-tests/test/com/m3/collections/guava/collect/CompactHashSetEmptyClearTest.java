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

/** Exact compact-bucket family contract after repeated clears of an empty set. */
public final class CompactHashSetEmptyClearTest extends TestCase {

  public void testPreallocatedSetRemainsInsertableAcrossLargeEmptyClears() {
    for (int capacity : new int[] {1, 256, 1024, 8192, 131072}) {
      CompactHashSet<Integer> set = CompactHashSet.createWithExpectedSize(capacity);
      set.allocArrays();
      assertFalse(set.needsAllocArrays());
      for (int i = 0; i < 20; i++) {
        set.clear();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertFalse(set.needsAllocArrays());
        assertTrue(set.add(i));
        assertTrue(set.contains(i));
        assertFalse(set.add(i));
        assertTrue(set.remove(i));
        assertFalse(set.contains(i));
      }
      assertTrue(set.add(null));
      assertTrue(set.contains(null));
      set.clear();
      assertFalse(set.contains(null));
      assertTrue(set.isEmpty());
    }
  }

  public void testFloodDelegateMustStillResetToLazyArrays() {
    CompactHashSet<Integer> set = CompactHashSet.create();
    assertTrue(set.add(1));
    set.convertToHashFloodingResistantImplementation();
    assertNotNull(set.delegateOrNull());
    assertTrue(set.remove(1));
    assertEquals(0, set.size());
    set.clear();
    assertTrue(set.needsAllocArrays());
    assertNull(set.delegateOrNull());
    assertTrue(set.add(2));
    assertTrue(set.contains(2));
  }

  public void testLinkedSubclassPreservesInsertionOrderAfterClear() {
    CompactLinkedHashSet<Integer> set = CompactLinkedHashSet.createWithExpectedSize(8192);
    assertTrue(set.add(3));
    assertTrue(set.add(1));
    assertTrue(set.remove(3));
    assertTrue(set.remove(1));
    for (int i = 0; i < 20; i++) {
      set.clear();
      assertEquals(0, set.size());
    }
    assertTrue(set.add(7));
    assertTrue(set.add(5));
    assertEquals(Arrays.asList(7, 5), new ArrayList<>(set));
  }
}

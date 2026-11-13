/*
 * Copyright (C) 2012 The Guava Authors
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
// Copyright 2026 Synexia contributors — source-sealed array lane regression.
package com.m3.collections.guava.collect;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import junit.framework.TestCase;

/** Contract tests for the exact-owner array-lane source in CompactHashMap.putAll. */
public final class M3CompactHashMapArrayPutAllTest extends TestCase {

  public void testNullsOverwriteAndDetachment() {
    CompactHashMap<Object, Object> source = CompactHashMap.create();
    source.put(null, null);
    source.put(0, 1);
    source.put(-1, null);
    source.put("overlap", "replacement");
    CompactHashMap<Object, Object> target = CompactHashMap.create();
    target.put("overlap", "old");
    target.put("retained", 77);

    Map<Object, Object> expected = new HashMap<>();
    expected.putAll(target);
    expected.putAll(source);
    Map<Object, Object> sourceBefore = new HashMap<>(source);
    target.putAll(source);

    assertEquals(expected, target);
    assertEquals(sourceBefore, source);
    assertEquals(6, target.size());
    assertNull(target.get(null));
    assertTrue(target.containsKey(null));
    assertTrue(target.containsKey(-1));
  }

  public void testEmptySelfAliasLinkedAndDelegateFallback() {
    CompactHashMap<Integer, Integer> target = CompactHashMap.create();
    target.put(1, 1);
    target.putAll(CompactHashMap.<Integer, Integer>create());
    assertEquals(1, target.size());

    target.putAll(target); // Retain the original aliased entry-iterator path.
    assertEquals(1, target.size());
    assertEquals(Integer.valueOf(1), target.get(1));

    CompactLinkedHashMap<Integer, Integer> linked = new CompactLinkedHashMap<>(4, true);
    linked.put(4, 40);
    linked.put(5, 50);
    target.putAll(linked); // The subclass source must retain its fallback.
    assertEquals(Integer.valueOf(40), target.get(4));
    assertEquals(Integer.valueOf(50), target.get(5));

    CompactHashMap<Integer, Integer> flooding = CompactHashMap.create();
    flooding.put(8, 80);
    flooding.put(9, 90);
    flooding.convertToHashFloodingResistantImplementation();
    target.putAll(flooding); // No direct-lane read after delegate conversion.
    assertEquals(Integer.valueOf(80), target.get(8));
    assertEquals(Integer.valueOf(90), target.get(9));

    try {
      target.putAll(null);
      fail("null Map must throw");
    } catch (NullPointerException expected) {
      // Original Map.putAll contract.
    }
  }

  public void testStructuralSourceMutationDetectedBeforeNextLane() {
    final CompactHashMap<Object, Integer> source = CompactHashMap.create();
    final boolean[] armed = {false};
    final Object mutatingKey = new Object() {
      @Override
      public int hashCode() {
        if (armed[0]) {
          armed[0] = false;
          source.put("inserted-by-hash", 3);
        }
        return 31;
      }

      @Override
      public boolean equals(Object object) {
        return this == object;
      }
    };
    source.put(mutatingKey, 1);
    source.put("second", 2);
    CompactHashMap<Object, Integer> target = CompactHashMap.create();
    armed[0] = true;
    try {
      target.putAll(source);
      fail("source mutation by target hash callback must fail fast");
    } catch (ConcurrentModificationException expected) {
      assertEquals(Integer.valueOf(3), source.get("inserted-by-hash"));
    }
  }

  public void testRandomizedFullSourceAndTargetState() {
    Random random = new Random(0xA9C2913L);
    CompactHashMap<Object, Object> source = CompactHashMap.create();
    CompactHashMap<Object, Object> target = CompactHashMap.create();
    Map<Object, Object> oracle = new HashMap<>();
    for (int step = 0; step < 5_000; step++) {
      Object key = random.nextInt(11) == 0 ? null : random.nextInt(256) - 128;
      Object value = random.nextInt(7) == 0 ? null : random.nextInt();
      if (step % 4 == 0) {
        source.remove(key);
      } else {
        source.put(key, value);
      }
      if (step % 19 == 0) {
        target.clear();
        oracle.clear();
      }
      if (step % 31 == 0) {
        target.putAll(target);
      }
      oracle.putAll(source);
      target.putAll(source);
      assertEquals("target at step " + step, oracle, target);
      assertEquals("source entries", new HashMap<>(source), source);
    }
  }
}

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
package com.google.common.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import junit.framework.TestCase;

/** Entry identity of the array bimap: an entry belongs to one mapping, wherever its slot moves. */
public final class HashBiMapEntryIdentityTest extends TestCase {

  public void testEntryFollowsItsSlotThroughRemovalsOfOtherKeys() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    for (int i = 0; i < 8; i++) {
      map.put(i, "v" + i);
    }
    Entry<Integer, String> last = null;
    for (Entry<Integer, String> entry : map.entrySet()) {
      last = entry;
    }
    for (int i = 0; i < 7; i++) {
      map.remove(i);
    }
    assertEquals("v7", last.getValue());
    assertEquals("v7", last.setValue("w7"));
    assertEquals("w7", last.getValue());
    assertEquals("w7", map.get(7));
    assertEquals(Integer.valueOf(7), map.inverse().get("w7"));
  }

  public void testEntryOfReplacedMappingIsRejectedAndKeepsItsValue() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    map.put(1, "a");
    map.put(2, "b");
    Entry<Integer, String> first = map.entrySet().iterator().next();
    Entry<Integer, String> sameMapping = map.entrySet().iterator().next();
    map.put(1, "c");
    assertEquals("a", first.getValue());
    try {
      first.setValue("d");
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("a", sameMapping.getValue());
    assertEquals("c", map.get(1));
    assertEquals(Arrays.asList(1, 2), new ArrayList<>(map.keySet()));
  }

  public void testSetValueThroughOneEntryRetiresTheOtherEntryOfThatMapping() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    map.put(1, "a");
    Entry<Integer, String> writer = map.entrySet().iterator().next();
    Entry<Integer, String> other = map.entrySet().iterator().next();
    assertEquals("a", writer.setValue("b"));
    assertEquals("b", writer.setValue("c"));
    assertEquals("a", other.getValue());
    try {
      other.setValue("d");
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("c", map.get(1));
  }

  public void testEntriesSurviveFailedReplaceAll() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    for (int i = 0; i < 6; i++) {
      map.put(i, "v" + i);
    }
    Iterator<Entry<Integer, String>> iterator = map.entrySet().iterator();
    iterator.next();
    Entry<Integer, String> second = iterator.next();
    try {
      map.replaceAll(
          (key, value) -> {
            if (key == 4) {
              throw new UnsupportedOperationException();
            }
            return value + "!";
          });
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), new ArrayList<>(map.keySet()));
    assertEquals("v1", map.get(1));
    assertEquals("v1", second.setValue("w1"));
    assertEquals("w1", map.get(1));
  }

  public void testEntriesAreRetiredBySuccessfulReplaceAll() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    map.put(1, "a");
    Entry<Integer, String> entry = map.entrySet().iterator().next();
    map.replaceAll((key, value) -> value + "!");
    assertEquals("a", entry.getValue());
    try {
      entry.setValue("b");
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("a!", map.get(1));
  }

  public void testInverseEntryFollowsItsSlotAndIsRetiredByReinsertion() {
    HashBiMap<Integer, String> map = HashBiMap.create();
    for (int i = 0; i < 4; i++) {
      map.put(i, "v" + i);
    }
    Entry<String, Integer> last = null;
    for (Entry<String, Integer> entry : map.inverse().entrySet()) {
      last = entry;
    }
    map.remove(0);
    map.remove(1);
    assertEquals(Integer.valueOf(3), last.getValue());
    assertEquals(Integer.valueOf(3), last.setValue(30));
    assertEquals("v3", map.get(30));
    map.inverse().remove("v3");
    map.put(30, "v3");
    assertEquals(Integer.valueOf(30), last.getValue());
    try {
      last.setValue(31);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("v3", map.get(30));
  }
}

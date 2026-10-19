/*
 * Copyright (C) 2007 The Guava Authors
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

import static com.m3.collections.guava.collect.Maps.filterEntries;
import static com.m3.collections.guava.collect.Maps.newTreeMap;
import static com.m3.collections.guava.truth.Truth.assertThat;

import com.m3.collections.guava.annotations.GwtCompatible;
import java.util.SortedMap;
import org.jspecify.annotations.NullMarked;

@GwtCompatible
@NullMarked
public class FilteredSortedMapTest extends AbstractFilteredMapTest {
  @Override
  SortedMap<String, Integer> createUnfiltered() {
    return newTreeMap();
  }

  public void testFirstAndLastKeyFilteredMap() {
    SortedMap<String, Integer> unfiltered = createUnfiltered();
    unfiltered.put("apple", 2);
    unfiltered.put("banana", 6);
    unfiltered.put("cat", 3);
    unfiltered.put("dog", 5);

    SortedMap<String, Integer> filtered = filterEntries(unfiltered, CORRECT_LENGTH);
    assertThat(filtered.firstKey()).isEqualTo("banana");
    assertThat(filtered.lastKey()).isEqualTo("cat");
  }

  public void testHeadSubTailMap_filteredMap() {
    SortedMap<String, Integer> unfiltered = createUnfiltered();
    unfiltered.put("apple", 2);
    unfiltered.put("banana", 6);
    unfiltered.put("cat", 4);
    unfiltered.put("dog", 3);
    SortedMap<String, Integer> filtered = filterEntries(unfiltered, CORRECT_LENGTH);

    assertEquals(ImmutableMap.of("banana", 6), filtered.headMap("dog"));
    assertEquals(ImmutableMap.of(), filtered.headMap("banana"));
    assertEquals(ImmutableMap.of("banana", 6, "dog", 3), filtered.headMap("emu"));

    assertEquals(ImmutableMap.of("banana", 6), filtered.subMap("banana", "dog"));
    assertEquals(ImmutableMap.of("dog", 3), filtered.subMap("cat", "emu"));

    assertEquals(ImmutableMap.of("dog", 3), filtered.tailMap("cat"));
    assertEquals(ImmutableMap.of("banana", 6, "dog", 3), filtered.tailMap("banana"));
  }
}

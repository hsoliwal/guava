/*
 * Copyright (C) 2008 The Guava Authors
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

package com.m3.collections.guava.collect.testing.testers;

import static com.m3.collections.guava.collect.testing.features.CollectionSize.SEVERAL;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ZERO;
import static com.m3.collections.guava.collect.testing.features.MapFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION;
import static com.m3.collections.guava.collect.testing.features.MapFeature.SUPPORTS_REMOVE;
import static org.junit.Assert.assertThrows;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.AbstractMapTester;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import com.m3.collections.guava.collect.testing.features.MapFeature;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * A generic JUnit test which tests {@code clear()} operations on a map. Can't be invoked directly;
 * please see {@link com.m3.collections.guava.collect.testing.MapTestSuiteBuilder}.
 *
 * @author George van den Driessche
 * @author Chris Povirk
 */
@GwtCompatible
public class MapClearTester<K, V> extends AbstractMapTester<K, V> {
  @MapFeature.Require(SUPPORTS_REMOVE)
  public void testClear() {
    getMap().clear();
    assertTrue("After clear(), a map should be empty.", getMap().isEmpty());
    assertEquals(0, getMap().size());
    assertFalse(getMap().entrySet().iterator().hasNext());
  }

  @MapFeature.Require({FAILS_FAST_ON_CONCURRENT_MODIFICATION, SUPPORTS_REMOVE})
  @CollectionSize.Require(SEVERAL)
  public void testClearConcurrentWithEntrySetIteration() {
    Iterator<Entry<K, V>> iterator = getMap().entrySet().iterator();
    getMap().clear();
    assertThrows(ConcurrentModificationException.class, iterator::next);
  }

  @MapFeature.Require({FAILS_FAST_ON_CONCURRENT_MODIFICATION, SUPPORTS_REMOVE})
  @CollectionSize.Require(SEVERAL)
  public void testClearConcurrentWithKeySetIteration() {
    Iterator<K> iterator = getMap().keySet().iterator();
    getMap().clear();
    assertThrows(ConcurrentModificationException.class, iterator::next);
  }

  @MapFeature.Require({FAILS_FAST_ON_CONCURRENT_MODIFICATION, SUPPORTS_REMOVE})
  @CollectionSize.Require(SEVERAL)
  public void testClearConcurrentWithValuesIteration() {
    Iterator<V> iterator = getMap().values().iterator();
    getMap().clear();
    assertThrows(ConcurrentModificationException.class, iterator::next);
  }

  @MapFeature.Require(absent = SUPPORTS_REMOVE)
  @CollectionSize.Require(absent = ZERO)
  public void testClear_unsupported() {
    assertThrows(UnsupportedOperationException.class, () -> getMap().clear());
    expectUnchanged();
  }

  @MapFeature.Require(absent = SUPPORTS_REMOVE)
  @CollectionSize.Require(ZERO)
  public void testClear_unsupportedByEmptyCollection() {
    try {
      getMap().clear();
    } catch (UnsupportedOperationException tolerated) {
    }
    expectUnchanged();
  }
}

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

package com.m3.collections.guava.collect.testing.testers;

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SUPPORTS_ADD;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ZERO;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.MinimalCollection;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.collect.testing.features.CollectionSize;

/**
 * A generic JUnit test which tests addAll operations on a set. Can't be invoked directly; please
 * see {@link com.m3.collections.guava.collect.testing.SetTestSuiteBuilder}.
 *
 * @author Kevin Bourrillion
 */
@GwtCompatible
public class SetAddAllTester<E> extends AbstractSetTester<E> {
  @CollectionFeature.Require(SUPPORTS_ADD)
  @CollectionSize.Require(absent = ZERO)
  public void testAddAll_supportedSomePresent() {
    assertTrue(
        "add(somePresent) should return true", getSet().addAll(MinimalCollection.of(e3(), e0())));
    expectAdded(e3());
  }

  @CollectionFeature.Require(SUPPORTS_ADD)
  public void testAddAll_withDuplicates() {
    MinimalCollection<E> elementsToAdd = MinimalCollection.of(e3(), e4(), e3(), e4());
    assertTrue("add(hasDuplicates) should return true", getSet().addAll(elementsToAdd));
    expectAdded(e3(), e4());
  }

  @CollectionFeature.Require(SUPPORTS_ADD)
  @CollectionSize.Require(absent = ZERO)
  public void testAddAll_supportedAllPresent() {
    assertFalse("add(allPresent) should return false", getSet().addAll(MinimalCollection.of(e0())));
    expectUnchanged();
  }
}

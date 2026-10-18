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

import static com.m3.collections.guava.collect.testing.features.CollectionSize.ZERO;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.AbstractCollectionTester;
import com.m3.collections.guava.collect.testing.features.CollectionSize;

/**
 * A generic JUnit test which tests {@code isEmpty()} operations on a collection. Can't be invoked
 * directly; please see {@link com.m3.collections.guava.collect.testing.CollectionTestSuiteBuilder}.
 *
 * @author Kevin Bourrillion
 */
@GwtCompatible
public class CollectionIsEmptyTester<E> extends AbstractCollectionTester<E> {
  @CollectionSize.Require(ZERO)
  public void testIsEmpty_yes() {
    assertTrue("isEmpty() should return true", collection.isEmpty());
  }

  @CollectionSize.Require(absent = ZERO)
  public void testIsEmpty_no() {
    assertFalse("isEmpty() should return false", collection.isEmpty());
  }
}

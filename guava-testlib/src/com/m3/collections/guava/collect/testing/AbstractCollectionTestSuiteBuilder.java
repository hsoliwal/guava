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

package com.m3.collections.guava.collect.testing;

import static java.util.Arrays.asList;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.collect.testing.testers.CollectionAddAllTester;
import com.m3.collections.guava.collect.testing.testers.CollectionAddTester;
import com.m3.collections.guava.collect.testing.testers.CollectionClearTester;
import com.m3.collections.guava.collect.testing.testers.CollectionContainsAllTester;
import com.m3.collections.guava.collect.testing.testers.CollectionContainsTester;
import com.m3.collections.guava.collect.testing.testers.CollectionCreationTester;
import com.m3.collections.guava.collect.testing.testers.CollectionEqualsTester;
import com.m3.collections.guava.collect.testing.testers.CollectionForEachTester;
import com.m3.collections.guava.collect.testing.testers.CollectionIsEmptyTester;
import com.m3.collections.guava.collect.testing.testers.CollectionIteratorTester;
import com.m3.collections.guava.collect.testing.testers.CollectionRemoveAllTester;
import com.m3.collections.guava.collect.testing.testers.CollectionRemoveIfTester;
import com.m3.collections.guava.collect.testing.testers.CollectionRemoveTester;
import com.m3.collections.guava.collect.testing.testers.CollectionRetainAllTester;
import com.m3.collections.guava.collect.testing.testers.CollectionSerializationTester;
import com.m3.collections.guava.collect.testing.testers.CollectionSizeTester;
import com.m3.collections.guava.collect.testing.testers.CollectionSpliteratorTester;
import com.m3.collections.guava.collect.testing.testers.CollectionStreamTester;
import com.m3.collections.guava.collect.testing.testers.CollectionToArrayTester;
import com.m3.collections.guava.collect.testing.testers.CollectionToStringTester;
import java.util.Collection;
import java.util.List;

/**
 * Abstract superclass of all test-suite builders for collection interfaces.
 *
 * @author George van den Driessche
 */
@GwtIncompatible
public abstract class AbstractCollectionTestSuiteBuilder<
        B extends AbstractCollectionTestSuiteBuilder<B, E>, E>
    extends PerCollectionSizeTestSuiteBuilder<B, TestCollectionGenerator<E>, Collection<E>, E> {
  @SuppressWarnings("rawtypes") // class literals
  @Override
  protected List<Class<? extends AbstractTester>> getTesters() {
    return asList(
        CollectionAddAllTester.class,
        CollectionAddTester.class,
        CollectionClearTester.class,
        CollectionContainsAllTester.class,
        CollectionContainsTester.class,
        CollectionCreationTester.class,
        CollectionEqualsTester.class,
        CollectionForEachTester.class,
        CollectionIsEmptyTester.class,
        CollectionIteratorTester.class,
        CollectionRemoveAllTester.class,
        CollectionRemoveIfTester.class,
        CollectionRemoveTester.class,
        CollectionRetainAllTester.class,
        CollectionSerializationTester.class,
        CollectionSizeTester.class,
        CollectionSpliteratorTester.class,
        CollectionStreamTester.class,
        CollectionToArrayTester.class,
        CollectionToStringTester.class);
  }
}

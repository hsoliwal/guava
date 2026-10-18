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

package com.m3.collections.guava.collect.testing.google;

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SERIALIZABLE_INCLUDING_VIEWS;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.Multiset;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.testing.SerializableTester;
import java.util.Set;
import org.junit.Ignore;

/**
 * A generic JUnit test which tests multiset-specific serialization. Can't be invoked directly;
 * please see {@link MultisetTestSuiteBuilder}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible // but no-op
@Ignore("test runners must not instantiate and run this directly, only via suites we build")
// @Ignore affects the Android test runner, which respects JUnit 4 annotations on JUnit 3 tests.
@SuppressWarnings("JUnit4ClassUsedInJUnit3")
public class MultisetSerializationTester<E> extends AbstractMultisetTester<E> {
  @CollectionFeature.Require(SERIALIZABLE_INCLUDING_VIEWS)
  public void testEntrySetSerialization() {
    Set<Multiset.Entry<E>> expected = getMultiset().entrySet();
    assertEquals(expected, SerializableTester.reserialize(expected));
  }

  @CollectionFeature.Require(SERIALIZABLE_INCLUDING_VIEWS)
  public void testElementSetSerialization() {
    Set<E> expected = getMultiset().elementSet();
    assertEquals(expected, SerializableTester.reserialize(expected));
  }
}

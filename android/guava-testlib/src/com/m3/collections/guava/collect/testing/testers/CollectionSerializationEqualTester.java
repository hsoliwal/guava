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

package com.m3.collections.guava.collect.testing.testers;

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SERIALIZABLE;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.AbstractCollectionTester;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.testing.SerializableTester;
import org.junit.Ignore;

/**
 * Basic reserialization test for collection types that must preserve {@code equals()} behavior when
 * reserialized. (Sets and Lists, but not bare Collections.)
 *
 * @author Louis Wasserman
 */
@GwtCompatible
@Ignore("test runners must not instantiate and run this directly, only via suites we build")
// @Ignore affects the Android test runner, which respects JUnit 4 annotations on JUnit 3 tests.
@SuppressWarnings("JUnit4ClassUsedInJUnit3")
public class CollectionSerializationEqualTester<E> extends AbstractCollectionTester<E> {
  @CollectionFeature.Require(SERIALIZABLE)
  /*
   * As the class docs say, this test only makes sense for collections that define equals().
   * Accordingly, our testing framework adds it only when testing an implementation of List, Set, or
   * Multiset.
   */
  @SuppressWarnings("UndefinedEquals")
  public void testReserialize() {
    assertEquals(SerializableTester.reserialize(actualContents()), actualContents());
  }
}

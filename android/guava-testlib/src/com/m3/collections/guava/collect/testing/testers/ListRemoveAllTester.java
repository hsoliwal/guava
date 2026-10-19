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

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SUPPORTS_REMOVE;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ONE;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ZERO;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.MinimalCollection;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.collect.testing.features.CollectionSize;

/**
 * A generic JUnit test which tests removeAll operations on a list. Can't be invoked directly;
 * please see {@link com.m3.collections.guava.collect.testing.ListTestSuiteBuilder}.
 *
 * @author George van den Driessche
 */
@GwtCompatible
public class ListRemoveAllTester<E> extends AbstractListTester<E> {
  @CollectionFeature.Require(SUPPORTS_REMOVE)
  @CollectionSize.Require(absent = {ZERO, ONE})
  public void testRemoveAll_duplicate() {
    ArrayWithDuplicate<E> arrayAndDuplicate = createArrayWithDuplicateElement();
    collection = getSubjectGenerator().create(arrayAndDuplicate.elements);
    E duplicate = arrayAndDuplicate.duplicate;

    assertTrue(
        "removeAll(intersectingCollection) should return true",
        getList().removeAll(MinimalCollection.of(duplicate)));
    assertFalse(
        "after removeAll(e), a collection should not contain e even "
            + "if it initially contained e more than once.",
        getList().contains(duplicate));
  }

  // All other cases are covered by CollectionRemoveAllTester.
}

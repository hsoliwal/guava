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

package com.m3.collections.guava.collect;

import static com.m3.collections.guava.collect.Lists.newArrayList;

import com.m3.collections.guava.base.Predicate;
import com.m3.collections.guava.collect.FilteredCollectionsTestUtil.AbstractFilteredCollectionTest;
import java.util.Collection;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public final class Collections2FilterArrayListTest
    extends AbstractFilteredCollectionTest<Collection<Integer>> {
  @Override
  Collection<Integer> createUnfiltered(Iterable<Integer> contents) {
    return newArrayList(contents);
  }

  @Override
  Collection<Integer> filter(Collection<Integer> elements, Predicate<? super Integer> predicate) {
    return Collections2.filter(elements, predicate);
  }
}

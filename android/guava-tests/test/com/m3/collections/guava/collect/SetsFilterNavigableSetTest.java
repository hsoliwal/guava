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

import static com.m3.collections.guava.collect.Sets.newTreeSet;

import com.m3.collections.guava.base.Predicate;
import com.m3.collections.guava.collect.FilteredCollectionsTestUtil.AbstractFilteredNavigableSetTest;
import java.util.NavigableSet;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public final class SetsFilterNavigableSetTest extends AbstractFilteredNavigableSetTest {
  @Override
  NavigableSet<Integer> createUnfiltered(Iterable<Integer> contents) {
    return newTreeSet(contents);
  }

  @Override
  NavigableSet<Integer> filter(
      NavigableSet<Integer> elements, Predicate<? super Integer> predicate) {
    return Sets.filter(elements, predicate);
  }
}

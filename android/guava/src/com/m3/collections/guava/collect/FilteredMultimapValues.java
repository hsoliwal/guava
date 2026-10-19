/*
 * Copyright (C) 2013 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.m3.collections.guava.collect;

import static com.m3.collections.guava.base.Preconditions.checkNotNull;
import static com.m3.collections.guava.base.Predicates.and;
import static com.m3.collections.guava.base.Predicates.in;
import static com.m3.collections.guava.base.Predicates.not;
import static com.m3.collections.guava.collect.Maps.valueIterator;
import static com.m3.collections.guava.collect.Maps.valuePredicateOnEntries;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.base.Predicate;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Implementation for {@link FilteredMultimap#values()}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
final class FilteredMultimapValues<K extends @Nullable Object, V extends @Nullable Object>
    extends AbstractCollection<V> {
  private final FilteredMultimap<K, V> multimap;

  FilteredMultimapValues(FilteredMultimap<K, V> multimap) {
    this.multimap = checkNotNull(multimap);
  }

  @Override
  public Iterator<V> iterator() {
    return valueIterator(multimap.entries().iterator());
  }

  @Override
  public boolean contains(@Nullable Object o) {
    return multimap.containsValue(o);
  }

  @Override
  public int size() {
    return multimap.size();
  }

  @Override
  public boolean remove(@Nullable Object o) {
    Predicate<? super Entry<K, V>> entryPredicate = multimap.entryPredicate();
    for (Iterator<Entry<K, V>> unfilteredItr = multimap.unfiltered().entries().iterator();
        unfilteredItr.hasNext(); ) {
      Entry<K, V> entry = unfilteredItr.next();
      if (entryPredicate.apply(entry) && Objects.equals(entry.getValue(), o)) {
        unfilteredItr.remove();
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return Iterables.removeIf(
        multimap.unfiltered().entries(),
        and(multimap.entryPredicate(), valuePredicateOnEntries(in(c))));
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return Iterables.removeIf(
        multimap.unfiltered().entries(),
        and(multimap.entryPredicate(), valuePredicateOnEntries(not(in(c)))));
  }

  @Override
  public void clear() {
    multimap.clear();
  }
}

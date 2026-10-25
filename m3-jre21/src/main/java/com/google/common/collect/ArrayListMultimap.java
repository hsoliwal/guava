/*
 * Copyright (C) 2007 The Guava Authors
 * Modified 2026 by Synexia M3 contributors: use M3 array-backed map/list storage
 * and preserve Guava's trim invalidation and custom serialization protocol.
 * SPDX-License-Identifier: Apache-2.0
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

package com.google.common.collect;

import static com.google.common.collect.CollectPreconditions.checkNonnegative;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.m3.collections.M3ArrayList;
import com.m3.collections.M3LinkedHashMap;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@code Multimap} that uses an {@link M3ArrayList} to store the values for a
 * given key. An {@link M3LinkedHashMap} associates each key with its list of values. These private
 * storage owners use object arrays and integer slot/index lanes; Guava's inherited multimap policy,
 * live views, and custom serialization protocol are retained.
 *
 * <p>When iterating through the collections supplied by this class, the ordering of values for a
 * given key agrees with the order in which the values were added.
 *
 * <p>This multimap allows duplicate key-value pairs. After adding a new key-value pair equal to an
 * existing key-value pair, the {@code ArrayListMultimap} will contain entries for both the new
 * value and the old value.
 *
 * <p>Keys and values may be null. All optional multimap methods are supported, and all returned
 * views are modifiable.
 *
 * <p>The lists returned by {@link #get}, {@link #removeAll}, and {@link #replaceValues} all
 * implement {@link java.util.RandomAccess}.
 *
 * <p>This class is not threadsafe when any concurrent operations update the multimap. Concurrent
 * read operations will work correctly. To allow concurrent update operations, wrap your multimap
 * with a call to {@link Multimaps#synchronizedListMultimap}.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/NewCollectionTypesExplained#multimap">{@code Multimap}</a>.
 *
 * @author Jared Levy
 * @since 2.0
 */
@GwtCompatible(serializable = true, emulated = true)
public final class ArrayListMultimap<K extends @Nullable Object, V extends @Nullable Object>
    extends ArrayListMultimapGwtSerializationDependencies<K, V> {
  // Default from ArrayList
  private static final int DEFAULT_VALUES_PER_KEY = 3;

  @VisibleForTesting transient int expectedValuesPerKey;

  /**
   * Creates a new, empty {@code ArrayListMultimap} with the default initial capacities.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys().arrayListValues().build()}, which provides more control over the
   * underlying data structure.
   */
  public static <K extends @Nullable Object, V extends @Nullable Object>
      ArrayListMultimap<K, V> create() {
    return new ArrayListMultimap<>();
  }

  /**
   * Constructs an empty {@code ArrayListMultimap} with enough capacity to hold the specified
   * numbers of keys and values without resizing.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys(expectedKeys).arrayListValues(expectedValuesPerKey).build()}, which
   * provides more control over the underlying data structure.
   *
   * @param expectedKeys the expected number of distinct keys
   * @param expectedValuesPerKey the expected average number of values per key
   * @throws IllegalArgumentException if {@code expectedKeys} or {@code expectedValuesPerKey} is
   *     negative
   */
  public static <K extends @Nullable Object, V extends @Nullable Object>
      ArrayListMultimap<K, V> create(int expectedKeys, int expectedValuesPerKey) {
    return new ArrayListMultimap<>(expectedKeys, expectedValuesPerKey);
  }

  /**
   * Constructs an {@code ArrayListMultimap} with the same mappings as the specified multimap.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys().arrayListValues().build(multimap)}, which provides more control over
   * the underlying data structure.
   *
   * @param multimap the multimap whose contents are copied to this multimap
   */
  public static <K extends @Nullable Object, V extends @Nullable Object>
      ArrayListMultimap<K, V> create(Multimap<? extends K, ? extends V> multimap) {
    return new ArrayListMultimap<>(multimap);
  }

  private ArrayListMultimap() {
    this(12, DEFAULT_VALUES_PER_KEY);
  }

  private ArrayListMultimap(int expectedKeys, int expectedValuesPerKey) {
    super(new M3LinkedHashMap<K, Collection<V>>(checkNonnegative(expectedKeys, "expectedSize")));
    checkNonnegative(expectedValuesPerKey, "expectedValuesPerKey");
    this.expectedValuesPerKey = expectedValuesPerKey;
  }

  private ArrayListMultimap(Multimap<? extends K, ? extends V> multimap) {
    this(
        multimap.keySet().size(),
        (multimap instanceof ArrayListMultimap)
            ? ((ArrayListMultimap<?, ?>) multimap).expectedValuesPerKey
            : DEFAULT_VALUES_PER_KEY);
    putAll(multimap);
  }

  /**
   * Creates a new, empty {@code M3ArrayList} to hold the values for an arbitrary key.
   */
  @Override
  List<V> createCollection() {
    return new M3ArrayList<>(expectedValuesPerKey);
  }

  /**
   * Reduces the memory used by this {@code ArrayListMultimap}, if feasible.
   *
   * @deprecated For a {@link ListMultimap} that automatically trims to size, use {@link
   *     ImmutableListMultimap}. If you need a mutable collection, remove the {@code trimToSize}
   *     call, or switch to a {@code HashMap<K, ArrayList<V>>}.
   */
  @Deprecated
  public void trimToSize() {
    for (Collection<V> collection : backingMap().values()) {
      M3ArrayList<V> arrayList = (M3ArrayList<V>) collection;
      arrayList.trimToSize(true);
    }
  }

  /**
   * @serialData expectedValuesPerKey, number of distinct keys, and then for each distinct key: the
   *     key, number of values for that key, and the key's values
   */
  @GwtIncompatible // java.io.ObjectOutputStream
  @J2ktIncompatible
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMultimap(this, stream);
  }

  @GwtIncompatible // java.io.ObjectOutputStream
  @J2ktIncompatible
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    expectedValuesPerKey = DEFAULT_VALUES_PER_KEY;
    int distinctKeys = Serialization.readCount(stream);
    Map<K, Collection<V>> map = new M3LinkedHashMap<>();
    setMap(map);
    Serialization.populateMultimap(this, stream, distinctKeys);
  }

  @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 0;
}

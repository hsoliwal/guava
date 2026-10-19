/*
 * Copyright (C) 2007 The Guava Authors
 * SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * Modified 2026 by Synexia M3 contributors: array/index directory and value sets;
 * bounded primitive-LFU reuse of immutable API pair projections.
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
import static java.util.Objects.requireNonNull;

import com.m3.collections.M3LinkedHashMap;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@link Multimap} using hash tables.
 *
 * <p>The multimap does not store duplicate key-value pairs. Adding a new key-value pair equal to an
 * existing key-value pair has no effect.
 *
 * <p>Keys and values may be null. All optional multimap methods are supported, and all returned
 * views are modifiable.
 *
 * <p>This class is not threadsafe when any concurrent operations update the multimap. Concurrent
 * read operations will work correctly if the last write <i>happens-before</i> any reads. To allow
 * concurrent update operations, wrap your multimap with a call to {@link
 * Multimaps#synchronizedSetMultimap}.
 *
 * <p><b>Warning:</b> Do not modify either a key <i>or a value</i> of a {@code HashMultimap} in a
 * way that affects its {@link Object#equals} behavior. Undefined behavior and bugs will result.
 *
 * @author Jared Levy
 * @since 2.0
 */
@GwtCompatible(serializable = true, emulated = true)
public final class HashMultimap<K extends @Nullable Object, V extends @Nullable Object>
    extends HashMultimapGwtSerializationDependencies<K, V> {
  private static final int DEFAULT_VALUES_PER_KEY = 2;

  @VisibleForTesting transient int expectedValuesPerKey = DEFAULT_VALUES_PER_KEY;

  /**
   * Creates a new, empty {@code HashMultimap} with the default initial capacities.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys().hashSetValues().build()}, which provides more control over the
   * underlying data structure.
   */
  public static <K extends @Nullable Object, V extends @Nullable Object>
      HashMultimap<K, V> create() {
    return new HashMultimap<>();
  }

  /**
   * Constructs an empty {@code HashMultimap} with enough capacity to hold the specified numbers of
   * keys and values without rehashing.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys(expectedKeys).hashSetValues(expectedValuesPerKey).build()}, which
   * provides more control over the underlying data structure.
   *
   * @param expectedKeys the expected number of distinct keys
   * @param expectedValuesPerKey the expected average number of values per key
   * @throws IllegalArgumentException if {@code expectedKeys} or {@code expectedValuesPerKey} is
   *     negative
   */
  public static <K extends @Nullable Object, V extends @Nullable Object> HashMultimap<K, V> create(
      int expectedKeys, int expectedValuesPerKey) {
    return new HashMultimap<>(expectedKeys, expectedValuesPerKey);
  }

  /**
   * Constructs a {@code HashMultimap} with the same mappings as the specified multimap. If a
   * key-value mapping appears multiple times in the input multimap, it only appears once in the
   * constructed multimap.
   *
   * <p>You may also consider the equivalent {@code
   * MultimapBuilder.hashKeys().hashSetValues().build(multimap)}, which provides more control over
   * the underlying data structure.
   *
   * @param multimap the multimap whose contents are copied to this multimap
   */
  public static <K extends @Nullable Object, V extends @Nullable Object> HashMultimap<K, V> create(
      Multimap<? extends K, ? extends V> multimap) {
    return new HashMultimap<>(multimap);
  }

  private HashMultimap() {
    this(12, DEFAULT_VALUES_PER_KEY);
  }

  private HashMultimap(int expectedKeys, int expectedValuesPerKey) {
    super(new M3LinkedHashMap<K, Collection<V>>(checkNonnegative(expectedKeys, "expectedSize")));
    Preconditions.checkArgument(expectedValuesPerKey >= 0);
    this.expectedValuesPerKey = expectedValuesPerKey;
  }

  private HashMultimap(Multimap<? extends K, ? extends V> multimap) {
    super(new M3LinkedHashMap<K, Collection<V>>(multimap.keySet().size()));
    putAll(multimap);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates an empty array/index-backed set for the values of one key.
   *
   * @return a new array/index-backed set containing values for one key
   */
  @Override
  Set<V> createCollection() {
    return new ValueSet<>(expectedValuesPerKey);
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

  @GwtIncompatible // java.io.ObjectInputStream
  @J2ktIncompatible
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    expectedValuesPerKey = DEFAULT_VALUES_PER_KEY;
    int distinctKeys = Serialization.readCount(stream);
    Map<K, Collection<V>> map = new M3LinkedHashMap<>(12);
    setMap(map);
    Serialization.populateMultimap(this, stream, distinctKeys);
  }


  // Immutable pair projections only. Live asMap/count entries retain inherited policy.
  // Guava creates the temporary entry before this boundary; cache hits do not eliminate it.
  // Immutable snapshots, including removed pairs, remain bounded128 until LFU eviction.
  private transient @Nullable M3LFUCache<Map.Entry<K, V>> apiEntries;

  private synchronized Map.Entry<K, V> cachedEntry(Map.Entry<K, V> fresh) {
    if (apiEntries == null) apiEntries = new M3LFUCache<>(128);
    K key = fresh.getKey();
    V value = fresh.getValue();
    int coordinate = System.identityHashCode(key) & Integer.MAX_VALUE;
    long generation = Integer.toUnsignedLong(System.identityHashCode(value));
    Map.Entry<K, V> cached = apiEntries.get(coordinate, generation, 0L);
    // Identity-hash collisions reduce reuse, but must never change representatives.
    if (cached != null && cached.getKey() == key && cached.getValue() == value) return cached;
    apiEntries.put(coordinate, generation, 0L, fresh);
    return fresh;
  }

  @Override
  public void forEach(java.util.function.BiConsumer<? super K, ? super V> action) {
    requireNonNull(action);
    backingMap().forEach((key, values) -> {
      for (V value : values) action.accept(key, value);
    });
  }

  @Override
  Iterator<Map.Entry<K, V>> entryIterator() {
    Iterator<Map.Entry<K, V>> delegate = super.entryIterator();
    return new Iterator<>() {
      @Override public boolean hasNext() { return delegate.hasNext(); }
      @Override public Map.Entry<K, V> next() { return cachedEntry(delegate.next()); }
      @Override public void remove() { delegate.remove(); }
    };
  }

  @Override
  Spliterator<Map.Entry<K, V>> entrySpliterator() {
    return new ProjectionSpliterator(super.entrySpliterator());
  }

  private final class ProjectionSpliterator implements Spliterator<Map.Entry<K, V>> {
    private final Spliterator<Map.Entry<K, V>> delegate;
    ProjectionSpliterator(Spliterator<Map.Entry<K, V>> delegate) { this.delegate = delegate; }
    @Override public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
      requireNonNull(action);
      return delegate.tryAdvance(entry -> action.accept(cachedEntry(entry)));
    }
    @Override public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
      requireNonNull(action);
      delegate.forEachRemaining(entry -> action.accept(cachedEntry(entry)));
    }
    @Override public @Nullable Spliterator<Map.Entry<K, V>> trySplit() {
      Spliterator<Map.Entry<K, V>> split = delegate.trySplit();
      return split == null ? null : new ProjectionSpliterator(split);
    }
    @Override public long estimateSize() { return delegate.estimateSize(); }
    @Override public int characteristics() { return delegate.characteristics(); }
    @Override public Comparator<? super Map.Entry<K, V>> getComparator() {
      return delegate.getComparator();
    }
  }

  // Adapted from M3LinkedHashSet's primitive map-key iteration/membership pattern.
  // One presence marker is shared; neither map retains per-element Entry/Node objects.
  private static final class ValueSet<E> extends AbstractSet<E> implements Serializable {
    private static final long serialVersionUID = 1L;
    private transient M3LinkedHashMap<E, Boolean> keys;
    ValueSet(int expectedSize) { keys = new M3LinkedHashMap<>(expectedSize); }
    @Override public int size() { return keys.size(); }
    @Override public boolean contains(Object item) { return keys.containsKey(item); }
    @Override public boolean add(E item) { return keys.put(item, Boolean.TRUE) == null; }
    @Override public boolean remove(Object item) { return keys.remove(item) != null; }
    @Override public void clear() { keys.clear(); }
    @Override public Iterator<E> iterator() { return keys.keySet().iterator(); }

    private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      stream.writeInt(size());
      for (E item : this) stream.writeObject(item);
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
      stream.defaultReadObject();
      int count = stream.readInt();
      if (count < 0) throw new java.io.InvalidObjectException("negative set size");
      keys = new M3LinkedHashMap<>(Math.min(count, 16));
      for (int i = 0; i < count; i++) keys.put((E) stream.readObject(), Boolean.TRUE);
    }
  }

  // Adapted from actual M3LFUCache at M3jdk21@6ae20943; mechanism identical to qualified HashBiMap v7.
  private static final class M3LFUCache<V> {
      private static final byte EMPTY = 0;
      private static final byte USED = 1;
      private static final byte DELETED = 2;
      private static final int MIN_TABLE_CAPACITY = 16;
      private static final int MAX_FREQUENCY = Integer.MAX_VALUE;

      private final int maximumSize;
      private final int mask;
      private final int[] slots;
      private final long[] generations;
      private final long[] leases;
      private final Object[] values;
      private final int[] frequencies;
      private final long[] touched;
      private final byte[] states;
      private int size;
      private int occupied;
      private long clock;

      M3LFUCache(int maximumSize) {
          if (maximumSize < 1) {
              throw new IllegalArgumentException("maximumSize must be positive");
          }
          this.maximumSize = maximumSize;
          int capacity = MIN_TABLE_CAPACITY;
          long minimum = Math.max((long) MIN_TABLE_CAPACITY, (long) maximumSize * 2L);
          while (capacity < minimum) {
              if (capacity >= (1 << 30)) {
                  throw new IllegalArgumentException("maximumSize is too large");
              }
              capacity <<= 1;
          }
          slots = new int[capacity];
          generations = new long[capacity];
          leases = new long[capacity];
          values = new Object[capacity];
          frequencies = new int[capacity];
          touched = new long[capacity];
          states = new byte[capacity];
          mask = capacity - 1;
      }

      @SuppressWarnings("unchecked")
      V get(int slot, long generation, long lease) {
          if (slot < 0) {
              throw new IllegalArgumentException("negative physical slot");
          }
          int index = find(slot, generation, lease);
          if (index < 0) {
              return null;
          }
          touch(index);
          return (V) values[index];
      }

      void put(int slot, long generation, long lease, V value) {
          if (slot < 0) {
              throw new IllegalArgumentException("negative physical slot");
          }
          requireNonNull(value, "value");
          int existing = find(slot, generation, lease);
          if (existing >= 0) {
              values[existing] = value;
              touch(existing);
              return;
          }
          if (size == maximumSize) {
              evictLeastFrequentlyUsed();
          }
          if ((long) occupied + 1L >= (long) states.length * 2L / 3L) {
              rehash();
          }
          int hash = hash(slot, generation, lease);
          int index = hash & mask;
          int firstDeleted = -1;
          while (states[index] != EMPTY) {
              if (states[index] == DELETED && firstDeleted < 0) {
                  firstDeleted = index;
              }
              index = (index + 1) & mask;
          }
          int target = firstDeleted >= 0 ? firstDeleted : index;
          if (states[target] == EMPTY) {
              occupied++;
          }
          slots[target] = slot;
          generations[target] = generation;
          leases[target] = lease;
          values[target] = value;
          frequencies[target] = 1;
          states[target] = USED;
          touched[target] = nextTick();
          size++;
      }

      /** Remove every cached generation for a physical slot before it is reused. */
      boolean removeSlot(int slot) {
          if (slot < 0) {
              return false;
          }
          boolean removed = false;
          for (int i = 0; i < states.length; i++) {
              if (states[i] == USED && slots[i] == slot) {
                  deleteAt(i);
                  removed = true;
              }
          }
          if (size == 0) {
              Arrays.fill(states, EMPTY);
              occupied = 0;
          } else if ((long) occupied > (long) size * 2L) {
              rehash();
          }
          return removed;
      }

      void clear() {
          Arrays.fill(slots, 0);
          Arrays.fill(generations, 0L);
          Arrays.fill(leases, 0L);
          Arrays.fill(values, null);
          Arrays.fill(frequencies, 0);
          Arrays.fill(touched, 0L);
          Arrays.fill(states, EMPTY);
          size = 0;
          occupied = 0;
          clock = 0L;
      }

      int size() {
          return size;
      }

      int capacity() {
          return maximumSize;
      }

      private int find(int slot, long generation, long lease) {
          int hash = hash(slot, generation, lease);
          int index = hash & mask;
          while (states[index] != EMPTY) {
              if (states[index] == USED && slots[index] == slot && generations[index] == generation && leases[index] == lease) {
                  return index;
              }
              index = (index + 1) & mask;
          }
          return -1;
      }

      private void touch(int index) {
          if (frequencies[index] < MAX_FREQUENCY) {
              frequencies[index]++;
          }
          touched[index] = nextTick();
      }

      private void evictLeastFrequentlyUsed() {
          int victim = -1;
          for (int i = 0; i < states.length; i++) {
              if (states[i] != USED) {
                  continue;
              }
              if (victim < 0 || frequencies[i] < frequencies[victim]
                      || frequencies[i] == frequencies[victim] && touched[i] < touched[victim]) {
                  victim = i;
              }
          }
          if (victim < 0) {
              throw new AssertionError("cache size and occupancy diverged");
          }
          deleteAt(victim);
      }

      private void deleteAt(int index) {
          slots[index] = 0;
          generations[index] = 0L;
          leases[index] = 0L;
          values[index] = null;
          frequencies[index] = 0;
          touched[index] = 0L;
          states[index] = DELETED;
          size--;
      }

      private void rehash() {
          int capacity = states.length;
          int[] oldSlots = Arrays.copyOf(slots, capacity);
          long[] oldGenerations = Arrays.copyOf(generations, capacity);
          long[] oldLeases = Arrays.copyOf(leases, capacity);
          Object[] oldValues = Arrays.copyOf(values, capacity);
          int[] oldFrequencies = Arrays.copyOf(frequencies, capacity);
          long[] oldTouched = Arrays.copyOf(touched, capacity);
          byte[] oldStates = Arrays.copyOf(states, capacity);
          Arrays.fill(slots, 0);
          Arrays.fill(generations, 0L);
          Arrays.fill(leases, 0L);
          Arrays.fill(values, null);
          Arrays.fill(frequencies, 0);
          Arrays.fill(touched, 0L);
          Arrays.fill(states, EMPTY);
          size = 0;
          occupied = 0;
          for (int i = 0; i < capacity; i++) {
              if (oldStates[i] != USED) {
                  continue;
              }
              int index = hash(oldSlots[i], oldGenerations[i], oldLeases[i]) & mask;
              while (states[index] == USED) {
                  index = (index + 1) & mask;
              }
              slots[index] = oldSlots[i];
              generations[index] = oldGenerations[i];
              leases[index] = oldLeases[i];
              values[index] = oldValues[i];
              frequencies[index] = oldFrequencies[i];
              touched[index] = oldTouched[i];
              states[index] = USED;
              size++;
              occupied++;
          }
      }

      private long nextTick() {
          if (clock == Long.MAX_VALUE) {
              long next = 0L;
              for (int i = 0; i < states.length; i++) {
                  if (states[i] == USED) {
                      touched[i] = ++next;
                  }
              }
              clock = next;
          }
          return ++clock;
      }

      private static int hash(int slot, long generation, long lease) {
          int h = slot * 0x9E3779B9;
          h ^= Integer.rotateLeft(Long.hashCode(generation) * 0x85EBCA6B, 13);
          h ^= Integer.rotateLeft(Long.hashCode(lease) * 0xC2B2AE35, 7);
          h ^= h >>> 16;
          return h;
      }
  }

  @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 0;
}

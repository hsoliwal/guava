/*
 * Copyright (C) 2007 The Guava Authors
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

// Modified 2026 by Hitesh Soliwal and Contributors to the Synexia Project: JRE policy retained over private indexed payload lanes.
// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.CollectPreconditions.checkNonnegative;
import static com.google.common.collect.Hashing.smearedHash;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.base.Objects;
import com.google.common.collect.Maps.IteratorBasedAbstractMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.j2objc.annotations.RetainedWith;
import com.google.j2objc.annotations.Weak;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BiMap} backed by two hash tables. This implementation allows null keys and values. A
 * {@code HashBiMap} and its inverse are both serializable.
 *
 * <p>This implementation guarantees insertion-based iteration order of its keys.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/NewCollectionTypesExplained#bimap">{@code BiMap} </a>.
 *
 * @author Louis Wasserman
 * @author Mike Bostock
 * @since 2.0
 */
@GwtCompatible(emulated = true)
public final class HashBiMap<K extends @Nullable Object, V extends @Nullable Object>
    extends IteratorBasedAbstractMap<K, V> implements BiMap<K, V>, Serializable {

  /** Returns a new, empty {@code HashBiMap} with the default initial capacity (16). */
  public static <K extends @Nullable Object, V extends @Nullable Object> HashBiMap<K, V> create() {
    return create(16);
  }

  /**
   * Constructs a new, empty bimap with the specified expected size.
   *
   * @param expectedSize the expected number of entries
   * @throws IllegalArgumentException if the specified expected size is negative
   */
  public static <K extends @Nullable Object, V extends @Nullable Object> HashBiMap<K, V> create(
      int expectedSize) {
    return new HashBiMap<>(expectedSize);
  }

  /**
   * Constructs a new bimap containing initial values from {@code map}. The bimap is created with an
   * initial capacity sufficient to hold the mappings in the specified map.
   */
  public static <K extends @Nullable Object, V extends @Nullable Object> HashBiMap<K, V> create(
      Map<? extends K, ? extends V> map) {
    HashBiMap<K, V> bimap = create(map.size());
    bimap.putAll(map);
    return bimap;
  }

  private static final double LOAD_FACTOR = 1.0;
  private static final int ABSENT = -1;
  private static final byte FREE = 0;
  private static final byte LIVE = 1;
  private static final byte DEAD = 2;
  private static final byte RESERVED = 3;

  private transient @Nullable K[] keys;
  private transient @Nullable V[] values;
  private transient int[] keyHashes;
  private transient int[] valueHashes;
  private transient int[] hashTableKToV;
  private transient int[] hashTableVToK;
  private transient int[] nextInKToVBucket;
  private transient int[] nextInVToKBucket;
  private transient int[] nextInKeyInsertionOrder;
  private transient int[] prevInKeyInsertionOrder;
  private transient byte[] states;
  private transient long[] generations;
  private transient int[] freeNext;
  private transient int highWater;
  private transient int freeHead;
  private transient int retiredHead;
  private transient int retiredCount;
  private transient int[] pins;
  private transient int pinCount;
  private transient int firstInKeyInsertionOrder;
  private transient int lastInKeyInsertionOrder;
  private transient int size;
  private transient int mask;
  private transient int modCount;
  // Lazily materialized API views, never structural records. Leases are never recycled.
  private transient @Nullable M3LFUCache<Object> apiEntries;
  private transient long nextApiLease;

  private long apiLease() {
    long result = nextApiLease;
    if (result != 0) nextApiLease = result == Long.MAX_VALUE ? 0 : result + 1;
    return result; // Zero disables caching after exhaustion; it can never alias an old lease.
  }

  private M3LFUCache<Object> apiEntries() {
    M3LFUCache<Object> result = apiEntries;
    if (result == null) apiEntries = result = new M3LFUCache<>(128);
    return result;
  }

  private HashBiMap(int expectedSize) {
    init(expectedSize);
  }

  @SuppressWarnings("unchecked")
  private void init(int expectedSize) {
    checkNonnegative(expectedSize, "expectedSize");
    int tableSize = Hashing.closedTableSize(expectedSize, LOAD_FACTOR);
    int capacity = Math.min(expectedSize, tableSize);
    keys = (K[]) new Object[capacity];
    values = (V[]) new Object[capacity];
    keyHashes = new int[capacity];
    valueHashes = new int[capacity];
    hashTableKToV = createTable(tableSize);
    hashTableVToK = createTable(tableSize);
    nextInKToVBucket = createTable(capacity);
    nextInVToKBucket = createTable(capacity);
    nextInKeyInsertionOrder = createTable(capacity);
    prevInKeyInsertionOrder = createTable(capacity);
    states = new byte[capacity];
    generations = new long[capacity];
    freeNext = createTable(capacity);
    pins = createTable(8);
    pinCount = 0;
    highWater = 0;
    freeHead = ABSENT;
    retiredCount = 0;
    retiredHead = ABSENT;
    firstInKeyInsertionOrder = ABSENT;
    lastInKeyInsertionOrder = ABSENT;
    size = 0;
    mask = tableSize - 1;
    modCount = 0;
    apiEntries = null;
    nextApiLease = 1;
  }

  /**
   * Finds and removes {@code entry} from the bucket linked lists in both the key-to-value direction
   * and the value-to-key direction.
   */
  private void delete(int entry) {
    if (entry < 0 || entry >= highWater || states[entry] != LIVE) {
      throw new NullPointerException();
    }
    int keyBucket = keyHashes[entry] & mask;
    int prevBucketEntry = ABSENT;
    for (int bucketEntry = hashTableKToV[keyBucket];
        true;
        bucketEntry = nextInKToVBucket[bucketEntry]) {
      if (bucketEntry == ABSENT) {
        throw new NullPointerException();
      }
      if (bucketEntry == entry) {
        if (prevBucketEntry == ABSENT) {
          hashTableKToV[keyBucket] = nextInKToVBucket[entry];
        } else {
          nextInKToVBucket[prevBucketEntry] = nextInKToVBucket[entry];
        }
        break;
      }
      prevBucketEntry = bucketEntry;
    }

    int valueBucket = valueHashes[entry] & mask;
    prevBucketEntry = ABSENT;
    for (int bucketEntry = hashTableVToK[valueBucket];
        true;
        bucketEntry = nextInVToKBucket[bucketEntry]) {
      if (bucketEntry == ABSENT) {
        throw new NullPointerException();
      }
      if (bucketEntry == entry) {
        if (prevBucketEntry == ABSENT) {
          hashTableVToK[valueBucket] = nextInVToKBucket[entry];
        } else {
          nextInVToKBucket[prevBucketEntry] = nextInVToKBucket[entry];
        }
        break;
      }
      prevBucketEntry = bucketEntry;
    }

    if (prevInKeyInsertionOrder[entry] == ABSENT) {
      firstInKeyInsertionOrder = nextInKeyInsertionOrder[entry];
    } else {
      nextInKeyInsertionOrder[prevInKeyInsertionOrder[entry]] = nextInKeyInsertionOrder[entry];
    }

    if (nextInKeyInsertionOrder[entry] == ABSENT) {
      lastInKeyInsertionOrder = prevInKeyInsertionOrder[entry];
    } else {
      prevInKeyInsertionOrder[nextInKeyInsertionOrder[entry]] = prevInKeyInsertionOrder[entry];
    }

    size--;
    modCount++;
    retireEntry(entry, DEAD);
  }

  private void insert(int entry, int oldEntryForKey) {
    if (states[entry] == RESERVED) {
      publishEntry(entry);
    }
    int keyBucket = keyHashes[entry] & mask;
    nextInKToVBucket[entry] = hashTableKToV[keyBucket];
    hashTableKToV[keyBucket] = entry;

    int valueBucket = valueHashes[entry] & mask;
    nextInVToKBucket[entry] = hashTableVToK[valueBucket];
    hashTableVToK[valueBucket] = entry;

    if (oldEntryForKey == ABSENT) {
      prevInKeyInsertionOrder[entry] = lastInKeyInsertionOrder;
      nextInKeyInsertionOrder[entry] = ABSENT;
      if (lastInKeyInsertionOrder == ABSENT) {
        firstInKeyInsertionOrder = entry;
      } else {
        nextInKeyInsertionOrder[lastInKeyInsertionOrder] = entry;
      }
      lastInKeyInsertionOrder = entry;
    } else {
      prevInKeyInsertionOrder[entry] = prevInKeyInsertionOrder[oldEntryForKey];
      if (prevInKeyInsertionOrder[entry] == ABSENT) {
        firstInKeyInsertionOrder = entry;
      } else {
        nextInKeyInsertionOrder[prevInKeyInsertionOrder[entry]] = entry;
      }
      nextInKeyInsertionOrder[entry] = nextInKeyInsertionOrder[oldEntryForKey];
      if (nextInKeyInsertionOrder[entry] == ABSENT) {
        lastInKeyInsertionOrder = entry;
      } else {
        prevInKeyInsertionOrder[nextInKeyInsertionOrder[entry]] = entry;
      }
    }

    size++;
    modCount++;
  }

  private int seekByKey(@Nullable Object key, int keyHash) {
    int roots = openPins(1);
    try {
      for (int entry = pin(roots, hashTableKToV[keyHash & mask]);
          entry != ABSENT;
          entry = pin(roots, nextInKToVBucket[entry])) {
        if (keyHash == keyHashes[entry] && Objects.equal(key, keys[entry])) {
          return entry;
        }
      }
      return ABSENT;
    
    } finally {
      closePins(roots);
    }
  }

  private int seekByValue(@Nullable Object value, int valueHash) {
    int roots = openPins(1);
    try {
      for (int entry = pin(roots, hashTableVToK[valueHash & mask]);
          entry != ABSENT;
          entry = pin(roots, nextInVToKBucket[entry])) {
        if (valueHash == valueHashes[entry] && Objects.equal(value, values[entry])) {
          return entry;
        }
      }
      return ABSENT;
    
    } finally {
      closePins(roots);
    }
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return seekByKey(key, smearedHash(key)) != ABSENT;
  }

  /**
   * Returns {@code true} if this BiMap contains an entry whose value is equal to {@code value} (or,
   * equivalently, if this inverse view contains a key that is equal to {@code value}).
   *
   * <p>Due to the property that values in a BiMap are unique, this will tend to execute in
   * faster-than-linear time.
   *
   * @param value the object to search for in the values of this BiMap
   * @return true if a mapping exists from a key to the specified value
   */
  @Override
  public boolean containsValue(@Nullable Object value) {
    return seekByValue(value, smearedHash(value)) != ABSENT;
  }

  @Override
  public @Nullable V get(@Nullable Object key) {
    int roots = openPins(1);
    try {
      int entry = pin(roots, seekByKey(key, smearedHash(key)));
      return valueOrNull(entry);
    } finally {
      closePins(roots);
    }
  }

  @CanIgnoreReturnValue
  @Override
  public @Nullable V put(@ParametricNullness K key, @ParametricNullness V value) {
    return put(key, value, false);
  }

  private @Nullable V put(@ParametricNullness K key, @ParametricNullness V value, boolean force) {
    int roots = openPins(3);
    try {
      int keyHash = smearedHash(key);
      int valueHash = smearedHash(value);

      int oldEntryForKey = pin(roots + 0, seekByKey(key, keyHash));
      if (oldEntryForKey != ABSENT
          && valueHash == valueHashes[oldEntryForKey]
          && Objects.equal(value, values[oldEntryForKey])) {
        return value;
      }

      int oldEntryForValue = pin(roots + 1, seekByValue(value, valueHash));
      if (oldEntryForValue != ABSENT) {
        if (force) {
          delete(oldEntryForValue);
        } else {
          throw new IllegalArgumentException("value already present: " + value);
        }
      }

      int newEntry = pin(roots + 2, allocateEntry(key, keyHash, value, valueHash));
      if (oldEntryForKey != ABSENT) {
        delete(oldEntryForKey);
        insert(newEntry, oldEntryForKey);
        prevInKeyInsertionOrder[oldEntryForKey] = ABSENT;
        nextInKeyInsertionOrder[oldEntryForKey] = ABSENT;
        return values[oldEntryForKey];
      } else {
        insert(newEntry, ABSENT);
        rehashIfNecessary();
        return null;
      }
    
    } finally {
      closePins(roots);
    }
  }

  @CanIgnoreReturnValue
  @Override
  public @Nullable V forcePut(@ParametricNullness K key, @ParametricNullness V value) {
    return put(key, value, true);
  }

  @CanIgnoreReturnValue
  private @Nullable K putInverse(
      @ParametricNullness V value, @ParametricNullness K key, boolean force) {
    int roots = openPins(3);
    try {
      int valueHash = smearedHash(value);
      int keyHash = smearedHash(key);

      int oldEntryForValue = pin(roots + 1, seekByValue(value, valueHash));
      int oldEntryForKey = pin(roots + 0, seekByKey(key, keyHash));
      if (oldEntryForValue != ABSENT
          && keyHash == keyHashes[oldEntryForValue]
          && Objects.equal(key, keys[oldEntryForValue])) {
        return key;
      } else if (oldEntryForKey != ABSENT && !force) {
        throw new IllegalArgumentException("key already present: " + key);
      }

      /*
       * The ordering here is important: if we deleted the key entry and then the value entry,
       * the key entry's prev or next pointer might point to the dead value entry, and when we
       * put the new entry in the key entry's position in iteration order, it might invalidate
       * the linked list.
       */

      if (oldEntryForValue != ABSENT) {
        delete(oldEntryForValue);
      }

      if (oldEntryForKey != ABSENT) {
        delete(oldEntryForKey);
      }

      int newEntry = pin(roots + 2, allocateEntry(key, keyHash, value, valueHash));
      insert(newEntry, oldEntryForKey);

      if (oldEntryForKey != ABSENT) {
        prevInKeyInsertionOrder[oldEntryForKey] = ABSENT;
        nextInKeyInsertionOrder[oldEntryForKey] = ABSENT;
      }
      if (oldEntryForValue != ABSENT) {
        prevInKeyInsertionOrder[oldEntryForValue] = ABSENT;
        nextInKeyInsertionOrder[oldEntryForValue] = ABSENT;
      }
      rehashIfNecessary();
      return keyOrNull(oldEntryForValue);
    
    } finally {
      closePins(roots);
    }
  }

  private void rehashIfNecessary() {
    int[] oldKToV = hashTableKToV;
    if (Hashing.needsResizing(size, oldKToV.length, LOAD_FACTOR)) {
      int newTableSize = oldKToV.length * 2;

      this.hashTableKToV = createTable(newTableSize);
      this.hashTableVToK = createTable(newTableSize);
      this.mask = newTableSize - 1;
      this.size = 0;

      for (int entry = firstInKeyInsertionOrder;
          entry != ABSENT;
          entry = nextInKeyInsertionOrder[entry]) {
        insert(entry, entry);
      }
      this.modCount++;
    }
  }

  private int[] createTable(int length) {
    int[] table = new int[length];
    Arrays.fill(table, ABSENT);
    return table;
  }

  @CanIgnoreReturnValue
  @Override
  public @Nullable V remove(@Nullable Object key) {
    int roots = openPins(1);
    try {
      int entry = pin(roots + 0, seekByKey(key, smearedHash(key)));
      if (entry == ABSENT) {
        return null;
      } else {
        delete(entry);
        prevInKeyInsertionOrder[entry] = ABSENT;
        nextInKeyInsertionOrder[entry] = ABSENT;
        return values[entry];
      }
    
    } finally {
      closePins(roots);
    }
  }

  @Override
  public void clear() {
    int roots = openPins(0);
    try {
      size = 0;
      if (apiEntries != null) apiEntries.clear();
      Arrays.fill(hashTableKToV, ABSENT);
      Arrays.fill(hashTableVToK, ABSENT);
      firstInKeyInsertionOrder = ABSENT;
      lastInKeyInsertionOrder = ABSENT;
      for (int entry = 0; entry < highWater; entry++) {
        if (states[entry] == LIVE) {
          retireEntry(entry, DEAD);
        }
      }
      modCount++;
    } finally {
      closePins(roots);
    }
  }

  @Override
  public int size() {
    return size;
  }

  private abstract class Itr<T extends @Nullable Object> implements Iterator<T> {
    int next = firstInKeyInsertionOrder;
    int toRemove = ABSENT;
    long toRemoveGeneration;
    int expectedModCount = modCount;
    int remaining = size();

    @Override
    public boolean hasNext() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
      return next != ABSENT && remaining > 0;
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      // requireNonNull is safe because of the hasNext check.
      int entry = next;
      next = nextInKeyInsertionOrder[entry];
      toRemove = entry;
      toRemoveGeneration = generations[entry];
      remaining--;
      return output(entry);
    }

    @Override
    public void remove() {
    int roots = openPins(0);
    try {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
        if (toRemove == ABSENT) {
          throw new IllegalStateException("no calls to next() since the last call to remove()");
        }
        requireLinked(toRemove, toRemoveGeneration);
        delete(toRemove);
        expectedModCount = modCount;
        toRemove = ABSENT;
      
    } finally {
      closePins(roots);
    }
  }

    abstract T output(int entry);
  }

  @Override
  public Set<K> keySet() {
    return new KeySet();
  }

  private final class KeySet extends Maps.KeySet<K, V> {
    KeySet() {
      super(HashBiMap.this);
    }

    @Override
    public Iterator<K> iterator() {
      return new Itr<K>() {
        @Override
        @ParametricNullness
        K output(int entry) {
          return keys[entry];
        }
      };
    }

    @Override
    public boolean remove(@Nullable Object o) {
    int roots = openPins(1);
    try {
        int entry = pin(roots + 0, seekByKey(o, smearedHash(o)));
        if (entry == ABSENT) {
          return false;
        } else {
          delete(entry);
          prevInKeyInsertionOrder[entry] = ABSENT;
          nextInKeyInsertionOrder[entry] = ABSENT;
          return true;
        }
      
    } finally {
      closePins(roots);
    }
  }
  }

  @Override
  public Set<V> values() {
    return inverse().keySet();
  }

  @Override
  Iterator<Entry<K, V>> entryIterator() {
    return new Itr<Entry<K, V>>() {
      private final long lease = apiLease();

      @Override
      @SuppressWarnings("unchecked")
      Entry<K, V> output(int entry) {
        if (lease != 0) {
          Object cached = apiEntries().get(entry, generations[entry], lease);
          if (cached != null) return (Entry<K, V>) cached;
        }
        return new MapEntry(entry);
      }

      class MapEntry extends AbstractMapEntry<K, V> {
        private int delegate;
        private long delegateGeneration;
        private @Nullable K keySnapshot;
        private @Nullable V valueSnapshot;
        private int keyHashSnapshot;
        private int valueHashSnapshot;

        MapEntry(int entry) {
          bind(entry);
        }

        private void bind(int entry) {
          delegate = entry;
          delegateGeneration = generations[entry];
          keySnapshot = keys[entry];
          valueSnapshot = values[entry];
          keyHashSnapshot = keyHashes[entry];
          valueHashSnapshot = valueHashes[entry];
          if (lease != 0) apiEntries().put(entry, delegateGeneration, lease, this);
        }

        @Override
        @ParametricNullness
        public K getKey() {
          return keySnapshot;
        }

        @Override
        @ParametricNullness
        public V getValue() {
          return valueSnapshot;
        }

        @Override
        @ParametricNullness
        public V setValue(@ParametricNullness V value) {
          int roots = openPins(3);
          try {
            int oldDelegate = pin(roots,
                isLinked(delegate, delegateGeneration) ? delegate : ABSENT);
            V oldValue = valueSnapshot;
            int valueHash = smearedHash(value);
            if (valueHash == valueHashSnapshot && Objects.equal(value, oldValue)) {
              return value;
            }
            checkArgument(seekByValue(value, valueHash) == ABSENT,
                "value already present: %s", value);
            requireLinked(delegate, delegateGeneration);
            oldDelegate = pin(roots, delegate);
            delete(oldDelegate);
            int newEntry = pin(roots + 1,
                allocateEntry(keySnapshot, keyHashSnapshot, value, valueHash));
            insert(newEntry, oldDelegate);
            prevInKeyInsertionOrder[oldDelegate] = ABSENT;
            nextInKeyInsertionOrder[oldDelegate] = ABSENT;
            expectedModCount = modCount;
            if (toRemove == oldDelegate && toRemoveGeneration == delegateGeneration) {
              toRemove = newEntry;
              toRemoveGeneration = generations[newEntry];
            }
            bind(newEntry);
            return oldValue;
          } finally {
            closePins(roots);
          }
        }
      }
    };
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    int roots = openPins(1);
    try {
      checkNotNull(action);
      for (int entry = pin(roots, firstInKeyInsertionOrder);
          entry != ABSENT;
          entry = pin(roots, nextInKeyInsertionOrder[entry])) {
        action.accept(keys[entry], values[entry]);
      }
    
    } finally {
      closePins(roots);
    }
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    int roots = openPins(2);
    try {
      checkNotNull(function);
      int oldFirst = pin(roots + 0, firstInKeyInsertionOrder);
      clear();
      for (int entry = pin(roots + 1, oldFirst); entry != ABSENT; entry = pin(roots + 1, nextInKeyInsertionOrder[entry])) {
        put(keys[entry], function.apply(keys[entry], values[entry]));
      }
    
    } finally {
      closePins(roots);
    }
  }


  private @Nullable V valueOrNull(int entry) {
    return entry == ABSENT ? null : values[entry];
  }

  private @Nullable K keyOrNull(int entry) {
    return entry == ABSENT ? null : keys[entry];
  }

  private boolean isLinked(int entry, long generation) {
    return entry >= 0 && entry < highWater
        && states[entry] == LIVE && generations[entry] == generation;
  }

  private void requireLinked(int entry, long generation) {
    if (!isLinked(entry, generation)) {
      // Preserve the pinned JRE stale-entry failure class and pre-mutation effect.
      // Enhanced NPE text/stack details after Map.Entry invalidation are not promised.
      throw new NullPointerException();
    }
  }

  private int allocateEntry(
      @ParametricNullness K key, int keyHash, @ParametricNullness V value, int valueHash) {
    if (freeHead == ABSENT && highWater == keys.length) {
      reclaimUnreachable();
      if (freeHead == ABSENT) {
        if (highWater == Integer.MAX_VALUE) {
          throw new OutOfMemoryError("HashBiMap slot capacity exhausted");
        }
        ensureSlotCapacity(highWater + 1);
      }
    }
    final int entry;
    if (freeHead != ABSENT) {
      entry = freeHead;
      freeHead = freeNext[entry];
      freeNext[entry] = ABSENT;
    } else {
      entry = highWater++;
      generations[entry] = 1;
    }
    keys[entry] = key;
    values[entry] = value;
    keyHashes[entry] = keyHash;
    valueHashes[entry] = valueHash;
    nextInKToVBucket[entry] = ABSENT;
    nextInVToKBucket[entry] = ABSENT;
    nextInKeyInsertionOrder[entry] = ABSENT;
    prevInKeyInsertionOrder[entry] = ABSENT;
    retireEntry(entry, RESERVED);
    return entry;
  }

  private void ensureSlotCapacity(int minCapacity) {
    int capacity = ImmutableCollection.Builder.expandedCapacity(keys.length, minCapacity);
    keys = Arrays.copyOf(keys, capacity);
    values = Arrays.copyOf(values, capacity);
    keyHashes = Arrays.copyOf(keyHashes, capacity);
    valueHashes = Arrays.copyOf(valueHashes, capacity);
    nextInKToVBucket = expandAndFillWithAbsent(nextInKToVBucket, capacity);
    nextInVToKBucket = expandAndFillWithAbsent(nextInVToKBucket, capacity);
    nextInKeyInsertionOrder = expandAndFillWithAbsent(nextInKeyInsertionOrder, capacity);
    prevInKeyInsertionOrder = expandAndFillWithAbsent(prevInKeyInsertionOrder, capacity);
    states = Arrays.copyOf(states, capacity);
    generations = Arrays.copyOf(generations, capacity);
    freeNext = expandAndFillWithAbsent(freeNext, capacity);
  }

  private static int[] expandAndFillWithAbsent(int[] array, int newSize) {
    int oldSize = array.length;
    int[] result = Arrays.copyOf(array, newSize);
    Arrays.fill(result, oldSize, newSize, ABSENT);
    return result;
  }

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
// REVIEW FRAGMENT ONLY: no public class, production source, JVM qualification or admission.
// Field integration: existing parallel payload/hash/four original link lanes; freeNext[];
// long[] generations; byte[] states; int[] pins; int pinCount, retiredCount, highWater;
// int freeHead = -1. Fresh generation=1. Allocate FREE->RESERVED, increment retiredCount,
// pin immediately before any callback; insert RESERVED->LIVE decrements retiredCount;
// delete LIVE->DEAD increments retiredCount. Preserve each original link write exactly.
private static final byte SLOT_FREE = 0, SLOT_LIVE = 1, SLOT_DEAD = 2,
        SLOT_RESERVED = 3, SLOT_PARKED = 4, SLOT_MARK = 8;
private static final int SLOT_STATE_MASK = 7;

private int openPins(int count) {
    int base = pinCount;
    int needed = Math.addExact(base, count);
    if (needed > pins.length) {
        int grown = Math.max(needed, pins.length <= Integer.MAX_VALUE / 2
                ? Math.max(8, pins.length * 2) : needed);
        pins = java.util.Arrays.copyOf(pins, grown);
    }
    java.util.Arrays.fill(pins, base, needed, -1);
    pinCount = needed;
    return base;
}

// rootIndex is an exact source-local home in the current/nested frame, not visited history.
// Clearing/updating this home is required when the original Java local is overwritten.
private int pin(int rootIndex, int slot) {
    pins[rootIndex] = slot;
    return slot;
}


// Intrusive gray-stack link uses freeNext only while the slot is occupied. No FREE slot's
// existing free-list link is changed by tracing. No key/value callbacks or allocation here.
private int markSlot(int slot, int grayHead) {
    if (slot == -1) return grayHead;
    int state = states[slot] & SLOT_STATE_MASK;
    assert state == SLOT_LIVE || state == SLOT_DEAD || state == SLOT_RESERVED;
    if ((states[slot] & SLOT_MARK) != 0) return grayHead;
    states[slot] |= SLOT_MARK;                 // mark on enqueue: at most one gray link/slot
    freeNext[slot] = grayHead;
    return slot;
}


// Call-site law (pseudocode, not a second owner):
// int frame = openPins(FIXED_NUMBER_OF_SIMULTANEOUS_SOURCE_LOCALS);
// try {
//   int candidate = pin(frame, bucketHead);
//   while (candidate != -1) {
//     // Keep candidate pinned across key.equals/value.equals, including reentrant removal.
//     // Preserve original comparison direction/evaluation and every donor branch.
//     if (matches(candidate)) { Object result = values[candidate]; return result; }
//     candidate = pin(frame, nextInKToVBucket[candidate]);
//   }
// } finally { closePins(frame); }
//
// Allocation pressure law: when freeHead==-1 and highWater==laneCapacity, call
// reclaimUnreachable() before growing. Reuse a FREE slot if one appears. Do not quarantine
// every retired slot until the outer operation exits. New RESERVED slots are construction
// locals and must be pinned immediately. Failed/unpublished reservations become reclaimable
// only after that construction local is gone. Do not root historical locals monotonically.

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
// PRIVATE REVIEW FRAGMENT ONLY. Add retiredHead=ABSENT in init; preserve v1 pins/markSlot.
// freeNext has three disjoint roles: FREE chain; DEAD/RESERVED chain outside collection;
// occupied gray chain during callback-free tracing. No production/JVM admission here.

private void retireEntry(int entry, byte state) {
    assert state == DEAD || state == RESERVED;
    assert state == RESERVED ? states[entry] == FREE : states[entry] == LIVE;
    if (state == DEAD && apiEntries != null) apiEntries.removeSlot(entry);
    states[entry] = state;
    freeNext[entry] = retiredHead;
    retiredHead = entry;
    retiredCount++;
}

private void publishEntry(int entry) {
    assert states[entry] == RESERVED;
    if (retiredHead == entry) {
        retiredHead = freeNext[entry];
    } else {
        // Exactly four allocation sites: forward put may delete its old key after allocate,
        // inserting one predecessor. putInverse and both projection setters delete before
        // allocate. No callback occurs between allocate/pin and insert. Pressure rebuilding
        // precedes pushing this new reservation. Thus no unbounded predecessor search.
        int predecessor = retiredHead;
        assert predecessor != ABSENT && freeNext[predecessor] == entry;
        freeNext[predecessor] = freeNext[entry];
    }
    freeNext[entry] = ABSENT;
    retiredCount--;
    states[entry] = LIVE;
}

private void recycleRetiredEntry(int entry) {
    assert states[entry] == DEAD || states[entry] == RESERVED;
    keys[entry] = null;
    values[entry] = null;
    keyHashes[entry] = 0;
    valueHashes[entry] = 0;
    prevInKeyInsertionOrder[entry] = ABSENT;
    nextInKeyInsertionOrder[entry] = ABSENT;
    nextInKToVBucket[entry] = ABSENT;
    nextInVToKBucket[entry] = ABSENT;
    retiredCount--;
    if (generations[entry] == Long.MAX_VALUE) {
        states[entry] = SLOT_PARKED;
        freeNext[entry] = ABSENT;
    } else {
        generations[entry]++;
        states[entry] = FREE;
        freeNext[entry] = freeHead;
        freeHead = entry;
    }
}

private void drainRetired() {
    // PRECONDITION: no pins and a completed original JRE helper boundary. Original insert,
    // delete, clear and rehash writes leave every LIVE slot's four links LIVE or ABSENT.
    // Check that law in qualification/model code, not by an O(highWater) production scan.
    assert pinCount == 0;
    while (retiredHead != ABSENT) {
        int entry = retiredHead;
        retiredHead = freeNext[entry]; // capture old retired link before FREE-link overwrite
        recycleRetiredEntry(entry);
    }
    assert retiredCount == 0;
}

private void closePins(int base) {
    java.util.Arrays.fill(pins, base, pinCount, ABSENT);
    pinCount = base;
    if (pinCount == 0) drainRetired();
}

private void reclaimUnreachable() {
    if (retiredCount == 0) return;
    int grayHead = ABSENT;
    for (int slot = 0; slot < highWater; slot++) {
        if ((states[slot] & SLOT_STATE_MASK) == LIVE) grayHead = markSlot(slot, grayHead);
    }
    for (int i = 0; i < pinCount; i++) grayHead = markSlot(pins[i], grayHead);
    while (grayHead != ABSENT) {
        int entry = grayHead;
        grayHead = freeNext[entry];
        freeNext[entry] = ABSENT;
        grayHead = markSlot(prevInKeyInsertionOrder[entry], grayHead);
        grayHead = markSlot(nextInKeyInsertionOrder[entry], grayHead);
        grayHead = markSlot(nextInKToVBucket[entry], grayHead);
        grayHead = markSlot(nextInVToKBucket[entry], grayHead);
    }
    int rebuiltRetiredHead = ABSENT;
    for (int entry = 0; entry < highWater; entry++) {
        int state = states[entry] & SLOT_STATE_MASK;
        boolean reached = (states[entry] & SLOT_MARK) != 0;
        states[entry] = (byte) state;
        if (state != DEAD && state != RESERVED) continue;
        if (!reached) {
            recycleRetiredEntry(entry);
        } else {
            freeNext[entry] = rebuiltRetiredHead;
            rebuiltRetiredHead = entry; // same already-counted retired record, no increment
        }
    }
    retiredHead = rebuiltRetiredHead;
}

// Exact integration replacements (do not also keep old retiredCount increments):
// allocateEntry: pop FREE/fresh slot, initialize fields/links; retireEntry(entry, RESERVED).
// A fresh highWater slot's state is initially FREE; keep generation=1 initialization.
// delete: after unchanged original unlink/size/epoch writes, retireEntry(entry, DEAD).
// clear: for each LIVE slot retireEntry(entry, DEAD), after original roots/tables are dropped.
// insert: if RESERVED publishEntry(entry), then the unchanged original pointer writes.
// No callbacks or reclamation may be introduced inside insert/delete or between these steps.

  // Adapted from M3LFUCache, M3jdk21@6ae20943e2295e6120c9666a04dc1f2be29f4873.
  // Apache-2.0; original SPDX copyright retained in the donor/provenance resource.
  // Cache values are API projections only. No authoritative mapping or link is stored here.
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

  @LazyInit @RetainedWith private transient @Nullable BiMap<V, K> inverse;

  @Override
  public BiMap<V, K> inverse() {
    BiMap<V, K> result = inverse;
    return (result == null) ? inverse = new Inverse() : result;
  }

  private final class Inverse extends IteratorBasedAbstractMap<V, K>
      implements BiMap<V, K>, Serializable {
    private static final long serialVersionUID = -2633234740206700248L;

    BiMap<K, V> forward() {
      return HashBiMap.this;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public void clear() {
      forward().clear();
    }

    @Override
    public boolean containsKey(@Nullable Object value) {
      return forward().containsValue(value);
    }

    @Override
    public @Nullable K get(@Nullable Object value) {
      int roots = openPins(1);
      try {
        int entry = pin(roots, seekByValue(value, smearedHash(value)));
        return keyOrNull(entry);
      } finally {
        closePins(roots);
      }
    }

    @CanIgnoreReturnValue
    @Override
    public @Nullable K put(@ParametricNullness V value, @ParametricNullness K key) {
      return putInverse(value, key, false);
    }

    @Override
    public @Nullable K forcePut(@ParametricNullness V value, @ParametricNullness K key) {
      return putInverse(value, key, true);
    }

    @Override
    public @Nullable K remove(@Nullable Object value) {
    int roots = openPins(1);
    try {
        int entry = pin(roots + 0, seekByValue(value, smearedHash(value)));
        if (entry == ABSENT) {
          return null;
        } else {
          delete(entry);
          prevInKeyInsertionOrder[entry] = ABSENT;
          nextInKeyInsertionOrder[entry] = ABSENT;
          return keys[entry];
        }
      
    } finally {
      closePins(roots);
    }
  }

    @Override
    public BiMap<K, V> inverse() {
      return forward();
    }

    @Override
    public Set<V> keySet() {
      return new InverseKeySet();
    }

    private final class InverseKeySet extends Maps.KeySet<V, K> {
      InverseKeySet() {
        super(Inverse.this);
      }

      @Override
      public boolean remove(@Nullable Object o) {
    int roots = openPins(1);
    try {
          int entry = pin(roots + 0, seekByValue(o, smearedHash(o)));
          if (entry == ABSENT) {
            return false;
          } else {
            delete(entry);
            return true;
          }
        
    } finally {
      closePins(roots);
    }
  }

      @Override
      public Iterator<V> iterator() {
        return new Itr<V>() {
          @Override
          @ParametricNullness
          V output(int entry) {
            return values[entry];
          }
        };
      }
    }

    @Override
    public Set<K> values() {
      return forward().keySet();
    }

    @Override
    Iterator<Entry<V, K>> entryIterator() {
      return new Itr<Entry<V, K>>() {
        private final long lease = apiLease();

        @Override
        @SuppressWarnings("unchecked")
        Entry<V, K> output(int entry) {
          if (lease != 0) {
            Object cached = apiEntries().get(entry, generations[entry], lease);
            if (cached != null) return (Entry<V, K>) cached;
          }
          return new InverseEntry(entry);
        }

        class InverseEntry extends AbstractMapEntry<V, K> {
          private int delegate;
          private long delegateGeneration;
          private @Nullable K keySnapshot;
          private @Nullable V valueSnapshot;
          private int keyHashSnapshot;
          private int valueHashSnapshot;

          InverseEntry(int entry) {
            bind(entry);
          }

          private void bind(int entry) {
            delegate = entry;
            delegateGeneration = generations[entry];
            keySnapshot = keys[entry];
            valueSnapshot = values[entry];
            keyHashSnapshot = keyHashes[entry];
            valueHashSnapshot = valueHashes[entry];
            if (lease != 0) apiEntries().put(entry, delegateGeneration, lease, this);
          }

          @Override
          @ParametricNullness
          public V getKey() {
            return valueSnapshot;
          }

          @Override
          @ParametricNullness
          public K getValue() {
            return keySnapshot;
          }

          @Override
          @ParametricNullness
          public K setValue(@ParametricNullness K key) {
            int roots = openPins(3);
            try {
              int oldDelegate = pin(roots,
                  isLinked(delegate, delegateGeneration) ? delegate : ABSENT);
              K oldKey = keySnapshot;
              int keyHash = smearedHash(key);
              if (keyHash == keyHashSnapshot && Objects.equal(key, oldKey)) {
                return key;
              }
              checkArgument(seekByKey(key, keyHash) == ABSENT,
                  "value already present: %s", key);
              requireLinked(delegate, delegateGeneration);
              oldDelegate = pin(roots, delegate);
              delete(oldDelegate);
              int newEntry = pin(roots + 1,
                  allocateEntry(key, keyHash, valueSnapshot, valueHashSnapshot));
              bind(newEntry);
              insert(newEntry, ABSENT);
              expectedModCount = modCount;
              // JRE intentionally leaves toRemove on the detached old entry.
              return oldKey;
            } finally {
              closePins(roots);
            }
          }
        }
      };
    }

    @Override
    public void forEach(BiConsumer<? super V, ? super K> action) {
      checkNotNull(action);
      HashBiMap.this.forEach((k, v) -> action.accept(v, k));
    }

    @Override
    public void replaceAll(BiFunction<? super V, ? super K, ? extends K> function) {
    int roots = openPins(2);
    try {
        checkNotNull(function);
        int oldFirst = pin(roots + 0, firstInKeyInsertionOrder);
        clear();
        for (int entry = pin(roots + 1, oldFirst); entry != ABSENT; entry = pin(roots + 1, nextInKeyInsertionOrder[entry])) {
          put(values[entry], function.apply(values[entry], keys[entry]));
        }
      
    } finally {
      closePins(roots);
    }
  }

    Object writeReplace() {
      return new InverseSerializedForm<>(HashBiMap.this);
    }

    @GwtIncompatible // serialization
    @J2ktIncompatible
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
      throw new InvalidObjectException("Use InverseSerializedForm");
    }
  }

  private static final class InverseSerializedForm<
          K extends @Nullable Object, V extends @Nullable Object>
      implements Serializable {
    private static final long serialVersionUID = 2881074110665323944L;

    private final HashBiMap<K, V> bimap;

    InverseSerializedForm(HashBiMap<K, V> bimap) {
      this.bimap = bimap;
    }

    Object readResolve() {
      return bimap.inverse();
    }
  }

  /**
   * @serialData the number of entries, first key, first value, second key, second value, and so on.
   */
  @GwtIncompatible // java.io.ObjectOutputStream
  @J2ktIncompatible
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMap(this, stream);
  }

  @GwtIncompatible // java.io.ObjectInputStream
  @J2ktIncompatible
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int size = Serialization.readCount(stream);
    init(16); // resist hostile attempts to allocate gratuitous heap
    Serialization.populateMap(this, stream, size);
  }

  @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 0;
}

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

// M3 immutable bimap index adaptation; provenance: m3/immutable-index-arrays-20261019/CONTRACT.md
// Modified 2026 by Hitesh Soliwal and Synexia contributors: array storage.
package com.m3.collections.guava.collect;

import static com.m3.collections.guava.base.Preconditions.checkNotNull;
import static com.m3.collections.guava.base.Preconditions.checkPositionIndex;
import static com.m3.collections.guava.collect.CollectPreconditions.checkEntryNotNull;
import static com.m3.collections.guava.collect.ImmutableMapEntry.createEntryArray;
import static com.m3.collections.guava.collect.Maps.immutableEntry;
import static com.m3.collections.guava.collect.RegularImmutableMap.ABSENT;
import static com.m3.collections.guava.collect.RegularImmutableMap.MAX_HASH_BUCKET_LENGTH;
import static com.m3.collections.guava.collect.RegularImmutableMap.checkNoConflictInKeyBucket;
import static com.m3.collections.guava.collect.RegularImmutableMap.createIndexArray;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.j2objc.annotations.RetainedWith;
import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.annotations.VisibleForTesting;
import com.m3.collections.guava.collect.RegularImmutableMap.BucketOverflowException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Bimap with zero or more mappings.
 *
 * @author Louis Wasserman
 */
@GwtCompatible(serializable = true, emulated = true)
@SuppressWarnings("serial") // uses writeReplace(), not default serialization
class RegularImmutableBiMap<K, V> extends ImmutableBiMap<K, V> {
  @SuppressWarnings("unchecked") // TODO(cpovirk): Consider storing Entry<?, ?>[] instead.
  static final RegularImmutableBiMap<Object, Object> EMPTY =
      new RegularImmutableBiMap<>(
          null,
          null,
          (Entry<Object, Object>[]) ImmutableMap.EMPTY_ENTRY_ARRAY,
          new int[0],
          new int[0],
          0,
          0);

  static final double MAX_LOAD_FACTOR = 1.2;

  private final transient int @Nullable [] keyTable;
  private final transient int @Nullable [] valueTable;
  private final transient int[] nextInKeyBucket;
  private final transient int[] nextInValueBucket;
  @VisibleForTesting final transient Entry<K, V>[] entries;
  private final transient int mask;
  private final transient int hashCode;

  static <K, V> ImmutableBiMap<K, V> fromEntries(Entry<K, V>... entries) {
    return fromEntryArray(entries.length, entries);
  }

  static <K, V> ImmutableBiMap<K, V> fromEntryArray(int n, @Nullable Entry<K, V>[] entryArray) {
    checkPositionIndex(n, entryArray.length);
    int tableSize = Hashing.closedTableSize(n, MAX_LOAD_FACTOR);
    int mask = tableSize - 1;
    int[] keyTable = createIndexArray(tableSize);
    int[] valueTable = createIndexArray(tableSize);
    int[] nextInKeyBucket = createIndexArray(n);
    int[] nextInValueBucket = createIndexArray(n);
    /*
     * The cast is safe: n==entryArray.length means that we have filled the whole array with Entry
     * instances, in which case it is safe to cast it from an array of nullable entries to an array
     * of non-null entries.
     */
    @SuppressWarnings("nullness")
    Entry<K, V>[] entries =
        (n == entryArray.length) ? (Entry<K, V>[]) entryArray : createEntryArray(n);
    int hashCode = 0;

    for (int i = 0; i < n; i++) {
      // requireNonNull is safe because the first `n` elements have been filled in.
      Entry<K, V> entry = requireNonNull(entryArray[i]);
      K key = entry.getKey();
      V value = entry.getValue();
      checkEntryNotNull(key, value);
      int keyHash = key.hashCode();
      int valueHash = value.hashCode();
      int keyBucket = Hashing.smear(keyHash) & mask;
      int valueBucket = Hashing.smear(valueHash) & mask;

      int keyBucketHead = keyTable[keyBucket];
      int valueBucketHead = valueTable[valueBucket];
      try {
        checkNoConflictInKeyBucket(
            key, value, keyBucketHead, entries, nextInKeyBucket, /* throwIfDuplicateKeys= */ true);
        checkNoConflictInValueBucket(value, entry, valueBucketHead, entries, nextInValueBucket);
      } catch (BucketOverflowException e) {
        return JdkBackedImmutableBiMap.create(n, entryArray);
      }
      entries[i] = RegularImmutableMap.makeImmutable(entry, key, value);
      nextInKeyBucket[i] = keyBucketHead;
      nextInValueBucket[i] = valueBucketHead;
      keyTable[keyBucket] = i;
      valueTable[valueBucket] = i;
      hashCode += keyHash ^ valueHash;
    }
    return new RegularImmutableBiMap<>(
        keyTable, valueTable, entries, nextInKeyBucket, nextInValueBucket, mask, hashCode);
  }

  private RegularImmutableBiMap(
      int @Nullable [] keyTable,
      int @Nullable [] valueTable,
      Entry<K, V>[] entries,
      int[] nextInKeyBucket,
      int[] nextInValueBucket,
      int mask,
      int hashCode) {
    this.keyTable = keyTable;
    this.valueTable = valueTable;
    this.entries = entries;
    this.nextInKeyBucket = nextInKeyBucket;
    this.nextInValueBucket = nextInValueBucket;
    this.mask = mask;
    this.hashCode = hashCode;
  }

  // checkNoConflictInKeyBucket is static imported from RegularImmutableMap

  /**
   * @throws IllegalArgumentException if another entry in the bucket has the same key
   * @throws BucketOverflowException if this bucket has too many entries, which may indicate a hash
   *     flooding attack
   */
  private static void checkNoConflictInValueBucket(
      Object value,
      Entry<?, ?> entry,
      int valueBucketHead,
      Entry<?, ?>[] entries,
      int[] nextInValueBucket)
      throws BucketOverflowException {
    int bucketSize = 0;
    for (int index = valueBucketHead; index != ABSENT; index = nextInValueBucket[index]) {
      Entry<?, ?> candidate = entries[index];
      checkNoConflict(!value.equals(candidate.getValue()), "value", entry, candidate);
      if (++bucketSize > MAX_HASH_BUCKET_LENGTH) {
        throw new BucketOverflowException();
      }
    }
  }

  @Override
  public @Nullable V get(@Nullable Object key) {
    return RegularImmutableMap.get(key, keyTable, entries, nextInKeyBucket, mask);
  }

  @Override
  ImmutableSet<Entry<K, V>> createEntrySet() {
    return isEmpty()
        ? ImmutableSet.<Entry<K, V>>of()
        : new ImmutableMapEntrySet.RegularEntrySet<K, V>(this, entries);
  }

  @Override
  ImmutableSet<K> createKeySet() {
    return new ImmutableMapKeySet<>(this);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    checkNotNull(action);
    for (Entry<K, V> entry : entries) {
      action.accept(entry.getKey(), entry.getValue());
    }
  }

  @Override
  boolean isHashCodeFast() {
    return true;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  boolean isPartialView() {
    return false;
  }

  @Override
  public int size() {
    return entries.length;
  }

  @LazyInit @RetainedWith private transient @Nullable ImmutableBiMap<V, K> inverse;

  @Override
  public ImmutableBiMap<V, K> inverse() {
    if (isEmpty()) {
      return ImmutableBiMap.of();
    }
    ImmutableBiMap<V, K> result = inverse;
    return (result == null) ? inverse = new Inverse() : result;
  }

  private final class Inverse extends ImmutableBiMap<V, K> {

    @Override
    public int size() {
      return inverse().size();
    }

    @Override
    public ImmutableBiMap<K, V> inverse() {
      return RegularImmutableBiMap.this;
    }

    @Override
    public void forEach(BiConsumer<? super V, ? super K> action) {
      checkNotNull(action);
      RegularImmutableBiMap.this.forEach((k, v) -> action.accept(v, k));
    }

    @Override
    public @Nullable K get(@Nullable Object value) {
      if (value == null || valueTable == null) {
        return null;
      }
      int bucket = Hashing.smear(value.hashCode()) & mask;
      for (int index = valueTable[bucket]; index != ABSENT; index = nextInValueBucket[index]) {
        Entry<K, V> entry = entries[index];
        if (value.equals(entry.getValue())) {
          return entry.getKey();
        }
      }
      return null;
    }

    @Override
    ImmutableSet<V> createKeySet() {
      return new ImmutableMapKeySet<>(this);
    }

    @Override
    ImmutableSet<Entry<V, K>> createEntrySet() {
      return new InverseEntrySet();
    }

    final class InverseEntrySet extends ImmutableMapEntrySet<V, K> {
      @Override
      ImmutableMap<V, K> map() {
        return Inverse.this;
      }

      @Override
      boolean isHashCodeFast() {
        return true;
      }

      @Override
      public int hashCode() {
        return hashCode;
      }

      @Override
      public UnmodifiableIterator<Entry<V, K>> iterator() {
        return asList().iterator();
      }

      @Override
      public void forEach(Consumer<? super Entry<V, K>> action) {
        asList().forEach(action);
      }

      @Override
      ImmutableList<Entry<V, K>> createAsList() {
        return new ImmutableAsList<Entry<V, K>>() {
          @Override
          public Entry<V, K> get(int index) {
            Entry<K, V> entry = entries[index];
            return immutableEntry(entry.getValue(), entry.getKey());
          }

          @Override
          ImmutableCollection<Entry<V, K>> delegateCollection() {
            return InverseEntrySet.this;
          }

          // redeclare to help optimizers with b/310253115
          @SuppressWarnings("RedundantOverride")
          @Override
          @J2ktIncompatible
          @GwtIncompatible
          Object writeReplace() {
            return super.writeReplace();
          }
        };
      }

      // redeclare to help optimizers with b/310253115
      @SuppressWarnings("RedundantOverride")
      @Override
      @J2ktIncompatible
      @GwtIncompatible
      Object writeReplace() {
        return super.writeReplace();
      }
    }

    @Override
    boolean isPartialView() {
      return false;
    }

    @Override
    @J2ktIncompatible
    @GwtIncompatible
    Object writeReplace() {
      return new InverseSerializedForm<>(RegularImmutableBiMap.this);
    }

    @J2ktIncompatible // java.io.ObjectInputStream
    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
      throw new InvalidObjectException("Use InverseSerializedForm");
    }
  }

  @J2ktIncompatible // serialization
  private static class InverseSerializedForm<K, V> implements Serializable {
    private final ImmutableBiMap<K, V> forward;

    InverseSerializedForm(ImmutableBiMap<K, V> forward) {
      this.forward = forward;
    }

    Object readResolve() {
      return forward.inverse();
    }

    @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 1;
  }

  // redeclare to help optimizers with b/310253115
  @SuppressWarnings("RedundantOverride")
  @Override
  @J2ktIncompatible
  @GwtIncompatible
  Object writeReplace() {
    return super.writeReplace();
  }
}

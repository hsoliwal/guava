/*
 * Copyright (C) 2026 Hitesh Soliwal and contributors to the Synexia Project
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.m3.collections.guava.annotations.GwtCompatible;
import org.jspecify.annotations.Nullable;

/**
 * Array-owned directory for the distinct keys in a linked-list multimap.
 *
 * <p>Physical key slots are stable and are referenced by an open-addressed
 * primitive index. Deletes recycle physical slots instead of moving live
 * values, so the linked-list owner's value-chain coordinates remain unchanged.
 * The directory does not retain a per-key {@code Map.Entry} or chain node.
 *
 * <p>The node-free path has expected constant-time lookup. When an insertion
 * encounters an adversarial probe run, a JDK map is used for that exceptional
 * case to retain the incumbent hash-flooding behavior.
 *
 * <p>This is an internal structural owner, not a public map or an adapter.
 */
@GwtCompatible
final class ArrayKeyDirectory<K extends @Nullable Object, V extends @Nullable Object> {
    private static final int EMPTY = 0;
    private static final int DELETED = -1;
    private static final int MAX_PROBE = 9;
    private static final Object[] NO_OBJECTS = new Object[0];
    private static final int[] NO_INTS = new int[0];

    private Object[] keys = NO_OBJECTS;
    private Object[] values = NO_OBJECTS;
    private int[] hashes = NO_INTS;
    private int[] freeNext = NO_INTS;
    private int[] index = NO_INTS;
    private @Nullable Map<K, V> fallback;
    private final int expectedSize;
    private int highWater;
    private int free = -1;
    private int size;
    private int deleted;

    ArrayKeyDirectory(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedKeys < 0");
        }
        this.expectedSize = expectedSize;
    }

    int size() {
        Map<K, V> old = fallback;
        return old == null ? size : old.size();
    }

    boolean containsKey(@Nullable Object key) {
        // Custom hashCode/equals callbacks retain JDK HashMap bucket order.
        useFallbackForUserCallbacks(key);
        Map<K, V> old = fallback;
        return old == null ? bucket(key, hash(key)) >= 0 : old.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Nullable V get(@Nullable Object key) {
        useFallbackForUserCallbacks(key);
        Map<K, V> old = fallback;
        if (old != null) {
            return old.get(key);
        }
        int position = bucket(key, hash(key));
        return position < 0 ? null : (V) values[index[position] - 1];
    }

    @SuppressWarnings("unchecked")
    @Nullable V put(K key, V value) {
        Objects.requireNonNull(value, "key-list value");
        useFallbackForUserCallbacks(key);
        Map<K, V> old = fallback;
        if (old != null) {
            return old.put(key, value);
        }
        int keyHash = hash(key);
        if (index.length == 0) {
            int wanted = Math.max(8, Math.min(expectedSize, 1 << 23));
            Object[] newKeys = new Object[wanted];
            Object[] newValues = new Object[wanted];
            int[] newHashes = new int[wanted];
            int[] newFreeNext = new int[wanted];
            int[] newIndex = new int[tableCapacity(wanted)];
            keys = newKeys;
            values = newValues;
            hashes = newHashes;
            freeNext = newFreeNext;
            index = newIndex;
        }
        int position = bucket(key, keyHash);
        if (position >= 0) {
            int slot = index[position] - 1;
            V previous = (V) values[slot];
            values[slot] = value;
            return previous;
        }
        if (index.length <= (size + deleted + 1L) * 2) {
            int capacity = size + 1L >= index.length / 2L
                    ? index.length << 1 : index.length;
            rehash(capacity);
        }
        position = insertionBucket(keyHash);
        if (position == -1) {
            switchToFallback();
            return Objects.requireNonNull(fallback).put(key, value);
        }
        int slot;
        if (free >= 0) {
            slot = free;
            free = freeNext[slot];
        } else {
            if (highWater == keys.length) {
                grow();
            }
            slot = highWater++;
        }
        if (index[position] == DELETED) {
            deleted--;
        }
        keys[slot] = key;
        values[slot] = value;
        hashes[slot] = keyHash;
        index[position] = slot + 1;
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable V remove(@Nullable Object key) {
        useFallbackForUserCallbacks(key);
        Map<K, V> old = fallback;
        if (old != null) {
            return old.remove(key);
        }
        int position = bucket(key, hash(key));
        if (position < 0) {
            return null;
        }
        int slot = index[position] - 1;
        V removed = (V) values[slot];
        index[position] = DELETED;
        deleted++;
        keys[slot] = null;
        values[slot] = null;
        freeNext[slot] = free;
        free = slot;
        size--;
        return removed;
    }

    void clear() {
        fallback = null;
        Arrays.fill(keys, 0, highWater, null);
        Arrays.fill(values, 0, highWater, null);
        Arrays.fill(index, EMPTY);
        size = 0;
        deleted = 0;
        highWater = 0;
        free = -1;
    }

    /**
     * Only intrinsically final key types enter the array index. For arbitrary
     * user key classes, HashMap can invoke observable equals/hashCode callbacks
     * while traversing and treeifying collision bins. A linear-probe directory
     * can change those callback histories, so retain JDK bucket behavior.
     */
    private static boolean hasStableKeyCallbacks(@Nullable Object key) {
        return key == null || key instanceof String || key instanceof Integer
                || key instanceof Long || key instanceof Short || key instanceof Byte
                || key instanceof Character || key instanceof Boolean
                || key instanceof Enum<?>;
    }

    private void useFallbackForUserCallbacks(@Nullable Object key) {
        if (fallback == null && !hasStableKeyCallbacks(key)) {
            switchToFallback();
        }
    }

    private static int hash(@Nullable Object key) {
        int value = key == null ? 0 : key.hashCode();
        return value ^ (value >>> 16);
    }

    private int bucket(@Nullable Object key, int keyHash) {
        if (index.length == 0) {
            return -1;
        }
        int mask = index.length - 1;
        int position = keyHash & mask;
        for (;;) {
            int entry = index[position];
            if (entry == EMPTY) {
                return -1;
            }
            if (entry > 0) {
                int slot = entry - 1;
                if (hashes[slot] == keyHash && Objects.equals(key, keys[slot])) {
                    return position;
                }
            }
            position = (position + 1) & mask;
        }
    }

    private int insertionBucket(int keyHash) {
        int mask = index.length - 1;
        int position = keyHash & mask;
        int firstDeleted = -1;
        for (int probed = 0; probed <= MAX_PROBE; probed++) {
            int entry = index[position];
            if (entry == EMPTY) {
                return firstDeleted < 0 ? position : firstDeleted;
            }
            if (entry == DELETED && firstDeleted < 0) {
                firstDeleted = position;
            }
            position = (position + 1) & mask;
        }
        // Even recycled tombstones must not mask an adversarial probe run.
        return -1;
    }

    private void grow() {
        int old = keys.length;
        long requested = (long) old + Math.max(4, old >>> 1);
        int capacity = (int) Math.min(Integer.MAX_VALUE - 8L, requested);
        if (capacity <= old) {
            throw new OutOfMemoryError("key directory exhausted");
        }
        Object[] newKeys = Arrays.copyOf(keys, capacity);
        Object[] newValues = Arrays.copyOf(values, capacity);
        int[] newHashes = Arrays.copyOf(hashes, capacity);
        int[] newFreeNext = Arrays.copyOf(freeNext, capacity);
        keys = newKeys;
        values = newValues;
        hashes = newHashes;
        freeNext = newFreeNext;
    }

    private static int tableCapacity(int wanted) {
        long desired = Math.max(16L, (long) wanted * 2);
        if (desired > (1L << 30)) {
            throw new IllegalArgumentException("too many keys");
        }
        int result = 1;
        while (result < desired) {
            result <<= 1;
        }
        return result;
    }

    private void rehash(int capacity) {
        int[] newIndex = new int[capacity];
        int mask = capacity - 1;
        for (int slot = 0; slot < highWater; slot++) {
            if (values[slot] != null) {
                int position = hashes[slot] & mask;
                while (newIndex[position] != EMPTY) {
                    position = (position + 1) & mask;
                }
                newIndex[position] = slot + 1;
            }
        }
        index = newIndex;
        deleted = 0;
    }

    @SuppressWarnings("unchecked")
    private void switchToFallback() {
        Map<K, V> newMap = new HashMap<>(Math.max(16, size * 2));
        for (int slot = 0; slot < highWater; slot++) {
            if (values[slot] != null) {
                newMap.put((K) keys[slot], (V) values[slot]);
            }
        }
        fallback = newMap;
        keys = NO_OBJECTS;
        values = NO_OBJECTS;
        hashes = NO_INTS;
        freeNext = NO_INTS;
        index = NO_INTS;
        highWater = 0;
        size = 0;
        deleted = 0;
        free = -1;
    }
}

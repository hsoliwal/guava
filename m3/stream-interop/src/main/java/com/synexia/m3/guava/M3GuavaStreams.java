// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.synexia.m3.guava;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.m3.collections.M3ObjectStream;
import java.util.Objects;
/** Opt-in typed surface; M3JDK21 remains the sole operator engine. */
public final class M3GuavaStreams {
    private M3GuavaStreams() {}
    public static <K,V> M3ObjectStream<V> snapshotValues(Multimap<K,V> map, K key) {
        return M3ObjectStream.snapshot(Objects.requireNonNull(map, "map").get(key));
    }
    public static <K,V> M3ObjectStream<V> liveValues(Multimap<K,V> map, K key) {
        return M3ObjectStream.live(Objects.requireNonNull(map, "map").get(key));
    }
    public static <T> M3ObjectStream<T> snapshotMultiset(Multiset<T> source) {
        return M3ObjectStream.snapshot(Objects.requireNonNull(source, "source"));
    }

    /** Universal Iterable adapter for List, Set, Queue, Bag and other provider occurrences. */
    public static <T> M3ObjectStream<T> snapshotAny(Iterable<? extends T> source) {
        return M3ObjectStream.snapshot(Objects.requireNonNull(source, "source"));
    }

    /** Universal live adapter; encounter and fail-fast semantics remain provider-owned. */
    public static <T> M3ObjectStream<T> liveAny(java.util.Collection<T> source) {
        return M3ObjectStream.live(Objects.requireNonNull(source, "source"));
    }
}

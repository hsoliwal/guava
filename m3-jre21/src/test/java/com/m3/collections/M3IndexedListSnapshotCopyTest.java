/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections;

import java.lang.reflect.Field;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import org.junit.Test;

/** Detached-owner and callback contracts for copying final indexed snapshots. */
public final class M3IndexedListSnapshotCopyTest {
    private static long observations;

    private static void require(boolean condition, String message) {
        observations++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Object field(M3IndexedListSnapshot<?> snapshot, String name)
            throws ReflectiveOperationException {
        Field field = M3IndexedListSnapshot.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(snapshot);
    }

    private static void verify(M3IndexedListSnapshot<?> source)
            throws ReflectiveOperationException {
        Object[] expected = source.toArray();
        int[] oldPositions = source.physicalIndices();
        Collection<?> collection = source;
        M3IndexedListSnapshot<?> copy = M3IndexedListSnapshot.copyOf(collection);
        require(copy != source, "copy returned original snapshot");
        require(copy.size() == expected.length, "copy changed logical size");
        require(field(copy, "values") != field(source, "values"),
                "copy retained source payload array");
        require(field(copy, "positions") != field(source, "positions"),
                "copy retained source index array");
        require(field(copy, "values").getClass() == Object[].class,
                "payload must remain exact Object[]");
        require(((Object[]) field(copy, "values")).length == expected.length,
                "copy retained unselected source payload slots");
        require(field(copy, "physicalMembership") == null,
                "copy inherited physical membership in the old coordinate space");
        for (int i = 0; i < expected.length; i++) {
            require(copy.get(i) == expected[i], "payload identity or encounter order changed");
            require(copy.physicalIndex(i) == i, "copy did not reset physical coordinates");
        }
        Object[] exported = copy.toArray();
        if (exported.length != 0) {
            exported[0] = new Object();
            require(copy.get(0) == expected[0], "public export leaked owned payload");
        }
        require(Arrays.equals(oldPositions, source.physicalIndices()),
                "source physical order changed");
        for (int i = 0; i < expected.length; i++) {
            require(source.get(i) == expected[i], "source payload changed");
        }
    }

    /** Deliberately exports its retained array; only toArray may be called. */
    private static final class ArraySource extends AbstractCollection<Object> {
        private final Object[] retained;
        private final RuntimeException failure;
        private int calls;

        ArraySource(Object[] retained) {
            this(retained, null);
        }

        ArraySource(Object[] retained, RuntimeException failure) {
            this.retained = retained;
            this.failure = failure;
        }

        @Override public Object[] toArray() {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return retained;
        }

        @Override public int size() {
            throw new AssertionError("copy must not call source.size()");
        }

        @Override public Iterator<Object> iterator() {
            throw new AssertionError("copy must not call source.iterator()");
        }
    }

    @Test
    public void emptySingletonNullsAndDuplicateReferencesAreDetached() throws Exception {
        Object duplicate = new Object();
        for (Object[] input : new Object[][] {
                {}, {null}, {duplicate}, {duplicate, null, duplicate, null}}) {
            M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(input);
            verify(source);
            verify(M3IndexedListSnapshot.copyOf(source));
        }
    }

    @Test
    public void slicedFilteredSortedCopyResetsPhysicalCoordinates() throws Exception {
        Integer[] input = new Integer[257];
        for (int i = 0; i < input.length; i++) {
            input[i] = (i * 73) % 31;
        }
        M3IndexedListSnapshot<Integer> source = M3IndexedListSnapshot.copyOf(input)
                .slice(13, 231).filtered(value -> (value & 3) != 0)
                .sorted(Comparator.reverseOrder()).slice(5, 129);
        BitSet before = source.physicalMaskCopy();
        verify(source);
        M3IndexedListSnapshot<Integer> copy = M3IndexedListSnapshot.copyOf(source);
        require(copy.physicalMaskCopy().cardinality() == copy.size(),
                "copy must have one physical slot per logical occurrence");
        require(copy.physicalMaskCopy().nextClearBit(0) == copy.size(),
                "new physical membership must be a dense prefix");
        require(before.equals(source.physicalMaskCopy()), "source membership changed");
    }

    @Test
    public void arbitraryCollectionRetainsDefensiveCopyAndSingleCallback() throws Exception {
        Object first = new Object();
        Object second = new Object();
        Object[] retained = {first, null, second, first};
        ArraySource source = new ArraySource(retained);
        M3IndexedListSnapshot<Object> copy = M3IndexedListSnapshot.copyOf(source);
        require(source.calls == 1, "arbitrary source toArray callback count");
        require(field(copy, "values") != retained, "arbitrary source alias was adopted");
        Arrays.fill(retained, null);
        require(copy.get(0) == first && copy.get(2) == second && copy.get(3) == first,
                "arbitrary source mutation affected copy");
    }

    @Test
    public void covariantArrayFromArbitraryCollectionStillNormalizes() throws Exception {
        String[] retained = {"first", null, "last"};
        ArraySource source = new ArraySource(retained);
        M3IndexedListSnapshot<Object> copy = M3IndexedListSnapshot.copyOf(source);
        require(source.calls == 1, "covariant source callback count");
        require(field(copy, "values").getClass() == Object[].class,
                "covariant source component type was retained");
        retained[0] = "changed";
        require("first".equals(copy.get(0)), "covariant source alias was adopted");
    }

    @Test
    public void sourceFailuresAndNullOverloadsPreserveBehavior() {
        RuntimeException signal = new IllegalStateException("source toArray failure");
        ArraySource throwing = new ArraySource(new Object[0], signal);
        try {
            M3IndexedListSnapshot.copyOf(throwing);
            throw new AssertionError("source failure was swallowed");
        } catch (RuntimeException failure) {
            require(failure == signal, "source exception identity changed");
            require(throwing.calls == 1, "throwing source called again");
        }
        ArraySource nullArray = new ArraySource(null);
        try {
            M3IndexedListSnapshot.copyOf(nullArray);
            throw new AssertionError("null source array was accepted");
        } catch (NullPointerException expected) {
            require(nullArray.calls == 1, "null-array source callback count");
        }
        try {
            M3IndexedListSnapshot.copyOf((Collection<Object>) null);
            throw new AssertionError("null collection accepted");
        } catch (NullPointerException expected) {
            require("source".equals(expected.getMessage()), "null collection message");
        }
        try {
            M3IndexedListSnapshot.copyOf((Object[]) null);
            throw new AssertionError("null array accepted");
        } catch (NullPointerException expected) {
            require("source".equals(expected.getMessage()), "null array message");
        }
    }

    @Test
    public void arrayOverloadStillCopiesAndCollectionCopyReadsNoElements() throws Exception {
        Object first = new Object();
        Object[] original = {first, null};
        M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(original);
        original[0] = new Object();
        require(source.get(0) == first, "array overload lost defensive copy");
        Object hostile = new Object() {
            @Override public boolean equals(Object other) {
                throw new AssertionError("copy invoked payload equals");
            }
            @Override public int hashCode() {
                throw new AssertionError("copy invoked payload hashCode");
            }
            @Override public String toString() {
                throw new AssertionError("copy invoked payload toString");
            }
        };
        verify(M3IndexedListSnapshot.copyOf(new Object[] {hostile, null, hostile}));
    }

    @Test
    public void randomizedViewsRetainLogicalIdentityAndDetachedStorage() throws Exception {
        Random random = new Random(0x636f70795f6c616eL);
        for (int trial = 0; trial < 700; trial++) {
            Integer[] input = new Integer[random.nextInt(513)];
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt(7) == 0 ? null : random.nextInt(17) - 8;
            }
            M3IndexedListSnapshot<Integer> source = M3IndexedListSnapshot.copyOf(input);
            int start = random.nextInt(input.length + 1);
            int length = random.nextInt(input.length - start + 1);
            source = source.slice(start, length);
            if ((trial & 1) == 0) {
                source = source.filtered(value -> value == null || (value & 1) == 0);
            }
            if (trial % 3 == 0) {
                source = source.sorted(Comparator.nullsFirst(Comparator.naturalOrder()));
            }
            if (trial % 5 == 0) {
                source.physicalMaskCopy();
            }
            verify(source);
        }
    }

    public static void main(String[] args) throws Exception {
        M3IndexedListSnapshotCopyTest test = new M3IndexedListSnapshotCopyTest();
        test.emptySingletonNullsAndDuplicateReferencesAreDetached();
        test.slicedFilteredSortedCopyResetsPhysicalCoordinates();
        test.arbitraryCollectionRetainsDefensiveCopyAndSingleCallback();
        test.covariantArrayFromArbitraryCollectionStillNormalizes();
        test.sourceFailuresAndNullOverloadsPreserveBehavior();
        test.arrayOverloadStillCopiesAndCollectionCopyReadsNoElements();
        test.randomizedViewsRetainLogicalIdentityAndDetachedStorage();
        System.out.println("M3_SNAPSHOT_COPY PASS tests=7 observations=" + observations);
    }
}

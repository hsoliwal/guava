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

    private static void verifyFilter(M3IndexedListSnapshot<Object> source, boolean[] keep,
            M3IndexMaskCompactor.Backend backend) {
        Object[] oldValues = source.toArray();
        int[] oldPositions = source.physicalIndices();
        BitSet oldMembership = source.physicalMaskCopy();
        int[] expected = new int[keep.length];
        int expectedCount = 0;
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) expected[expectedCount++] = oldPositions[i];
        }
        final int[] calls = {0};
        M3IndexedListSnapshot<Object> selected = source.filtered(value -> {
            int ordinal = calls[0]++;
            require(ordinal < oldValues.length, "extra predicate invocation");
            require(value == oldValues[ordinal], "predicate encounter order changed");
            return keep[ordinal];
        }, backend);
        require(calls[0] == keep.length, "predicate invocation count");
        require(selected != source, "filter reused observable snapshot identity");
        require(selected.size() == expectedCount, "filter result cardinality");
        require(Arrays.equals(selected.physicalIndices(), Arrays.copyOf(expected, expectedCount)),
                "filter lost occurrence positions or encounter order");
        for (int i = 0, out = 0; i < keep.length; i++) {
            if (keep[i]) require(selected.get(out++) == oldValues[i], "filter payload identity");
        }
        require(Arrays.equals(source.physicalIndices(), oldPositions), "filter changed source indexes");
        require(source.physicalMaskCopy().equals(oldMembership), "filter changed source membership");
        BitSet exported = selected.physicalMaskCopy();
        exported.flip(0, oldMembership.length() + 1);
        require(source.physicalMaskCopy().equals(oldMembership), "mutable mask leaked to source");
    }

    @Test
    public void filteringPreservesOccurrencesAcrossWordBoundaries() {
        Object duplicate = new Object();
        Random random = new Random(0x7061636b65645fL);
        int[] lengths = {0, 1, 2, 31, 32, 63, 64, 65, 127, 128, 129, 255, 256, 257, 1023};
        for (int length : lengths) {
            for (int offset : new int[] {0, 1, 7, 63, 64, 65}) {
                Object[] values = new Object[length + offset + 3];
                for (int i = 0; i < values.length; i++) {
                    values[i] = i % 3 == 0 ? null : i % 3 == 1 ? duplicate : new Object();
                }
                M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(values)
                        .slice(offset, length);
                for (int mode = 0; mode < 6; mode++) {
                    boolean[] keep = new boolean[length];
                    for (int i = 0; i < length; i++) {
                        keep[i] = switch (mode) {
                            case 0 -> false;
                            case 1 -> true;
                            case 2 -> (i & 1) == 0;
                            case 3 -> (i & 63) == 63;
                            case 4 -> (i & 63) == 0;
                            default -> random.nextBoolean();
                        };
                    }
                    verifyFilter(source, keep, M3IndexMaskCompactor.Backend.JAVA);
                }
            }
        }
    }

    @Test
    public void filteringSortedAndNestedSlicesKeepsPhysicalCoordinates() {
        Random random = new Random(0x696e6465785fL);
        Comparator<Object> comparator = Comparator.nullsFirst(
                Comparator.comparingInt(value -> (Integer) value));
        for (int trial = 0; trial < 400; trial++) {
            Object[] values = new Object[random.nextInt(514)];
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt(8) == 0 ? null : random.nextInt(19) - 9;
            }
            M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(values)
                    .sorted(comparator);
            int start = random.nextInt(source.size() + 1);
            source = source.slice(start, random.nextInt(source.size() - start + 1));
            source = source.filtered(value -> value == null || ((Integer) value & 1) == 0);
            int nextStart = random.nextInt(source.size() + 1);
            source = source.slice(nextStart, source.size() - nextStart);
            boolean[] keep = new boolean[source.size()];
            for (int i = 0; i < keep.length; i++) keep[i] = random.nextBoolean();
            verifyFilter(source, keep, M3IndexMaskCompactor.Backend.JAVA);
        }
    }

    @Test
    public void predicateFailureIdentityAndDemandRemainUnchanged() {
        Object[] values = new Object[130];
        Arrays.fill(values, new Object());
        M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(values).slice(1, 128);
        int[] positions = source.physicalIndices();
        for (int stop : new int[] {0, 1, 62, 63, 64, 65, 127}) {
            RuntimeException failure = new IllegalStateException("predicate failure " + stop);
            int[] calls = {0};
            try {
                source.filtered(value -> {
                    if (calls[0]++ == stop) throw failure;
                    return true;
                });
                throw new AssertionError("predicate failure swallowed");
            } catch (RuntimeException observed) {
                require(observed == failure, "predicate exception identity changed");
                require(calls[0] == stop + 1, "callback demand after exception");
            }
            require(Arrays.equals(positions, source.physicalIndices()), "exception mutated owner");
        }
    }

    @Test
    public void reentrantFilteringRetainsIndependentInvocationState() {
        Object[] values = new Object[129];
        Object duplicate = new Object();
        Arrays.fill(values, duplicate);
        M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(values);
        int[] outerCalls = {0};
        int[] innerCalls = {0};
        M3IndexedListSnapshot<Object> selected = source.filtered(value -> {
            int ordinal = outerCalls[0]++;
            if (ordinal == 64) {
                M3IndexedListSnapshot<Object> inner = source.filtered(ignored -> {
                    innerCalls[0]++;
                    return false;
                });
                require(inner.isEmpty(), "nested selection leaked outer decisions");
                require(source.physicalMaskCopy().cardinality() == source.size(),
                        "nested membership request changed physical owner");
            }
            return (ordinal & 1) == 0;
        });
        require(outerCalls[0] == 129 && innerCalls[0] == 129, "reentrant callback counts");
        require(selected.size() == 65, "reentrant selection count");
        for (int i = 0; i < selected.size(); i++) {
            require(selected.physicalIndex(i) == i * 2, "reentrant physical order");
        }
    }

    @Test
    public void filteringValidationAndNativeRequirementPrecedeCallbacks() {
        M3IndexedListSnapshot<Object> source = M3IndexedListSnapshot.copyOf(new Object[] {null});
        int[] calls = {0};
        try {
            source.filtered(null, null);
            throw new AssertionError("null predicate and backend accepted");
        } catch (NullPointerException expected) {
            require("predicate".equals(expected.getMessage()), "predicate validation order");
        }
        try {
            source.filtered(value -> { calls[0]++; return true; }, null);
            throw new AssertionError("null backend accepted");
        } catch (NullPointerException expected) {
            require("backend".equals(expected.getMessage()), "backend validation message");
        }
        if (!M3IndexMaskCompactor.nativeAvailable()) {
            for (M3IndexedListSnapshot<Object> view : Arrays.asList(source, source.slice(0, 0))) {
                try {
                    view.filtered(value -> { calls[0]++; return true; },
                            M3IndexMaskCompactor.Backend.NATIVE_REQUIRED);
                    throw new AssertionError("native requirement silently fell back to Java");
                } catch (UnsatisfiedLinkError expected) {
                    require("m3_collections_mask JNI library unavailable".equals(expected.getMessage()),
                            "native absence error changed");
                }
            }
        } else {
            verifyFilter(source, new boolean[] {true}, M3IndexMaskCompactor.Backend.NATIVE_REQUIRED);
            verifyFilter(source, new boolean[] {false}, M3IndexMaskCompactor.Backend.NATIVE_REQUIRED);
        }
        require(calls[0] == 0, "validation or absent native library invoked predicate");
    }

    @Test
    public void allPassSharesOnlyCompleteImmutableIndexStorage() throws Exception {
        Integer[] values = new Integer[129];
        for (int i = 0; i < values.length; i++) values[i] = values.length - i;
        M3IndexedListSnapshot<Integer> source = M3IndexedListSnapshot.copyOf(values);
        source.physicalMaskCopy();
        M3IndexedListSnapshot<Integer> selected = source.filtered(value -> true);
        require(field(selected, "values") == field(source, "values"), "payload was copied");
        require(field(selected, "positions") == field(source, "positions"),
                "complete immutable directory was redundantly copied");
        require(field(selected, "physicalMembership") == field(source, "physicalMembership"),
                "immutable membership was redundantly rebuilt");
        M3IndexedListSnapshot<Integer> sorted = selected.sorted();
        require(sorted.get(0).equals(1), "sort of selected directory");
        require(source.get(0).equals(129) && selected.get(0).equals(129),
                "sorting selected snapshot mutated shared index storage");
        M3IndexedListSnapshot<Integer> slice = source.slice(63, 65);
        M3IndexedListSnapshot<Integer> selectedSlice = slice.filtered(value -> true);
        require(field(selectedSlice, "positions") != field(slice, "positions"),
                "small selection retained larger index storage");
        require(((int[]) field(selectedSlice, "positions")).length == 65,
                "slice did not retain exact index storage");
        require(Arrays.equals(selectedSlice.physicalIndices(), slice.physicalIndices()),
                "all-pass slice changed physical coordinates");
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
        test.filteringPreservesOccurrencesAcrossWordBoundaries();
        test.filteringSortedAndNestedSlicesKeepsPhysicalCoordinates();
        test.predicateFailureIdentityAndDemandRemainUnchanged();
        test.reentrantFilteringRetainsIndependentInvocationState();
        test.filteringValidationAndNativeRequirementPrecedeCallbacks();
        test.allPassSharesOnlyCompleteImmutableIndexStorage();
        System.out.println("M3_SNAPSHOT_COPY PASS tests=13 observations=" + observations);
    }
}

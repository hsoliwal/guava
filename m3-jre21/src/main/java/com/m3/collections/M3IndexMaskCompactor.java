/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections;

import java.util.ConcurrentModificationException;
import java.util.Objects;

/**
 * Compacts an already-ordered primitive row index using a caller-precomputed state mask.
 *
 * <p>Position {@code i} in {@code rowStates} describes {@code orderedRows[i]};
 * values are unsigned bytes for mask tests. The selected output retains the original
 * index order and duplicate IDs. Neither input is mutated or retained. An explicit
 * JNI backend is optional and never receives predicates, comparators or object refs.
 *
 * <p>Selection is O(n) time, O(k) returned slots (two Java passes, one exact array).
 * Existing M3JDK collection owners retain domain lifetime, modification epochs,
 * order, and index composition; a detached mask is not a live collection view.
 */
public final class M3IndexMaskCompactor {
    /** Backends do not silently change based on machine configuration. */
    public enum Backend { JAVA, NATIVE_REQUIRED }

    private M3IndexMaskCompactor() {
    }

    /** Returns whether a compatible optional JNI binary could be loaded. */
    public static boolean nativeAvailable() {
        return NativeLibrary.AVAILABLE;
    }

    /**
     * Select row IDs satisfying all {@code requiredMask} bits, preserving order.
     * Exactly one input validation pass is performed before the JNI call.
     * Source aliases may change after return; the output is always detached.
     */
    public static int[] select(int[] orderedRows, byte[] rowStates,
                               int requiredMask, Backend backend) {
        Objects.requireNonNull(orderedRows, "orderedRows");
        Objects.requireNonNull(rowStates, "rowStates");
        Objects.requireNonNull(backend, "backend");
        validate(rowStates, orderedRows.length, requiredMask);
        if (backend == Backend.NATIVE_REQUIRED) {
            if (!NativeLibrary.AVAILABLE) {
                throw new UnsatisfiedLinkError("m3_index_mask JNI library unavailable");
            }
            return selectNative(orderedRows, rowStates, requiredMask);
        }
        return selectJava(orderedRows, rowStates, requiredMask);
    }

    /**
     * Select logical positions satisfying all required state bits without a caller-owned
     * identity row array. The result is detached, ascending encounter-order coordinates.
     * New native binaries generate coordinates directly from the byte-state lane; legacy
     * binaries remain compatible through the original row selector.
     */
    public static int[] selectPositions(byte[] rowStates, int requiredMask, Backend backend) {
        Objects.requireNonNull(rowStates, "rowStates");
        Objects.requireNonNull(backend, "backend");
        requireMask(requiredMask);
        if (backend == Backend.NATIVE_REQUIRED) {
            if (!NativeLibrary.AVAILABLE) {
                throw new UnsatisfiedLinkError("m3_index_mask JNI library unavailable");
            }
            if (NativePositions.AVAILABLE) {
                return selectPositionsNative(rowStates, requiredMask);
            }
            return selectNative(identityPositions(rowStates.length), rowStates, requiredMask);
        }
        return selectPositionsJava(rowStates, requiredMask);
    }

    /**
     * Internal native selection over an owner's stable half-open index window.
     * States describe logical positions, not physical row IDs. This never retains inputs.
     * Old binaries keep using their original native selector and defensive window copy;
     * an absent library still fails, and a native request never falls back to Java.
     */
    static int[] selectNativeRange(int[] orderedRows, int first, int length,
            byte[] rowStates, int requiredMask) {
        Objects.requireNonNull(orderedRows, "orderedRows");
        Objects.checkFromIndexSize(first, length, orderedRows.length);
        validate(rowStates, length, requiredMask);
        if (!NativeLibrary.AVAILABLE) {
            throw new UnsatisfiedLinkError("m3_index_mask JNI library unavailable");
        }
        if (NativeRange.AVAILABLE) {
            return selectRangeNative(orderedRows, rowStates, first, length, requiredMask);
        }
        return selectNative(java.util.Arrays.copyOfRange(orderedRows, first, first + length),
                rowStates, requiredMask);
    }

    /** Count set-flag matches without boxing, copying, or retaining a view. */
    public static int selectedCount(byte[] rowStates, int requiredMask) {
        Objects.requireNonNull(rowStates, "rowStates");
        requireMask(requiredMask);
        return countSelected(rowStates, requiredMask);
    }

    /** Primitive-long index selection, preserving source order and duplicate IDs. */
    public static long[] selectLong(long[] orderedRows, byte[] rowStates, int requiredMask) {
        Objects.requireNonNull(orderedRows, "orderedRows");
        validate(rowStates, orderedRows.length, requiredMask);
        int selectedCount = countSelected(rowStates, requiredMask);
        // Every row is selected: one defensive primitive copy, no second mask scan.
        if (selectedCount == orderedRows.length) return orderedRows.clone();
        long[] selected = new long[selectedCount];
        int written = 0;
        for (int i = 0; i < orderedRows.length; i++) {
            if (matches(rowStates[i], requiredMask)) {
                if (written == selected.length) {
                    throw new ConcurrentModificationException("aligned selection states changed");
                }
                selected[written++] = orderedRows[i];
            }
        }
        if (written != selected.length) {
            throw new ConcurrentModificationException("aligned selection states changed");
        }
        return selected;
    }

    /** Validate before an in-place primitive owner writes a single resident slot. */
    static void validate(byte[] states, int logicalSize, int requiredMask) {
        Objects.requireNonNull(states, "rowStates");
        if (states.length != logicalSize) {
            throw new IllegalArgumentException("index and aligned state lengths differ");
        }
        requireMask(requiredMask);
    }

    private static void requireMask(int requiredMask) {
        if ((requiredMask & ~0xff) != 0) {
            throw new IllegalArgumentException("required mask must fit an unsigned byte");
        }
    }

    static boolean matches(byte state, int requiredMask) {
        return ((state & 0xff) & requiredMask) == requiredMask;
    }

    private static int countSelected(byte[] states, int requiredMask) {
        if (requiredMask == 0) return states.length;
        int kept = 0;
        for (byte state : states) if (matches(state, requiredMask)) kept++;
        return kept;
    }

    /**
     * Java reference kernel: exactly one final primitive array, no O(n) scratch
     * or per-element object. Caller must keep both input arrays stable during use.
     */
    static int[] selectJava(int[] orderedRows, byte[] rowStates, int requiredMask) {
        int selectedCount = countSelected(rowStates, requiredMask);
        // Retain output isolation even when the mask selects every source index.
        if (selectedCount == orderedRows.length) return orderedRows.clone();
        int[] selected = new int[selectedCount];
        int written = 0;
        for (int i = 0; i < orderedRows.length; i++) {
            if (matches(rowStates[i], requiredMask)) {
                if (written == selected.length) {
                    throw new ConcurrentModificationException("aligned selection states changed");
                }
                selected[written++] = orderedRows[i];
            }
        }
        if (written != selected.length) {
            throw new ConcurrentModificationException("aligned selection states changed");
        }
        return selected;
    }

    /** Exact-sized logical-position selection; no O(n) identity input scratch. */
    static int[] selectPositionsJava(byte[] rowStates, int requiredMask) {
        int selectedCount = countSelected(rowStates, requiredMask);
        if (selectedCount == rowStates.length) return identityPositions(rowStates.length);
        int[] selected = new int[selectedCount];
        int written = 0;
        for (int i = 0; i < rowStates.length; i++) {
            if (matches(rowStates[i], requiredMask)) {
                if (written == selected.length) {
                    throw new ConcurrentModificationException("aligned selection states changed");
                }
                selected[written++] = i;
            }
        }
        if (written != selected.length) {
            throw new ConcurrentModificationException("aligned selection states changed");
        }
        return selected;
    }

    private static int[] identityPositions(int length) {
        int[] positions = new int[length];
        for (int i = 0; i < length; i++) positions[i] = i;
        return positions;
    }

    /**
     * Select a bounded logical window with independently validated offsets into
     * the dynamic row and state lanes; no input slices or per-row objects.
     * The detached result preserves source encounter order and duplicate IDs.
     * Both inputs must remain stable during this call.
     */
    public static int[] selectWindow(int[] orderedRows, int rowOffset,
                                     byte[] states, int stateOffset, int length,
                                     int requiredMask, Backend backend) {
        Objects.requireNonNull(orderedRows, "orderedRows");
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(backend, "backend");
        Objects.checkFromIndexSize(rowOffset, length, orderedRows.length);
        Objects.checkFromIndexSize(stateOffset, length, states.length);
        requireMask(requiredMask);
        if (backend == Backend.NATIVE_REQUIRED) {
            if (!NativeLibrary.AVAILABLE) {
                throw new UnsatisfiedLinkError("m3_index_mask JNI library unavailable");
            }
            return selectWindowNative(orderedRows, rowOffset, states, stateOffset,
                    length, requiredMask);
        }
        int kept = countWindow(states, stateOffset, length, requiredMask);
        if (kept == length) {
            return java.util.Arrays.copyOfRange(orderedRows, rowOffset, rowOffset + length);
        }
        int[] selected = new int[kept];
        int written = 0;
        for (int i = 0; i < length; i++) {
            if (matches(states[stateOffset + i], requiredMask)) {
                if (written == kept) {
                    throw new ConcurrentModificationException("aligned selection states changed");
                }
                selected[written++] = orderedRows[rowOffset + i];
            }
        }
        if (written != kept) {
            throw new ConcurrentModificationException("aligned selection states changed");
        }
        return selected;
    }

    /** Primitive-long counterpart, preserving signed extremes and duplicates. */
    public static long[] selectLongWindow(long[] orderedRows, int rowOffset,
                                          byte[] states, int stateOffset, int length,
                                          int requiredMask, Backend backend) {
        Objects.requireNonNull(orderedRows, "orderedRows");
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(backend, "backend");
        Objects.checkFromIndexSize(rowOffset, length, orderedRows.length);
        Objects.checkFromIndexSize(stateOffset, length, states.length);
        requireMask(requiredMask);
        if (backend == Backend.NATIVE_REQUIRED) {
            if (!NativeLibrary.AVAILABLE) {
                throw new UnsatisfiedLinkError("m3_index_mask JNI library unavailable");
            }
            return selectLongWindowNative(orderedRows, rowOffset, states, stateOffset,
                    length, requiredMask);
        }
        int kept = countWindow(states, stateOffset, length, requiredMask);
        if (kept == length) {
            return java.util.Arrays.copyOfRange(orderedRows, rowOffset, rowOffset + length);
        }
        long[] selected = new long[kept];
        int written = 0;
        for (int i = 0; i < length; i++) {
            if (matches(states[stateOffset + i], requiredMask)) {
                if (written == kept) {
                    throw new ConcurrentModificationException("aligned selection states changed");
                }
                selected[written++] = orderedRows[rowOffset + i];
            }
        }
        if (written != kept) {
            throw new ConcurrentModificationException("aligned selection states changed");
        }
        return selected;
    }

    /** Zero-mask selection is known without reading any state words. */
    private static int countWindow(byte[] states, int offset, int length, int mask) {
        if (mask == 0) return length;
        int kept = 0;
        for (int i = 0; i < length; i++) {
            if (matches(states[offset + i], mask)) kept++;
        }
        return kept;
    }

    private static native int[] selectWindowNative(int[] rows, int rowOffset,
            byte[] states, int stateOffset, int length, int mask);

    private static native long[] selectLongWindowNative(long[] rows, int rowOffset,
            byte[] states, int stateOffset, int length, int mask);

    private static final class NativeLibrary {
        private static final boolean AVAILABLE;

        static {
            boolean loaded;
            try {
                System.loadLibrary("m3_collections_mask");
                loaded = true;
            } catch (UnsatisfiedLinkError unsupported) {
                // Java remains the default; only NATIVE_REQUIRED demands the library.
                loaded = false;
            }
            AVAILABLE = loaded;
        }
    }

    /** Lazy capability probe keeps old JNI binaries compatible with position selection. */
    private static final class NativePositions {
        private static final boolean AVAILABLE = available();
        private static boolean available() {
            try {
                return positionsAbiNative() == 1;
            } catch (UnsatisfiedLinkError legacyBinary) {
                return false;
            }
        }
    }

    private static native int positionsAbiNative();

    private static native int[] selectPositionsNative(byte[] rowStates, int requiredMask);

    /** Lazy probing does not change initialization of the legacy public native API. */
    private static final class NativeRange {
        private static final boolean AVAILABLE = available();
        private static boolean available() {
            try {
                return rangeAbiNative() == 1;
            } catch (UnsatisfiedLinkError legacyBinary) {
                return false;
            }
        }
    }

    private static native int rangeAbiNative();

    private static native int[] selectRangeNative(int[] orderedRows, byte[] rowStates,
            int first, int length, int requiredMask);

    private static native int[] selectNative(int[] orderedRows, byte[] rowStates,
                                              int requiredMask);
}

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;
import java.util.Random;
import org.junit.Test;

/** Numeric capacity contracts; no large backing array is constructed. */
public final class M3SlotArraysCapacityTest {
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger THREE = BigInteger.valueOf(3);

    @Test public void smallCapacitiesKeepTheExistingGrowthPolicy() {
        int[][] cases = {{0, 8}, {1, 8}, {4, 8}, {7, 11}, {8, 13},
                {15, 23}, {16, 25}, {1024, 1537}};
        for (int[] item : cases) assertEquals(item[1], M3SlotArrays.grow(item[0]));
    }

    @Test public void largeCapacitiesNeverShrinkAtSignedAdditionBoundaries() {
        int[] boundaries = {1_431_655_765, 1_431_655_766, 1_500_000_000,
                1_610_612_736, 2_000_000_000, MAX_CAPACITY - 1};
        for (int boundary : boundaries) {
            for (int delta = -8; delta <= 8; delta++) {
                long capacity = (long) boundary + delta;
                if (capacity >= 0 && capacity < MAX_CAPACITY) {
                    assertGrowth((int) capacity);
                }
            }
        }
        assertEquals(MAX_CAPACITY, M3SlotArrays.grow(1_500_000_000));
    }

    @Test public void validCapacitiesMatchIndependentBigIntegerArithmetic() {
        Random random = new Random(0x4d33534c4f544152L);
        for (int i = 0; i < 20_000; i++) assertGrowth(random.nextInt(MAX_CAPACITY));
    }

    @Test public void exhaustedCapacitiesKeepTheExistingFailure() {
        for (long capacity = MAX_CAPACITY; capacity <= Integer.MAX_VALUE; capacity++) {
            int old = (int) capacity;
            OutOfMemoryError error = assertThrows(OutOfMemoryError.class,
                    () -> M3SlotArrays.grow(old));
            assertEquals("collection capacity exhausted", error.getMessage());
        }
    }

    private static void assertGrowth(int old) {
        // floor(3 * old / 2) + 1 is computed without fixed-width overflow.
        int expected = BigInteger.valueOf(old).multiply(THREE).divide(TWO)
                .add(BigInteger.ONE).max(BigInteger.valueOf(8))
                .min(BigInteger.valueOf(MAX_CAPACITY)).intValueExact();
        int actual = M3SlotArrays.grow(old);
        assertEquals("capacity " + old, expected, actual);
        assertTrue("growth must exceed a usable old capacity", actual > old);
        assertTrue("capacity ceiling", actual <= MAX_CAPACITY);
    }
}

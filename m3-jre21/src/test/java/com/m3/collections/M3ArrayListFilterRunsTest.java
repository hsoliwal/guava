// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.collections;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Public list behavior and failure atomicity across sparse and dense masks. */
public final class M3ArrayListFilterRunsTest {
    @Test public void everySmallMaskPreservesStableOrderAndClearsReferences() throws Exception {
        for (int size = 0; size <= 10; size++) {
            for (int bits = 0; bits < (1 << size); bits++) {
                boolean[] rejected = new boolean[size];
                for (int i = 0; i < size; i++) rejected[i] = (bits & (1 << i)) != 0;
                assertFilter(rejected);
            }
        }
    }

    @Test public void wordBoundariesAndLongSurvivorRunsMatchTheListOracle() throws Exception {
        Random random = new Random(0x4d3346494c544552L);
        for (int size : new int[] {31, 32, 63, 64, 65, 127, 128, 129, 255, 1025}) {
            for (int mode = 0; mode < 10; mode++) {
                boolean[] rejected = new boolean[size];
                for (int i = 0; i < size; i++) {
                    rejected[i] = switch (mode) {
                        case 0 -> i == 0;
                        case 1 -> i == size - 1;
                        case 2 -> i == size / 2;
                        case 3 -> i % 64 == 63;
                        case 4 -> i % 64 != 63;
                        case 5 -> i >= size / 3 && i < 2 * size / 3;
                        case 6 -> (i & 1) == 0;
                        case 7 -> random.nextBoolean();
                        case 8 -> true;
                        default -> false;
                    };
                }
                assertFilter(rejected);
            }
        }
    }

    @Test public void predicateFailureRetainsOwnerReferencesAndIteratorEpoch() throws Exception {
        for (int failAt = 0; failAt < 130; failAt++) {
            M3ArrayList<Integer> list = sequence(130);
            Object[] before = list.toArray();
            Object[] backing = storage(list);
            Iterator<Integer> iterator = list.iterator();
            RuntimeException failure = new RuntimeException("predicate");
            int[] calls = {0};
            int selected = failAt;
            assertSame(failure, assertThrows(RuntimeException.class, () -> list.removeIf(value -> {
                if (calls[0]++ == selected) throw failure;
                return (value & 1) == 0;
            })));
            assertEquals(failAt + 1, calls[0]);
            assertArrayEquals(before, list.toArray());
            assertSame(backing, storage(list));
            assertEquals(Integer.valueOf(0), iterator.next());
        }
    }

    @Test public void structuralReentrancyStopsBeforeAnyCompaction() {
        for (int trigger : new int[] {0, 1, 63, 64, 129}) {
            M3ArrayList<Integer> list = sequence(130);
            int[] calls = {0};
            ConcurrentModificationException failure = assertThrows(
                    ConcurrentModificationException.class, () -> list.removeIf(value -> {
                        calls[0]++;
                        if (value == trigger) list.add(-1);
                        return true;
                    }));
            assertEquals("predicate structurally changed owner", failure.getMessage());
            assertEquals(trigger + 1, calls[0]);
            assertEquals(131, list.size());
            for (int i = 0; i < 130; i++) assertEquals(Integer.valueOf(i), list.get(i));
            assertEquals(Integer.valueOf(-1), list.getLast());
        }
    }

    @Test public void nonStructuralPredicateUpdatesRemainVisible() {
        M3ArrayList<Integer> list = sequence(8);
        List<Integer> visited = new ArrayList<>();
        assertTrue(list.removeIf(value -> {
            visited.add(value);
            if (value == 2) list.set(0, -9);
            if (value == 5) list.set(6, 66);
            return value == 1 || value == 4;
        }));
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 66, 7), visited);
        assertEquals(Arrays.asList(-9, 2, 3, 5, 66, 7), list);
    }

    @Test public void noRemovalAndRemovalKeepTheirDistinctIteratorAndViewEpochs() {
        M3ArrayList<Integer> list = sequence(5);
        List<Integer> view = list.subList(1, 4);
        Iterator<Integer> before = list.iterator();
        assertFalse(list.removeIf(value -> false));
        assertEquals(Integer.valueOf(0), before.next());
        assertEquals(Arrays.asList(1, 2, 3), view);
        assertTrue(list.removeIf(value -> true));
        assertTrue(list.isEmpty());
        assertThrows(ConcurrentModificationException.class, before::next);
        assertThrows(ConcurrentModificationException.class, view::size);
        assertThrows(NullPointerException.class, () -> list.removeIf(null));
    }

    @Test public void filteredValuesRoundTripAndKeepDetachedCloneAndSnapshot() throws Exception {
        M3ArrayList<Integer> list = sequence(130);
        list.set(64, null);
        list.removeIf(value -> value != null && value % 3 == 0);
        M3ArrayList<Integer> clone = list.clone();
        List<Integer> snapshot = list.snapshot();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(list); }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(list, input.readObject());
        }
        Integer[] oversized = new Integer[list.size() + 2];
        Arrays.fill(oversized, -1);
        assertSame(oversized, list.toArray(oversized));
        assertNull(oversized[list.size()]);
        assertEquals(Integer.valueOf(-1), oversized[list.size() + 1]);
        List<Integer> expected = new ArrayList<>(list);
        list.clear();
        assertEquals(expected, clone);
        assertEquals(expected, snapshot);
    }

    private static void assertFilter(boolean[] rejected) throws Exception {
        M3ArrayList<Integer> list = new M3ArrayList<>(rejected.length + 11);
        List<Integer> before = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < rejected.length; i++) {
            Integer value = i % 7 == 0 ? null : Integer.valueOf(i % 13);
            list.add(value);
            before.add(value);
            if (!rejected[i]) expected.add(value);
        }
        int capacity = list.capacity();
        Object[] backing = storage(list);
        List<Integer> visited = new ArrayList<>();
        int[] cursor = {0};
        assertEquals(expected.size() != before.size(), list.removeIf(value -> {
            visited.add(value);
            return rejected[cursor[0]++];
        }));
        assertEquals(before, visited);
        assertEquals(expected, list);
        assertEquals(capacity, list.capacity());
        assertSame(backing, storage(list));
        for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), list.get(i));
        for (int i = list.size(); i < backing.length; i++) assertNull(backing[i]);
    }

    private static M3ArrayList<Integer> sequence(int count) {
        M3ArrayList<Integer> list = new M3ArrayList<>(count + 3);
        for (int i = 0; i < count; i++) list.add(i);
        return list;
    }

    private static Object[] storage(M3ArrayList<?> list) throws Exception {
        Field field = M3ArrayList.class.getDeclaredField("elements");
        field.setAccessible(true);
        return (Object[]) field.get(list);
    }
}

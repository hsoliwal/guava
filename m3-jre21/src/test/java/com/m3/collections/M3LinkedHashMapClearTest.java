// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.collections;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Clears release payload references while retaining reusable slot ownership. */
public final class M3LinkedHashMapClearTest {
    @Test public void clearSparseDenseAndEmptyOwnersKeepsViewAndInsertionContracts() throws Exception {
        for (boolean accessOrder : new boolean[] {false, true}) {
            for (int remaining : new int[] {0, 1, 7, 8, 9, 64, 511, 512}) {
                M3LinkedHashMap<Integer, Object> map = new M3LinkedHashMap<>(accessOrder);
                LinkedHashMap<Integer, Object> oracle = new LinkedHashMap<>(16, 0.75f, accessOrder);
                for (int key = 0; key < 512; key++) {
                    Object value = new Object();
                    map.put(key, value);
                    oracle.put(key, value);
                }
                for (int key = 0; key < 512 - remaining; key++) {
                    assertSame(oracle.remove(key), map.remove(key));
                }
                if (remaining > 1) { map.get(512 - remaining); oracle.get(512 - remaining); }
                map.verifyInvariants();
                Object table = field(map, "table");
                Object[] keys = (Object[]) field(table, "keys");
                Object[] values = (Object[]) field(table, "values");
                int[] index = (int[]) field(table, "index");
                int[] oldIndex = index.clone();
                List<Integer> before = new ArrayList<>(map.keySet());
                Iterator<Integer> cursor = map.keySet().iterator();
                M3LinkedHashMap<Integer, Object> reversed = map.reversed();
                Map.Entry<Integer, Object> entry = map.firstEntry();
                map.clear();
                oracle.clear();
                assertEquals(oracle, map);
                assertTrue(reversed.isEmpty());
                if (!before.isEmpty()) {
                    assertThrows(ConcurrentModificationException.class, cursor::next);
                } else {
                    assertFalse(cursor.hasNext());
                }
                assertSame(keys, field(table, "keys"));
                assertSame(values, field(table, "values"));
                assertSame(index, field(table, "index"));
                for (Object value : keys) assertNull(value);
                for (Object value : values) assertNull(value);
                if (remaining == 0) {
                    // Empty clear keeps its existing no-op, including tombstones.
                    assertArrayEquals(oldIndex, index);
                } else {
                    for (int bucket : index) assertEquals(0, bucket);
                }
                map.verifyInvariants();
                // Endpoint entries are detached compatibility snapshots.
                if (entry != null) assertNotNull(entry.getValue());
                for (int key : new int[] {7, 0, 19, -1}) {
                    Object value = new Object();
                    map.put(key, value); oracle.put(key, value);
                }
                assertEquals(oracle, map);
                assertEquals(new ArrayList<>(oracle.keySet()), new ArrayList<>(map.keySet()));
                assertEquals(List.of(-1, 19, 0, 7), new ArrayList<>(reversed.keySet()));
                map.verifyInvariants();
            }
        }
    }

    @Test public void sparseKeyOnlyOwnerAndReversedClearReleaseTheSamePayload() throws Exception {
        M3LinkedHashMap<Integer, Integer> map = new M3LinkedHashMap<>(false, true);
        for (int key = 0; key < 512; key++) map.put(key, key);
        for (int key = 0; key < 511; key++) map.remove(key);
        Object table = field(map, "table");
        Object[] keys = (Object[]) field(table, "keys");
        Object[] values = (Object[]) field(table, "values");
        assertEquals(0, values.length);
        map.reversed().clear();
        assertTrue(map.isEmpty());
        assertSame(keys, field(table, "keys"));
        assertSame(values, field(table, "values"));
        for (Object key : keys) assertNull(key);
        map.verifyInvariants();
        map.put(42, 42);
        assertTrue(map.containsKey(42));
        assertNull(map.get(42));
        map.verifyInvariants();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}

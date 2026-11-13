/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections.guava.collect;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Independent HashMap reference oracle for the production array key-directory. */
public final class ArrayKeyDirectoryModelProof {
    private static long checks;

    private static final class Key {
        private final int id;
        private final boolean collision;

        Key(int id, boolean collision) {
            this.id = id;
            this.collision = collision;
        }

        @Override
        public int hashCode() {
            return collision ? id % 13 : id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && ((Key) other).id == id;
        }
    }

    private static void require(boolean actual, String reason) {
        checks++;
        if (!actual) {
            throw new AssertionError(reason + " check=" + checks);
        }
    }

    public static void main(String[] arguments) {
        for (int seed = 0; seed < 24; seed++) {
            Random random = new Random(0x4d334b4559524452L ^ seed);
            ArrayKeyDirectory<Key, Object> actual = new ArrayKeyDirectory<>(seed % 9);
            Map<Key, Object> reference = new HashMap<>();
            Object[] values = new Object[103];
            for (int index = 0; index < values.length; index++) {
                values[index] = new Object();
            }
            for (int step = 0; step < 50000; step++) {
                Key key = random.nextInt(30) == 0 ? null
                        : new Key(random.nextInt(8192), seed % 2 == 0);
                int operation = random.nextInt(10);
                if (operation < 4) {
                    Object value = values[random.nextInt(values.length)];
                    require(actual.put(key, value) == reference.put(key, value), "put");
                } else if (operation < 6) {
                    require(actual.remove(key) == reference.remove(key), "remove");
                } else if (operation == 6) {
                    actual.clear();
                    reference.clear();
                } else if (operation == 7) {
                    require(actual.containsKey(key) == reference.containsKey(key), "contains");
                } else if (operation == 8) {
                    require(actual.get(key) == reference.get(key), "get");
                } else {
                    require(actual.size() == reference.size(), "reported size");
                }
                require(actual.size() == reference.size(), "size after operation");
                if ((step & 31) == 0) {
                    for (Key candidate : reference.keySet()) {
                        require(actual.containsKey(candidate), "missing key");
                        require(actual.get(candidate) == reference.get(candidate), "wrong value");
                    }
                }
            }
        }
        System.out.println("PASS assertions=" + checks);
    }
}

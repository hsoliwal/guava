/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections.guava.collect;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Independent entry-view oracle, callback contracts and projection allocation guard. */
public final class M3CompactMapHashCodeTest extends junit.framework.TestCase {
  private static int checks;

  private static CompactHashMap<Object, Object> create(int flavor) {
    switch (flavor) {
      case 0: return CompactHashMap.create();
      case 1: return new CompactLinkedHashMap<>(4, false);
      case 2: return new CompactLinkedHashMap<>(4, true);
      case 3:
        CompactHashMap<Object, Object> map = CompactHashMap.create();
        map.convertToHashFloodingResistantImplementation();
        return map;
      default: throw new AssertionError(flavor);
    }
  }

  private static void equal(Object expected, Object actual) {
    checks++;
    if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + " actual=" + actual);
  }

  private static void randomOperations() {
    for (int flavor = 0; flavor < 4; flavor++) {
      CompactHashMap<Object, Object> map = create(flavor);
      Map<Object, Object> oracle = new java.util.HashMap<>();
      Random random = new Random(349829L + flavor);
      for (int i = 0; i < 30_000; i++) {
        Object key = random.nextInt(12) == 0 ? null : random.nextInt(128);
        Object value = random.nextInt(12) == 0 ? null : random.nextInt();
        switch (random.nextInt(7)) {
          case 0: case 1: case 2: equal(String.valueOf(oracle.put(key, value)), String.valueOf(map.put(key, value))); break;
          case 3: equal(String.valueOf(oracle.remove(key)), String.valueOf(map.remove(key))); break;
          case 4: equal(String.valueOf(oracle.get(key)), String.valueOf(map.get(key))); break;
          case 5: if (random.nextInt(32) == 0) { map.clear(); oracle.clear(); } break;
          case 6: map.trimToSize(); break;
          default: throw new AssertionError();
        }
        equal(oracle.hashCode(), map.hashCode());
        equal(map.entrySet().hashCode(), map.hashCode());
      }
    }
  }

  private static final class Fixture {
    final CompactHashMap<Object, Object> map;
    final List<String> trace = new ArrayList<>();
    final Atom[] keys;
    final Atom[] values;
    final int target;
    final int action;
    final boolean onValue;
    boolean armed;
    boolean acted;
    Fixture(int flavor, int size, int target, int action, boolean onValue) {
      this.map = create(flavor); this.target = target; this.action = action; this.onValue = onValue;
      keys = new Atom[size]; values = new Atom[size];
      for (int i = 0; i < size; i++) { keys[i] = new Atom(this, i, false); values[i] = new Atom(this, i, true); map.put(keys[i], values[i]); }
      armed = true;
    }
    void callback(Atom atom) {
      if (!armed) return;
      trace.add((atom.value ? "v" : "k") + atom.id);
      if (acted || atom.value != onValue || atom.id != target) return;
      acted = true;
      switch (action) {
        case 0: break;
        case 1: map.put(keys[target], 71); break;
        case 2: map.put(900, 42); break;
        case 3: map.remove(keys[target]); break;
        case 4: map.clear(); break;
        case 5: map.convertToHashFloodingResistantImplementation(); break;
        case 6: assertSame(values[0], map.get(keys[0])); break;
        case 7: throw new IllegalStateException("callback failure");
        case 8: map.remove(keys[target]); map.put(keys[target], 72); break;
        case 9: map.trimToSize(); break;
        default: throw new AssertionError();
      }
    }
    String run(boolean throughEntryView) {
      String result;
      try { result = "hash=" + (throughEntryView ? map.entrySet().hashCode() : map.hashCode()); }
      catch (RuntimeException error) { result = error.getClass().getName() + ":" + error.getMessage(); }
      armed = false;
      StringBuilder entries = new StringBuilder();
      for (Map.Entry<Object, Object> entry : map.entrySet()) entries.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
      return result + ":" + trace + ":" + entries;
    }
  }
  private static final class Atom {
    final Fixture fixture; final int id; final boolean value;
    Atom(Fixture fixture, int id, boolean value) { this.fixture = fixture; this.id = id; this.value = value; }
    @Override public int hashCode() { fixture.callback(this); return id * (value ? 17 : 31); }
    @Override public boolean equals(Object other) { return this == other; }
    @Override public String toString() { return (value ? "v" : "k") + id; }
  }

  private static void callbackContracts() {
    for (int flavor = 0; flavor < 4; flavor++) {
      for (int size : new int[] {1, 2, 5}) {
        for (int target = 0; target < size; target++) {
          for (int action = 0; action < 10; action++) {
            for (boolean value : new boolean[] {false, true}) {
              String expected = new Fixture(flavor, size, target, action, value).run(true);
              String actual = new Fixture(flavor, size, target, action, value).run(false);
              equal(expected, actual);
            }
          }
        }
      }
    }
  }

  private static void subclassContract() {
    CompactHashMap<Object, Object> map = new CompactHashMap<Object, Object>() {
      @Override public Set<Map.Entry<Object, Object>> entrySet() {
        return Collections.singleton(new AbstractMap.SimpleImmutableEntry<>("custom", 82));
      }
    };
    map.put("stored", 52);
    equal(map.entrySet().hashCode(), map.hashCode());
    map.convertToHashFloodingResistantImplementation();
    equal(map.entrySet().hashCode(), map.hashCode());
  }

  public void testRandomOperationsPreserveMapHashCode() { randomOperations(); }

  public void testHashCallbackMutationAndExceptionContracts() { callbackContracts(); }

  public void testSubclassEntrySetOverrideRemainsAuthoritative() { subclassContract(); }
}

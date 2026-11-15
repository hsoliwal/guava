/*
 * Copyright (C) 2026 The Guava Authors
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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import junit.framework.TestCase;
import org.jspecify.annotations.Nullable;

/** Tests array traversal through ArrayTable's live map views. */
public final class ArrayTableArrayTraversalTest extends TestCase {

  private static ArrayTable<String, Integer, String> table() {
    ArrayTable<String, Integer, String> table =
        ArrayTable.create(asList("r0", "r1", "r2"), asList(3, 1, 2));
    table.put("r0", 3, "a");
    table.put("r0", 2, "c");
    table.put("r1", 3, "d");
    table.put("r1", 1, "e");
    table.put("r1", 2, "f");
    table.put("r2", 1, "h");
    table.put("r2", 2, "i");
    return table;
  }

  public void testContainsValueUsesQueryEqualityInOrder() {
    Map<Integer, @Nullable String> row = table().row("r0");
    List<@Nullable Object> visited = new ArrayList<>();
    Object query = new EqualityQuery(visited, null);
    assertTrue(row.containsValue(query));
    assertEquals(asList("a", null), visited);
    visited.clear();
    assertTrue(row.values().contains(query));
    assertEquals(asList("a", null), visited);
  }

  public void testContainsNullDoesNotCallStoredEquality() {
    ArrayTable<String, Integer, Object> table =
        ArrayTable.create(asList("r"), asList(0, 1));
    table.put("r", 0, new Object() {
      @Override
      public boolean equals(@Nullable Object other) {
        throw new AssertionError("stored value equality must not be called");
      }

      @Override
      public int hashCode() {
        return 0;
      }
    });
    assertTrue(table.row("r").containsValue(null));
    assertFalse(table.column(0).containsValue(null));
    assertTrue(table.column(1).containsValue(null));
  }

  public void testContainsValueDoesNotSkipQueryCallbackForIdenticalReference() {
    List<@Nullable Object> visited = new ArrayList<>();
    Object query = new EqualityQuery(visited, "not present");
    ArrayTable<String, Integer, Object> table =
        ArrayTable.create(asList("r"), asList(0));
    table.put("r", 0, query);
    assertFalse(table.row("r").containsValue(query));
    assertEquals(1, visited.size());
    assertSame(query, visited.get(0));
  }

  public void testContainsValueReadsReentrantChanges() {
    Map<Integer, @Nullable String> row = table().row("r0");
    List<@Nullable Object> visited = new ArrayList<>();
    Object query = new EqualityQuery(visited, "changed") {
      @Override
      public boolean equals(@Nullable Object candidate) {
        row.put(1, "changed");
        return super.equals(candidate);
      }
    };
    assertTrue(row.containsValue(query));
    assertEquals(asList("a", "changed"), visited);
  }

  public void testForEachRowPreservesOrderAndLiveReads() {
    Map<Integer, @Nullable String> row = table().row("r0");
    List<String> visited = new ArrayList<>();
    row.forEach((key, value) -> {
      visited.add(key + ":" + value);
      row.put(1, "changed");
    });
    assertEquals(asList("3:a", "1:changed", "2:c"), visited);
  }

  public void testForEachColumnPreservesOrderAndNulls() {
    ArrayTable<String, Integer, String> table = table();
    List<String> visited = new ArrayList<>();
    table.column(3).forEach((key, value) -> {
      visited.add(key + ":" + value);
      table.put("r1", 3, "changed");
    });
    assertEquals(asList("r0:a", "r1:changed", "r2:null"), visited);
  }

  public void testOuterMapForEachReturnsLiveViewsInOrder() {
    ArrayTable<String, Integer, String> table = table();
    List<String> rows = new ArrayList<>();
    table.rowMap().forEach((key, value) -> {
      rows.add(key);
      value.put(3, key);
    });
    assertEquals(asList("r0", "r1", "r2"), rows);
    assertEquals(asList("r0", "r1", "r2"), new ArrayList<>(table.column(3).values()));
    List<Integer> columns = new ArrayList<>();
    table.columnMap().forEach((key, value) -> {
      columns.add(key);
      value.put("r1", "column" + key);
    });
    assertEquals(asList(3, 1, 2), columns);
    assertEquals(asList("column3", "column1", "column2"),
        new ArrayList<>(table.row("r1").values()));
  }

  public void testForEachPropagatesCallbackExceptionUnchanged() {
    Map<Integer, @Nullable String> row = table().row("r0");
    IllegalStateException failure = new IllegalStateException("callback");
    List<Integer> visited = new ArrayList<>();
    assertSame(failure, assertThrows(IllegalStateException.class, () ->
        row.forEach((key, value) -> {
          visited.add(key);
          if (key == 1) {
            throw failure;
          }
        })));
    assertEquals(asList(3, 1), visited);
  }

  public void testReplaceAllReadsLiveValuesAndWritesAfterCallback() {
    Map<Integer, @Nullable String> row = table().row("r0");
    List<String> visited = new ArrayList<>();
    row.replaceAll((key, value) -> {
      visited.add(key + ":" + value);
      if (key == 3) {
        row.put(3, "overwritten");
        row.put(1, "changed");
      }
      return value + "!";
    });
    assertEquals(asList("3:a", "1:changed", "2:c"), visited);
    assertEquals(asList("a!", "changed!", "c!"), new ArrayList<>(row.values()));
  }

  public void testReplaceAllColumnAllowsNullResults() {
    ArrayTable<String, Integer, String> table = table();
    List<String> visited = new ArrayList<>();
    table.column(3).replaceAll((key, value) -> {
      visited.add(key + ":" + value);
      return null;
    });
    assertEquals(asList("r0:a", "r1:d", "r2:null"), visited);
    assertEquals(asList(null, null, null), new ArrayList<>(table.column(3).values()));
  }

  public void testReplaceAllKeepsPartialUpdatesWhenCallbackThrows() {
    Map<String, @Nullable String> column = table().column(3);
    IllegalStateException failure = new IllegalStateException("callback");
    List<String> visited = new ArrayList<>();
    assertSame(failure, assertThrows(IllegalStateException.class, () ->
        column.replaceAll((key, value) -> {
          visited.add(key);
          if (key.equals("r1")) {
            throw failure;
          }
          return "changed";
        })));
    assertEquals(asList("r0", "r1"), visited);
    assertEquals(asList("changed", "d", null), new ArrayList<>(column.values()));
  }

  public void testOuterMapReplaceAllRetainsCallbackBeforeUnsupportedWrite() {
    ArrayTable<String, Integer, String> table = table();
    List<String> visited = new ArrayList<>();
    assertThrows(UnsupportedOperationException.class, () ->
        table.rowMap().replaceAll((key, value) -> {
          visited.add(key);
          value.put(3, "changed");
          return value;
        }));
    assertEquals(asList("r0"), visited);
    assertEquals("changed", table.get("r0", 3));
    List<Integer> columns = new ArrayList<>();
    assertThrows(UnsupportedOperationException.class, () ->
        table.columnMap().replaceAll((key, value) -> {
          columns.add(key);
          value.put("r1", "changed");
          return value;
        }));
    assertEquals(asList(3), columns);
    assertEquals("changed", table.get("r1", 3));
  }

  public void testEmptyMapsAndNullCallbacks() {
    ArrayTable<String, Integer, String> empty =
        ArrayTable.create(Collections.<String>emptyList(), Collections.<Integer>emptyList());
    assertFalse(empty.rowMap().containsValue(null));
    assertFalse(empty.columnMap().containsValue(null));
    empty.rowMap().forEach((key, value) -> fail("empty view"));
    empty.columnMap().replaceAll((key, value) -> {
      fail("empty view");
      return value;
    });
    assertThrows(NullPointerException.class, () -> empty.rowMap().forEach(null));
    assertThrows(NullPointerException.class, () -> empty.columnMap().replaceAll(null));
    assertThrows(NullPointerException.class, () -> table().row("r0").forEach(null));
    assertThrows(NullPointerException.class, () -> table().column(3).replaceAll(null));
  }

  public void testEntryProjectionsRemainLiveAfterBulkUpdates() {
    Map<Integer, @Nullable String> row = table().row("r0");
    Map.Entry<Integer, @Nullable String> entry = row.entrySet().iterator().next();
    row.replaceAll((key, value) -> "replaced");
    assertEquals("replaced", entry.getValue());
    assertEquals("replaced", entry.setValue("throughEntry"));
    assertEquals("throughEntry", row.get(3));
    assertTrue(row.containsValue("throughEntry"));
  }

  public void testBulkOperationsMatchEntryBasedReference() {
    Random random = new Random(0x4a771eL);
    for (int trial = 0; trial < 300; trial++) {
      ArrayTable<String, Integer, String> actual = table();
      ArrayTable<String, Integer, String> reference = table();
      for (int i = 0; i < 9; i++) {
        String value = random.nextBoolean() ? null : "v" + random.nextInt(4);
        actual.set(i / 3, i % 3, value);
        reference.set(i / 3, i % 3, value);
      }
      Map<Integer, @Nullable String> row = actual.row("r1");
      Map<Integer, @Nullable String> expected = entryBased(reference.row("r1"));
      for (String query : asList(null, "v0", "v1", "v2", "v3", "missing")) {
        assertEquals(expected.containsValue(query), row.containsValue(query));
      }
      List<String> actualCalls = new ArrayList<>();
      List<String> expectedCalls = new ArrayList<>();
      row.forEach((key, value) -> {
        actualCalls.add(key + ":" + value);
        actual.put("r1", 2, value);
      });
      expected.forEach((key, value) -> {
        expectedCalls.add(key + ":" + value);
        reference.put("r1", 2, value);
      });
      assertEquals(expectedCalls, actualCalls);
      row.replaceAll((key, value) -> key == 1 ? null : "new:" + value);
      expected.replaceAll((key, value) -> key == 1 ? null : "new:" + value);
      assertEquals(reference, actual);
    }
  }

  private static <K, V> Map<K, V> entryBased(Map<K, V> map) {
    return new AbstractMap<K, V>() {
      @Override
      public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
      }
    };
  }

  private static class EqualityQuery {
    final List<@Nullable Object> visited;
    final @Nullable Object target;

    EqualityQuery(List<@Nullable Object> visited, @Nullable Object target) {
      this.visited = visited;
      this.target = target;
    }

    @Override
    public boolean equals(@Nullable Object candidate) {
      visited.add(candidate);
      return Objects.equals(target, candidate);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(target);
    }
  }
}

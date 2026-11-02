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

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.m3.collections.guava.annotations.GwtIncompatible;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import junit.framework.TestCase;

/** Native JVM contracts for the row, column, and outer array-map values views. */
@GwtIncompatible
public class M3ArrayTableValuesProjectionTest extends TestCase {
  private static volatile Object observed;

  public void testValuesTraversalDoesNotAllocatePerElementEntries() {
    assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
    com.sun.management.ThreadMXBean bean =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    assumeTrue(bean.isThreadAllocatedMemorySupported());
    bean.setThreadAllocatedMemoryEnabled(true);
    int count = 4096;
    List<Integer> columns = new ArrayList<>();
    for (int column = 0; column < count; column++) {
      columns.add(column);
    }
    ArrayTable<Integer, Integer, String> rowTable =
        ArrayTable.create(Collections.singletonList(0), columns);
    ArrayTable<Integer, Integer, String> columnTable =
        ArrayTable.create(columns, Collections.singletonList(0));
    for (Collection<String> values :
        Arrays.asList(rowTable.row(0).values(), columnTable.column(0).values())) {
      for (int warmup = 0; warmup < 5; warmup++) {
        consume(values);
      }
      long thread = Thread.currentThread().getId();
      bean.getThreadAllocatedBytes(thread);
      long before = bean.getThreadAllocatedBytes(thread);
      consume(values);
      long allocated = bean.getThreadAllocatedBytes(thread) - before;
      System.out.println("array-map values: count=" + count + ", allocatedBytes=" + allocated);
      assertTrue("per-element entry projection allocated bytes=" + allocated, allocated < count * 8L);
    }
  }

  private static void consume(Collection<?> values) {
    for (Object value : values) {
      observed = value;
    }
  }

  public void testValuesOrderAndLiveUpdates() {
    ArrayTable<String, Integer, String> table =
        ArrayTable.create(Arrays.asList("r2", "r1"), Arrays.asList(3, 1, 2));
    table.put("r2", 3, "first");
    table.put("r2", 2, "last");
    Map<Integer, String> row = table.row("r2");
    Collection<String> values = row.values();
    assertSame(values, row.values());
    assertEquals(Arrays.asList("first", null, "last"), new ArrayList<>(values));
    Iterator<String> iterator = values.iterator();
    assertEquals("first", iterator.next());
    table.put("r2", 1, "changed");
    assertEquals("changed", iterator.next());
    assertEquals("last", iterator.next());
    assertFalse(iterator.hasNext());
    assertThrows(NoSuchElementException.class, iterator::next);
    assertEquals(Arrays.asList("first", null), new ArrayList<>(table.column(3).values()));
    List<String> visited = new ArrayList<>();
    values.forEach(visited::add);
    assertEquals(new ArrayList<>(values), visited);
    assertEquals(new ArrayList<>(values), Arrays.asList(values.toArray(new String[0])));
  }

  public void testUnsupportedMutationsAndAbsentRemovalMatchMapView() {
    ArrayTable<String, Integer, String> table =
        ArrayTable.create(Collections.singletonList("row"), Arrays.asList(1, 2));
    Collection<String> values = table.row("row").values();
    Iterator<String> iterator = values.iterator();
    assertThrows(UnsupportedOperationException.class, iterator::remove);
    assertNull(iterator.next());
    assertThrows(UnsupportedOperationException.class, iterator::remove);
    assertThrows(UnsupportedOperationException.class, values::clear);
    assertThrows(UnsupportedOperationException.class, () -> values.add("new"));
    assertFalse(values.remove("absent"));
    assertFalse(values.removeAll(Collections.singleton("absent")));
    assertFalse(values.retainAll(Collections.singleton(null)));
    assertThrows(UnsupportedOperationException.class, () -> values.remove(null));
    assertThrows(UnsupportedOperationException.class, () -> values.removeIf(value -> value == null));
    assertThrows(NullPointerException.class, () -> values.forEach(null));
  }

  public void testOuterValuesRemainFreshViewsAndEntriesRemainIndependent() {
    ArrayTable<String, Integer, String> table =
        ArrayTable.create(Arrays.asList("a", "b"), Arrays.asList(1, 2));
    Map<String, Map<Integer, String>> rows = table.rowMap();
    Collection<Map<Integer, String>> values = rows.values();
    Map<Integer, String> first = values.iterator().next();
    Map<Integer, String> second = values.iterator().next();
    assertNotSame(first, second);
    first.put(1, "live");
    assertEquals("live", second.get(1));
    assertEquals(table.row("a"), first);
    assertEquals(Arrays.asList(table.row("a"), table.row("b")), new ArrayList<>(values));
    assertEquals(Arrays.asList(table.column(1), table.column(2)),
        new ArrayList<>(table.columnMap().values()));
    Iterator<Map.Entry<Integer, String>> entries = first.entrySet().iterator();
    Map.Entry<Integer, String> firstEntry = entries.next();
    Map.Entry<Integer, String> secondEntry = entries.next();
    assertNotSame(firstEntry, secondEntry);
    secondEntry.setValue("two");
    assertEquals("live", firstEntry.getValue());
    assertEquals("two", table.get("a", 2));
  }

  public void testSerializationRetainsLogicalContents() throws Exception {
    ArrayTable<String, Integer, String> table =
        ArrayTable.create(Arrays.asList("a", "b"), Arrays.asList(1, 2));
    table.put("a", 1, "saved");
    table.row("a").values().iterator().next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(table);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertEquals(table, input.readObject());
    }
  }
}

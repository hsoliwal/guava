// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.google.common.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import org.junit.Test;

/** Storage discriminators complement the unchanged 6,064-case Guava behavior suite. */
public final class M3NativeStorageTest {
  private static final String[] LANES =
      {"keys", "values", "hashes", "before", "after", "positions", "index"};

  @Test public void exactNativeOwnersAndExtremeKeyHintsRemainLazy() throws Exception {
    assertCodeSource(ArrayListMultimap.class);
    for (int hint : new int[] {0, 22, Integer.MAX_VALUE}) {
      ArrayListMultimap<String, String> map = ArrayListMultimap.create(hint, 0);
      Object owner = backing(map);
      assertEquals("com.m3.collections.M3LinkedHashMap", owner.getClass().getName());
      assertCodeSource(owner.getClass());
      for (Object lane : lanes(owner)) assertEquals(0, Array.getLength(lane));
      Object values = invoke(map, "createCollection");
      assertEquals("com.m3.collections.M3ArrayList", values.getClass().getName());
      assertCodeSource(values.getClass());
      assertTrue(values instanceof RandomAccess);
      assertEquals(0, Array.getLength(field(values, "elements")));
      assertTrue(map.get("absent").isEmpty());
      assertFalse(map.containsKey("absent"));
      for (Object lane : lanes(owner)) assertEquals(0, Array.getLength(lane));
    }
  }

  @Test public void sizedFactoryReservesActualKeyAndValueArrays() throws Exception {
    ArrayListMultimap<Integer, Integer> map = ArrayListMultimap.create(22, 7);
    Object owner = backing(map);
    for (Object lane : lanes(owner)) assertEquals(0, Array.getLength(lane));
    map.put(0, 0);
    Object[] reserved = lanes(owner);
    assertEquals(Object[].class, reserved[0].getClass());
    assertEquals(Object[].class, reserved[1].getClass());
    for (int i = 2; i < reserved.length; i++) assertEquals(int[].class, reserved[i].getClass());
    assertTrue(Array.getLength(reserved[0]) >= 22);
    Object firstValues = backing(map).get(0);
    assertEquals("com.m3.collections.M3ArrayList", firstValues.getClass().getName());
    Object elements = field(firstValues, "elements");
    assertEquals(7, Array.getLength(elements));
    for (int i = 1; i < 22; i++) {
      map.put(i, i);
      Object[] current = lanes(owner);
      for (int j = 0; j < LANES.length; j++) assertSame(LANES[j], reserved[j], current[j]);
    }
    for (int value = 1; value < 7; value++) {
      map.put(0, value);
      assertSame(elements, field(firstValues, "elements"));
    }
    assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), map.get(0));
    assertEquals(22, map.keySet().size());
  }

  @SuppressWarnings("deprecation")
  @Test public void noOpTrimInvalidatesIteratorsWithoutReplacingPayload() throws Exception {
    ArrayListMultimap<String, String> map = ArrayListMultimap.create(1, 2);
    map.putAll("key", Arrays.asList("first", "second"));
    List<String> live = map.get("key");
    Object values = backing(map).get("key");
    Object elements = field(values, "elements");
    assertEquals(2, Array.getLength(elements));
    Iterator<String> iterator = live.iterator();
    List<String> subList = live.subList(0, 1);
    assertEquals("first", iterator.next());
    map.trimToSize();
    assertSame(elements, field(values, "elements"));
    assertThrows(ConcurrentModificationException.class, iterator::next);
    assertThrows(ConcurrentModificationException.class, subList::size);
    Iterator<String> again = live.iterator();
    assertEquals("first", again.next());
    map.trimToSize();
    assertSame(elements, field(values, "elements"));
    assertThrows(ConcurrentModificationException.class, again::next);
    assertEquals(Arrays.asList("first", "second"), live);
  }

  @Test public void serializationAndLiveViewsPreserveNullsDuplicatesAndUid() throws Exception {
    assertEquals(0L, ObjectStreamClass.lookup(ArrayListMultimap.class).getSerialVersionUID());
    assertEquals(0, ObjectStreamClass.lookup(ArrayListMultimap.class).getFields().length);
    ArrayListMultimap<String, String> map = ArrayListMultimap.create(8, 3);
    List<String> live = map.get("key");
    live.addAll(Arrays.asList(null, "duplicate", "duplicate"));
    map.put(null, null);
    assertTrue(live instanceof RandomAccess);
    assertTrue(live.subList(0, 2) instanceof RandomAccess);
    List<String> previous = map.replaceValues("key", Arrays.asList("next", "next", null));
    assertTrue(previous instanceof RandomAccess);
    assertEquals(Arrays.asList(null, "duplicate", "duplicate"), previous);
    assertThrows(UnsupportedOperationException.class, () -> previous.add("forbidden"));
    assertEquals(Arrays.asList("next", "next", null), live);
    List<String> removed = map.removeAll("key");
    assertTrue(removed instanceof RandomAccess);
    assertEquals(Arrays.asList("next", "next", null), removed);
    assertTrue(live.isEmpty());
    assertThrows(UnsupportedOperationException.class, removed::clear);
    live.addAll(Arrays.asList("returned", null, "returned"));
    assertEquals(Arrays.asList("next", "next", null), removed);
    ArrayListMultimap<String, String> restored = roundTrip(map);
    assertEquals(map, restored);
    assertEquals("com.m3.collections.M3LinkedHashMap", backing(restored).getClass().getName());
    assertEquals("com.m3.collections.M3ArrayList", backing(restored).get("key").getClass().getName());
    assertEquals(Arrays.asList("returned", null, "returned"), restored.get("key"));
    assertTrue(restored.containsEntry(null, null));
    List<String> restoredLive = restored.get("key");
    restored.removeAll("key");
    assertTrue(restoredLive.isEmpty());
    restoredLive.add("reattached");
    assertEquals(Arrays.asList("reattached"), restored.get("key"));
  }

  private static void assertCodeSource(Class<?> type) throws Exception {
    String expected = System.getProperty("m3.native.outputDirectory");
    assertTrue("m3.native.outputDirectory must bind the actual native output", expected != null);
    assertEquals(Path.of(expected).toRealPath(), Path.of(type.getProtectionDomain()
        .getCodeSource().getLocation().toURI()).toRealPath());
  }

  private static Map<?, ?> backing(ArrayListMultimap<?, ?> map) throws Exception {
    return (Map<?, ?>) invoke(map, "backingMap");
  }

  private static Object invoke(Object owner, String name) throws Exception {
    for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
      } catch (NoSuchMethodException absent) {
        // Continue to the actual inherited Guava method owner.
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static Object field(Object owner, String name) throws Exception {
    for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
      } catch (NoSuchFieldException absent) {
        // Continue to the actual inherited M3 storage owner.
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static Object[] lanes(Object map) throws Exception {
    Object table = field(map, "table");
    Object[] result = new Object[LANES.length];
    for (int i = 0; i < LANES.length; i++) result[i] = field(table, LANES[i]);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static ArrayListMultimap<String, String> roundTrip(ArrayListMultimap<String, String> map)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes) {
      @Override protected void annotateClass(Class<?> type) throws IOException {
        if (type.getName().startsWith("com.m3.collections."))
          throw new IOException("private M3 storage descriptor escaped: " + type.getName());
      }
    }) {
      output.writeObject(map);
    }
    assertFalse(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
        .contains("com.m3.collections."));
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ArrayListMultimap<String, String>) input.readObject();
    }
  }
}

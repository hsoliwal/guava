/*
 * Copyright 2026 Hitesh Soliwal and Contributors to the Synexia Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** Physical discriminators and comparisons against separately compiled, unchanged JRE owners. */
public final class M3ArrayStorageContractTest {
  private static final String PACKAGE = "com.m3.collections.guava.collect.";

  @Test
  public void retainedStorageUsesArraysAndPrimitiveCounts() throws Exception {
    HashBiMap<Object, Object> bimap = HashBiMap.create(0);
    for (int index = 0; index < 400; index++) {
      bimap.put(new CollisionKey(index), new CollisionKey(index + 400));
    }
    assertTrue(field(bimap, "keys") instanceof Object[]);
    assertTrue(field(bimap, "values") instanceof Object[]);
    for (String name :
        Arrays.asList(
            "hashTableKToV",
            "hashTableVToK",
            "nextInBucketKToV",
            "nextInBucketVToK",
            "prevInInsertionOrder",
            "nextInInsertionOrder")) {
      assertTrue(name, field(bimap, name) instanceof int[]);
    }
    for (Class<?> nested : HashBiMap.class.getDeclaredClasses()) {
      assertFalse(
          "Retained JRE per-mapping Node must be gone", nested.getSimpleName().equals("Node"));
    }
    for (Field candidate : HashBiMap.class.getDeclaredFields()) {
      if (!candidate.getName().equals("inverse")) {
        assertFalse(
            "Bimap must own array storage directly",
            Map.class.isAssignableFrom(candidate.getType()));
      }
    }
    for (Multiset<Object> multiset :
        Arrays.asList(HashMultiset.create(), LinkedHashMultiset.create())) {
      for (int index = 0; index < 400; index++) {
        multiset.add(new CollisionKey(index), index + 1);
      }
      Object owner = field(multiset, "backingMap");
      assertTrue(owner instanceof ObjectCountHashMap);
      assertTrue(field(owner, "keys") instanceof Object[]);
      assertTrue(field(owner, "values") instanceof int[]);
      assertTrue(field(owner, "entries") instanceof long[]);
      assertTrue(field(owner, "table") instanceof int[]);
      assertFalse(owner instanceof Map);
      if (multiset instanceof LinkedHashMultiset) {
        assertTrue(field(owner, "links") instanceof long[]);
      }
      for (Object retained : (Object[]) field(owner, "keys")) {
        assertTrue(retained == null || retained instanceof CollisionKey);
      }
    }
  }

  @Test
  public void platformUsesExistingCompactOwnersIncludingReadObject() throws Exception {
    assertTrue(Platform.newHashMapWithExpectedSize(0) instanceof CompactHashMap);
    assertTrue(Platform.newLinkedHashMapWithExpectedSize(0) instanceof CompactLinkedHashMap);
    assertTrue(Platform.newHashSetWithExpectedSize(0) instanceof CompactHashSet);
    assertTrue(Platform.newLinkedHashSetWithExpectedSize(0) instanceof CompactLinkedHashSet);
    assertTrue(Platform.preservesInsertionOrderOnPutsMap() instanceof CompactHashMap);
    assertTrue(
        Platform.preservesInsertionOrderOnPutsMapWithExpectedSize(0)
            instanceof CompactLinkedHashMap);
    ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
    multimap.put("first", "a");
    multimap.put("first", "a");
    multimap.put(null, null);
    assertTrue(multimap.backingMap() instanceof CompactHashMap);
    Object copy = read(write(multimap), getClass().getClassLoader());
    assertEquals(multimap, copy);
    assertTrue(((ArrayListMultimap<?, ?>) copy).backingMap() instanceof CompactHashMap);

    Map<CollisionKey, Integer> flooded = Platform.newLinkedHashMapWithExpectedSize(0);
    Set<CollisionKey> floodedSet = Platform.newLinkedHashSetWithExpectedSize(0);
    for (int index = 0; index < 200; index++) {
      flooded.put(new CollisionKey(index), index);
      floodedSet.add(new CollisionKey(index));
    }
    assertNotNull(((CompactHashMap<?, ?>) flooded).delegateOrNull());
    assertNotNull(((CompactHashSet<?>) floodedSet).delegateOrNull());
    assertEquals(new ArrayList<>(flooded.keySet()), new ArrayList<>(floodedSet));
    for (int index = 0; index < 200; index++) {
      assertEquals(Integer.valueOf(index), flooded.get(new CollisionKey(index)));
    }
  }

  @Test
  public void bimapMatchesOriginalAcrossSeededCollisionNullAndViewEdits() throws Exception {
    try (URLClassLoader oracle = originalLoader()) {
      Map<Object, Object> original = map(oracle, "HashBiMap");
      Map<Object, Object> candidate = HashBiMap.create(0);
      Random random = new Random(0x4D334152524159L);
      for (int step = 0; step < 12000; step++) {
        int operation = random.nextInt(12);
        Object key = random.nextInt(9) == 0 ? null : new CollisionKey(random.nextInt(50));
        Object value = random.nextInt(9) == 0 ? null : new CollisionKey(random.nextInt(50) + 100);
        boolean removeAfterSet = random.nextBoolean();
        Object expected = outcome(() -> editMap(original, operation, key, value, removeAfterSet));
        Object actual = outcome(() -> editMap(candidate, operation, key, value, removeAfterSet));
        assertEquals("result at step " + step + " operation " + operation, expected, actual);
        assertEquals("ordered mappings at step " + step, entries(original), entries(candidate));
        assertEquals(
            "inverse at step " + step, entries(inverse(original)), entries(inverse(candidate)));
        assertSame(candidate, inverse(inverse(candidate)));
      }
    }
  }

  @Test
  public void multisetsMatchOriginalAcrossSeededCountsOverflowAndCompaction() throws Exception {
    try (URLClassLoader oracle = originalLoader()) {
      for (String owner : Arrays.asList("HashMultiset", "LinkedHashMultiset")) {
        Object original = create(oracle, owner);
        Object candidate = create(getClass().getClassLoader(), owner);
        Random random = new Random(0x4D33434F554E54L);
        for (int step = 0; step < 10000; step++) {
          int operation = random.nextInt(8);
          Object key = random.nextInt(7) == 0 ? null : new CollisionKey(random.nextInt(30));
          int amount = new int[] {0, 1, 2, 5, -1, Integer.MAX_VALUE}[random.nextInt(6)];
          int replacement = random.nextInt(6);
          Object expected =
              outcome(() -> editMultiset(original, operation, key, amount, replacement));
          Object actual =
              outcome(() -> editMultiset(candidate, operation, key, amount, replacement));
          assertEquals(owner + " result at step " + step, expected, actual);
          assertEquals(((Collection<?>) original).size(), ((Collection<?>) candidate).size());
          for (int index = -1; index < 30; index++) {
            Object probe = index == -1 ? null : new CollisionKey(index);
            assertEquals(
                owner + " count at step " + step,
                multisetCall(original, "count", new Class<?>[] {Object.class}, probe),
                multisetCall(candidate, "count", new Class<?>[] {Object.class}, probe));
          }
          if (owner.equals("LinkedHashMultiset")) {
            assertEquals(new ArrayList<>(elements(original)), new ArrayList<>(elements(candidate)));
          } else {
            assertEquals(new HashSet<>(elements(original)), new HashSet<>(elements(candidate)));
          }
        }
      }
    }
  }

  @Test
  public void replaceAllKeepsOriginalClearBeforeCallbackAndPartialFailure() throws Exception {
    try (URLClassLoader oracle = originalLoader()) {
      for (boolean invert : new boolean[] {false, true}) {
        for (boolean fail : new boolean[] {false, true}) {
          Map<Object, Object> original = map(oracle, "HashBiMap");
          Map<Object, Object> candidate = HashBiMap.create();
          for (Map<Object, Object> map : Arrays.asList(original, candidate)) {
            map.put("a", "x");
            map.put("b", "y");
            map.put("c", "z");
          }
          Map<Object, Object> left = invert ? inverse(original) : original;
          Map<Object, Object> right = invert ? inverse(candidate) : candidate;
          List<Object> expectedCallbacks = new ArrayList<>();
          List<Object> actualCallbacks = new ArrayList<>();
          Object expected = outcome(() -> replaceAndRecord(left, expectedCallbacks, fail));
          Object actual = outcome(() -> replaceAndRecord(right, actualCallbacks, fail));
          assertEquals(expected, actual);
          assertEquals(expectedCallbacks, actualCallbacks);
          assertEquals(entries(original), entries(candidate));
        }
      }
    }
  }

  @Test
  public void inverseForcePutRepairsPredecessorWhenLastPhysicalSlotMoves() {
    HashBiMap<String, Integer> map = HashBiMap.create();
    map.put("first", 1);
    map.put("second", 2);
    map.put("third", 3);
    map.inverse().put(1, "renamed");
    assertEquals(Arrays.asList("second", "third", "renamed"), new ArrayList<>(map.keySet()));
    map.inverse().forcePut(4, "renamed");
    assertEquals(3, map.size());
    assertEquals(Arrays.asList("second", "third", "renamed"), new ArrayList<>(map.keySet()));
    assertEquals(Arrays.asList(2, 3, 4), new ArrayList<>(map.values()));
    assertEquals("renamed", map.inverse().get(4));
  }

  @Test
  public void inverseForcePutMergesAdjacentSlotsWithoutCreatingAnOrderCycle() {
    HashBiMap<String, Integer> map = HashBiMap.create();
    map.put("first", 1);
    map.put("second", 2);
    map.put("third", 3);
    assertEquals("third", map.inverse().forcePut(3, "second"));
    map.put("fourth", 4);
    assertEquals(Arrays.asList("first", "second", "fourth"), new ArrayList<>(map.keySet()));
    assertEquals(Arrays.asList(1, 3, 4), new ArrayList<>(map.values()));
    assertEquals(3, map.size());
  }

  @Test
  public void replacementEpochsAndOriginatingEntryIteratorRemainCompatible() {
    HashBiMap<String, Integer> map = HashBiMap.create();
    map.put("first", 1);
    map.put("second", 2);
    Iterator<String> unrelated = map.keySet().iterator();
    Iterator<Map.Entry<String, Integer>> entries = map.entrySet().iterator();
    Map.Entry<String, Integer> entry = entries.next();
    assertEquals(Integer.valueOf(1), entry.setValue(3));
    assertThrows(ConcurrentModificationException.class, unrelated::hasNext);
    entries.remove();
    assertEquals("second", entries.next().getKey());
    assertFalse(map.containsKey("first"));
    Iterator<Map.Entry<Integer, String>> inverseEntries = map.inverse().entrySet().iterator();
    assertEquals("second", inverseEntries.next().setValue("renamed"));
    inverseEntries.remove();
    assertTrue(map.isEmpty());
  }

  @Test
  public void multisetEntryViewsAndOccurrenceRemovalSurviveSlotMoves() {
    for (Multiset<String> multiset :
        Arrays.asList(HashMultiset.<String>create(), LinkedHashMultiset.<String>create())) {
      multiset.add("first", 3);
      multiset.add("second", 2);
      multiset.add("third", 1);
      Multiset.Entry<String> retained = multiset.entrySet().iterator().next();
      String key = retained.getElement();
      multiset.setCount(key, 0);
      assertEquals(0, retained.getCount());
      multiset.add(key, 4);
      assertEquals(4, retained.getCount());
      Iterator<String> occurrences = multiset.iterator();
      int removed = 0;
      while (occurrences.hasNext()) {
        occurrences.next();
        occurrences.remove();
        removed++;
      }
      assertEquals(7, removed);
      assertTrue(multiset.isEmpty());
      assertEquals(0, retained.getCount());
    }
  }

  @Test
  public void originalAndCandidateSerializationReadEachOtherIncludingInverseAndSelf()
      throws Exception {
    try (URLClassLoader oracle = originalLoader()) {
      for (ClassLoader writer : Arrays.asList(oracle, getClass().getClassLoader())) {
        ClassLoader reader = writer == oracle ? getClass().getClassLoader() : oracle;
        Map<Object, Object> bimap = map(writer, "HashBiMap");
        bimap.put("first", "one");
        bimap.put(null, null);
        bimap.put("third", "three");
        Object[] graph = (Object[]) read(write(new Object[] {bimap, inverse(bimap)}), reader);
        Map<Object, Object> restored = castMap(graph[0]);
        assertEquals(entries(bimap), entries(restored));
        assertSame(graph[1], inverse(restored));
        assertSame(restored, inverse(castMap(graph[1])));
        for (String owner : Arrays.asList("HashMultiset", "LinkedHashMultiset")) {
          Object multiset = create(writer, owner);
          assertEquals(
              0, multisetCall(multiset, "add", new Class<?>[] {Object.class, int.class}, "value", 7));
          assertEquals(
              0, multisetCall(multiset, "add", new Class<?>[] {Object.class, int.class}, null, 2));
          Object restoredMultiset = read(write(multiset), reader);
          assertEquals(9, ((Collection<?>) restoredMultiset).size());
          assertEquals(
              7, multisetCall(restoredMultiset, "count", new Class<?>[] {Object.class}, "value"));
          assertEquals(
              2,
              multisetCall(
                  restoredMultiset, "count", new Class<?>[] {Object.class}, new Object[] {null}));
          Object self = create(writer, owner);
          assertEquals(
              0, multisetCall(self, "add", new Class<?>[] {Object.class, int.class}, self, 2));
          Object restoredSelf = read(write(self), reader);
          assertEquals(2, ((Collection<?>) restoredSelf).size());
          assertSame(restoredSelf, ((Collection<?>) restoredSelf).iterator().next());
        }
      }
    }
  }

  private static Object editMap(
      Map<Object, Object> map, int operation, Object key, Object value, boolean removeAfterSet)
      throws Exception {
    switch (operation) {
      case 0:
        return map.put(key, value);
      case 1:
        return bimapCall(map, "forcePut", new Class<?>[] {Object.class, Object.class}, key, value);
      case 2:
        return inverse(map).put(value, key);
      case 3:
        return bimapCall(
            inverse(map), "forcePut", new Class<?>[] {Object.class, Object.class}, value, key);
      case 4:
        return map.remove(key);
      case 5:
        return inverse(map).remove(value);
      case 6:
        return map.keySet().remove(key);
      case 7:
        return map.values().remove(value);
      case 8:
        return editEntry(map, key, value, removeAfterSet);
      case 9:
        return editEntry(inverse(map), value, key, removeAfterSet);
      case 10:
        map.replaceAll(
            (k, v) -> v == null ? null : new CollisionKey(100 + (((CollisionKey) v).id + 1) % 50));
        return null;
      default:
        map.clear();
        return null;
    }
  }

  private static Object editEntry(
      Map<Object, Object> map, Object key, Object value, boolean remove) {
    Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Object, Object> entry = iterator.next();
      if (Objects.equals(key, entry.getKey())) {
        Object oldValue = entry.setValue(value);
        if (remove) {
          iterator.remove();
        }
        return oldValue;
      }
    }
    return "absent";
  }

  private static Object editMultiset(
      Object multiset, int operation, Object key, int amount, int replacement) throws Exception {
    switch (operation) {
      case 0:
        return multisetCall(multiset, "add", new Class<?>[] {Object.class, int.class}, key, amount);
      case 1:
        return multisetCall(
            multiset, "remove", new Class<?>[] {Object.class, int.class}, key, amount);
      case 2:
        return multisetCall(
            multiset, "setCount", new Class<?>[] {Object.class, int.class}, key, amount);
      case 3:
        return multisetCall(
            multiset,
            "setCount",
            new Class<?>[] {Object.class, int.class, int.class},
            key,
            amount,
            replacement);
      case 4:
        return elements(multiset).remove(key);
      case 5:
        Iterator<?> iterator = elements(multiset).iterator();
        while (iterator.hasNext()) {
          if (Objects.equals(key, iterator.next())) {
            iterator.remove();
            return true;
          }
        }
        return false;
      case 6:
        return ((Collection<?>) multiset).remove(key);
      default:
        ((Collection<?>) multiset).clear();
        return null;
    }
  }

  private static Object replaceAndRecord(
      Map<Object, Object> map, List<Object> callbacks, boolean fail) {
    map.replaceAll(
        (key, value) -> {
          callbacks.add(Arrays.asList(key, value, entries(map)));
          if (fail && callbacks.size() == 2) {
            throw new IllegalStateException("deliberate callback failure");
          }
          return value.equals("x")
              ? "y"
              : value.equals("y")
                  ? "z"
                  : value.equals("z")
                      ? "x"
                      : value.equals("a") ? "b" : value.equals("b") ? "c" : "a";
        });
    return null;
  }

  private static Object field(Object object, String name) throws Exception {
    for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
      try {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
      } catch (NoSuchFieldException ignored) {
        // Continue to the original package-private storage owner.
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static URLClassLoader originalLoader() throws Exception {
    String original = System.getProperty("m3.baseline.classes");
    assertNotNull("Provide independently compiled pinned JRE baseline classes", original);
    File directory = new File(original);
    assertTrue("Missing pinned JRE baseline classes: " + original, directory.isDirectory());
    List<URL> urls = new ArrayList<>();
    urls.add(directory.toURI().toURL());
    for (String entry :
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
            .split(File.pathSeparator)) {
      if (entry.endsWith(".jar")) {
        urls.add(new File(entry).toURI().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private static Object create(ClassLoader loader, String owner) throws Exception {
    return Class.forName(PACKAGE + owner, true, loader).getMethod("create").invoke(null);
  }

  private static Map<Object, Object> map(ClassLoader loader, String owner) throws Exception {
    return castMap(create(loader, owner));
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> castMap(Object object) {
    return (Map<Object, Object>) object;
  }

  private static Map<Object, Object> inverse(Map<Object, Object> map) throws Exception {
    return castMap(bimapCall(map, "inverse", new Class<?>[0]));
  }

  private static Object bimapCall(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    return call(target, "BiMap", name, types, args);
  }

  private static Object multisetCall(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    return call(target, "Multiset", name, types, args);
  }

  private static Object call(
      Object target, String owner, String name, Class<?>[] types, Object... args) throws Exception {
    Method method =
        Class.forName(PACKAGE + owner, true, target.getClass().getClassLoader())
            .getMethod(name, types);
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw (Error) cause;
    }
  }

  private static Set<?> elements(Object multiset) throws Exception {
    return (Set<?>) multisetCall(multiset, "elementSet", new Class<?>[0]);
  }

  private static List<Map.Entry<Object, Object>> entries(Map<Object, Object> map) {
    List<Map.Entry<Object, Object>> copy = new ArrayList<>();
    for (Map.Entry<Object, Object> entry : map.entrySet()) {
      copy.add(new SimpleImmutableEntry<>(entry));
    }
    return copy;
  }

  private interface Operation {
    Object execute() throws Exception;
  }

  private static Object outcome(Operation operation) throws Exception {
    try {
      return operation.execute();
    } catch (IllegalArgumentException
        | IllegalStateException
        | ConcurrentModificationException exception) {
      return Arrays.asList(exception.getClass().getName(), exception.getMessage());
    }
  }

  private static byte[] write(Object object) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(object);
    }
    return bytes.toByteArray();
  }

  private static Object read(byte[] bytes, ClassLoader loader) throws Exception {
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes)) {
          @Override
          protected Class<?> resolveClass(ObjectStreamClass descriptor)
              throws IOException, ClassNotFoundException {
            return Class.forName(descriptor.getName(), false, loader);
          }
        }) {
      return input.readObject();
    }
  }

  private static final class CollisionKey implements Serializable {
    private final int id;

    CollisionKey(int id) {
      this.id = id;
    }

    @Override
    public int hashCode() {
      return 7;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof CollisionKey && id == ((CollisionKey) other).id;
    }

    @Override
    public String toString() {
      return "collision-" + id;
    }

    private static final long serialVersionUID = 1;
  }
}

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/** Primitive topology discriminators with unchanged original owners as behavioral oracles. */
public final class M3ImmutableIndexArrayContractTest {
  private static final String PACKAGE = "com.m3.collections.guava.collect.";

  @Test
  public void collisionTopologyUsesIndexesWhilePreservingOriginalEntryArrayIdentity()
      throws Exception {
    ImmutableMap.Builder<ProbeKey, Integer> mapBuilder = ImmutableMap.builderWithExpectedSize(9);
    ImmutableBiMap.Builder<ProbeKey, ProbeKey> biBuilder =
        ImmutableBiMap.builderWithExpectedSize(9);
    Map.Entry<ProbeKey, Integer>[] mapPayload = mapBuilder.entries;
    Map.Entry<ProbeKey, ProbeKey>[] biPayload = biBuilder.entries;
    for (int i = 0; i < 9; i++) {
      mapBuilder.put(new ProbeKey(i, 0), i);
      biBuilder.put(new ProbeKey(i, 0), new ProbeKey(100 + i, 0));
    }
    Map.Entry<?, ?>[] mapEntries = mapPayload.clone();
    Map.Entry<?, ?>[] biEntries = biPayload.clone();
    RegularImmutableMap<ProbeKey, Integer> map =
        (RegularImmutableMap<ProbeKey, Integer>) mapBuilder.buildOrThrow();
    RegularImmutableBiMap<ProbeKey, ProbeKey> bimap =
        (RegularImmutableBiMap<ProbeKey, ProbeKey>) biBuilder.buildOrThrow();
    assertSame(mapPayload, map.entries);
    assertSame(biPayload, bimap.entries);
    assertTrue(field(map, "table") instanceof int[]);
    assertTrue(field(map, "nextInKeyBucket") instanceof int[]);
    for (String name :
        Arrays.asList("keyTable", "valueTable", "nextInKeyBucket", "nextInValueBucket")) {
      assertTrue(field(bimap, name) instanceof int[]);
    }
    for (int i = 0; i < 9; i++) {
      assertSame(mapEntries[i], map.entries[i]);
      assertSame(biEntries[i], bimap.entries[i]);
      assertEquals(ImmutableMapEntry.class, map.entries[i].getClass());
      assertEquals(ImmutableMapEntry.class, bimap.entries[i].getClass());
      assertNull(((ImmutableMapEntry<?, ?>) map.entries[i]).getNextInKeyBucket());
      assertNull(((ImmutableMapEntry<?, ?>) bimap.entries[i]).getNextInValueBucket());
      assertEquals(Integer.valueOf(i), map.get(new ProbeKey(i, 0)));
      assertEquals(new ProbeKey(i, 0), bimap.inverse().get(new ProbeKey(100 + i, 0)));
    }
  }

  @Test
  public void duplicateCompactionRebuildsEveryIndexWithAndWithoutCapacityChange() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (int[] sequence :
          new int[][] {
            {0, 1, 2, 1, 3, 4, 5, 0, 6}, {0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0}
          }) {
        Object expectedBuilder = builder(original, "ImmutableMap", sequence.length);
        Object actualBuilder =
            builder(getClass().getClassLoader(), "ImmutableMap", sequence.length);
        for (int i = 0; i < sequence.length; i++) {
          ProbeKey key = new ProbeKey(sequence[i], 0);
          put(expectedBuilder, "ImmutableMap", key, i);
          put(actualBuilder, "ImmutableMap", key, i);
        }
        Map<?, ?> expected = build(expectedBuilder, "ImmutableMap", true);
        Map<?, ?> actual = build(actualBuilder, "ImmutableMap", true);
        assertMapsAndKeyIdentities(expected, actual);
        for (int id : sequence) {
          assertEquals(expected.get(new ProbeKey(id, 0)), actual.get(new ProbeKey(id, 0)));
        }
        int[] next = (int[]) field(actual, "nextInKeyBucket");
        assertEquals(actual.size(), next.length);
        for (int index : next) {
          assertTrue(index == -1 || (index >= 0 && index < actual.size()));
        }
        assertEquals(
            buildOutcome(expectedBuilder, "ImmutableMap", false),
            buildOutcome(actualBuilder, "ImmutableMap", false));
        assertMapsAndKeyIdentities(expected, build(actualBuilder, "ImmutableMap", true));
      }
    }
  }

  @Test
  public void mapBuildersMatchOriginalAcrossDuplicatePoliciesOrderingAndSavedSnapshots()
      throws Exception {
    try (URLClassLoader original = originalLoader()) {
      Random random = new Random(0x4D33494E4445584DL);
      for (int round = 0; round < 180; round++) {
        int capacity = random.nextInt(24);
        Object expectedBuilder = builder(original, "ImmutableMap", capacity);
        Object actualBuilder = builder(getClass().getClassLoader(), "ImmutableMap", capacity);
        if (round % 3 == 0) {
          Comparator<Integer> order = Comparator.reverseOrder();
          assertSame(
              expectedBuilder,
              invoke(
                  expectedBuilder,
                  "ImmutableMap$Builder",
                  "orderEntriesByValue",
                  new Class<?>[] {Comparator.class},
                  order));
          assertSame(
              actualBuilder,
              invoke(
                  actualBuilder,
                  "ImmutableMap$Builder",
                  "orderEntriesByValue",
                  new Class<?>[] {Comparator.class},
                  order));
        }
        int mask = new int[] {0, 3, 31}[round % 3];
        List<Map<?, ?>> saved = new ArrayList<>();
        List<List<Map.Entry<Object, Object>>> snapshots = new ArrayList<>();
        for (int step = 0; step < 48; step++) {
          ProbeKey key = new ProbeKey(random.nextInt(22), mask);
          Integer value = random.nextInt(100);
          put(expectedBuilder, "ImmutableMap", key, value);
          put(actualBuilder, "ImmutableMap", key, value);
          if (step % 4 == 0) {
            Map<?, ?> expected = build(expectedBuilder, "ImmutableMap", true);
            Map<?, ?> actual = build(actualBuilder, "ImmutableMap", true);
            assertMapsAndKeyIdentities(expected, actual);
            assertEquals(expected.getClass().getSimpleName(), actual.getClass().getSimpleName());
            assertEquals(
                expected.entrySet().spliterator().characteristics(),
                actual.entrySet().spliterator().characteristics());
            assertEquals(
                buildOutcome(expectedBuilder, "ImmutableMap", false),
                buildOutcome(actualBuilder, "ImmutableMap", false));
            saved.add(actual);
            snapshots.add(entries(actual));
          }
        }
        for (int i = 0; i < saved.size(); i++) {
          assertEquals(snapshots.get(i), entries(saved.get(i)));
        }
      }
    }
  }

  @Test
  public void bimapDualIndexesMatchOriginalThroughBothFloodingDirectionsAndConflicts()
      throws Exception {
    try (URLClassLoader original = originalLoader()) {
      Random random = new Random(0x4D334249494E4458L);
      for (int round = 0; round < 700; round++) {
        int size = random.nextInt(24);
        Object expectedBuilder = builder(original, "ImmutableBiMap", size);
        Object actualBuilder = builder(getClass().getClassLoader(), "ImmutableBiMap", size);
        int keyMask = round % 3 == 0 ? 0 : 31;
        int valueMask = round % 3 == 1 ? 0 : 31;
        for (int i = 0; i < size; i++) {
          Object key = new ProbeKey(i, keyMask);
          Object value = new ProbeKey(1000 + i, valueMask);
          if (i == size - 1 && size > 1 && round % 7 == 0) {
            key = new ProbeKey(0, keyMask);
          }
          if (i == size - 1 && size > 1 && round % 7 == 1) {
            value = new ProbeKey(1000, valueMask);
          }
          put(expectedBuilder, "ImmutableBiMap", key, value);
          put(actualBuilder, "ImmutableBiMap", key, value);
        }
        Object expectedOutcome = buildOutcome(expectedBuilder, "ImmutableBiMap", false);
        Object actualOutcome = buildOutcome(actualBuilder, "ImmutableBiMap", false);
        assertEquals("bimap build at " + round, expectedOutcome, actualOutcome);
        if (expectedOutcome instanceof String) {
          continue;
        }
        Map<?, ?> expected = build(expectedBuilder, "ImmutableBiMap", false);
        Map<?, ?> actual = build(actualBuilder, "ImmutableBiMap", false);
        assertMapsAndKeyIdentities(expected, actual);
        assertMapsAndKeyIdentities(inverse(expected), inverse(actual));
        assertEquals(expected.getClass().getSimpleName(), actual.getClass().getSimpleName());
        // The unchanged singleton implementation returns fresh inverses; preserve the original
        // identity behavior of each concrete owner instead of strengthening that contract.
        assertEquals(expected == inverse(inverse(expected)), actual == inverse(inverse(actual)));
        for (int i = -1; i <= size; i++) {
          assertEquals(
              expected.get(new ProbeKey(i, keyMask)), actual.get(new ProbeKey(i, keyMask)));
          assertEquals(
              inverse(expected).get(new ProbeKey(1000 + i, valueMask)),
              inverse(actual).get(new ProbeKey(1000 + i, valueMask)));
        }
        assertNull(actual.get(null));
        assertNull(inverse(actual).get(null));
      }
    }
  }

  @Test
  public void originalAndCandidateSerializeMapsViewsInversesAndStandaloneEntriesBothWays()
      throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (String kind : Arrays.asList("ImmutableMap", "ImmutableBiMap")) {
        for (int size : new int[] {2, 9, 20}) {
          for (boolean originalWriter : new boolean[] {false, true}) {
            ClassLoader writer = originalWriter ? original : getClass().getClassLoader();
            ClassLoader reader = originalWriter ? getClass().getClassLoader() : original;
            Object builder = builder(writer, kind, size);
            for (int i = 0; i < size; i++) {
              put(builder, kind, collisionString(i), collisionString(i + 32));
            }
            Map<?, ?> map = build(builder, kind, false);
            Object secondary = kind.equals("ImmutableBiMap") ? inverse(map) : map.values();
            Object[] copy =
                (Object[])
                    read(
                        write(new Object[] {map, secondary, map.entrySet(), map.keySet()}), reader);
            assertEquals(entries(map), entries((Map<?, ?>) copy[0]));
            assertEquals(new ArrayList<>(map.entrySet()), new ArrayList<>((Collection<?>) copy[2]));
            assertEquals(new ArrayList<>(map.keySet()), new ArrayList<>((Collection<?>) copy[3]));
            if (kind.equals("ImmutableBiMap")) {
              assertEquals(entries((Map<?, ?>) secondary), entries((Map<?, ?>) copy[1]));
              if (size <= 9) {
                assertSame(copy[0], inverse((Map<?, ?>) copy[1]));
              }
            } else {
              assertEquals(new ArrayList<>(map.values()), new ArrayList<>((Collection<?>) copy[1]));
            }
            List<? extends Map.Entry<?, ?>> serializedEntries = new ArrayList<>(map.entrySet());
            Map.Entry<?, ?> entry =
                serializedEntries.get(kind.equals("ImmutableBiMap") ? size - 1 : 0);
            assertEquals(entry, read(write(entry), reader));
          }
        }
      }
    }
  }

  @Test
  public void mutableInputEntriesFollowOriginalReadsAndRemainDetached() throws Exception {
    try (URLClassLoader original = originalLoader()) {
      for (String kind : Arrays.asList("ImmutableMap", "ImmutableBiMap")) {
        for (int size : new int[] {6, 20}) {
          List<ObservedEntry> expectedSource = new ArrayList<>();
          List<ObservedEntry> actualSource = new ArrayList<>();
          for (int i = 0; i < size; i++) {
            expectedSource.add(new ObservedEntry(i));
            actualSource.add(new ObservedEntry(i));
          }
          Map<?, ?> expected =
              (Map<?, ?>)
                  Class.forName(PACKAGE + kind, true, original)
                      .getMethod("copyOf", Iterable.class)
                      .invoke(null, expectedSource);
          Map<?, ?> actual =
              (Map<?, ?>)
                  Class.forName(PACKAGE + kind, true, getClass().getClassLoader())
                      .getMethod("copyOf", Iterable.class)
                      .invoke(null, actualSource);
          for (int i = 0; i < size; i++) {
            assertEquals(expectedSource.get(i).keyReads, actualSource.get(i).keyReads);
            assertEquals(expectedSource.get(i).valueReads, actualSource.get(i).valueReads);
            expectedSource.get(i).value += 1000;
            actualSource.get(i).value += 1000;
            assertEquals(Integer.valueOf(i), expected.get(new ProbeKey(i, 0)));
            assertEquals(Integer.valueOf(i), actual.get(new ProbeKey(i, 0)));
          }
        }
      }
    }
  }

  private static void assertMapsAndKeyIdentities(Map<?, ?> expected, Map<?, ?> actual) {
    assertEquals(entries(expected), entries(actual));
    assertEquals(expected.hashCode(), actual.hashCode());
    List<?> expectedKeys = new ArrayList<>(expected.keySet());
    List<?> actualKeys = new ArrayList<>(actual.keySet());
    for (int i = 0; i < expectedKeys.size(); i++) {
      assertSame(expectedKeys.get(i), actualKeys.get(i));
    }
  }

  private static Object builder(ClassLoader loader, String owner, int capacity) throws Exception {
    return Class.forName(PACKAGE + owner, true, loader)
        .getMethod("builderWithExpectedSize", int.class)
        .invoke(null, capacity);
  }

  private static void put(Object builder, String owner, Object key, Object value) throws Exception {
    assertSame(
        builder,
        invoke(
            builder,
            owner + "$Builder",
            "put",
            new Class<?>[] {Object.class, Object.class},
            key,
            value));
  }

  private static Map<?, ?> build(Object builder, String owner, boolean keepingLast)
      throws Exception {
    return (Map<?, ?>)
        invoke(
            builder,
            owner + "$Builder",
            keepingLast ? "buildKeepingLast" : "buildOrThrow",
            new Class<?>[0]);
  }

  private static Object buildOutcome(Object builder, String owner, boolean keepingLast)
      throws Exception {
    try {
      return entries(build(builder, owner, keepingLast));
    } catch (IllegalArgumentException exception) {
      return exception.getClass().getName() + ":" + exception.getMessage();
    }
  }

  private static Map<?, ?> inverse(Map<?, ?> map) throws Exception {
    return (Map<?, ?>) invoke(map, "ImmutableBiMap", "inverse", new Class<?>[0]);
  }

  private static List<Map.Entry<Object, Object>> entries(Map<?, ?> map) {
    List<Map.Entry<Object, Object>> entries = new ArrayList<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      entries.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
    }
    return entries;
  }

  private static Object invoke(
      Object target, String owner, String method, Class<?>[] types, Object... args)
      throws Exception {
    Method selected =
        Class.forName(PACKAGE + owner, true, target.getClass().getClassLoader())
            .getMethod(method, types);
    try {
      return selected.invoke(target, args);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw (Error) cause;
    }
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static URLClassLoader originalLoader() throws Exception {
    String directory = System.getProperty("m3.baseline.classes");
    assertNotNull("Independently compiled original classes are required", directory);
    assertTrue(new File(directory).isDirectory());
    List<URL> urls = new ArrayList<>();
    urls.add(new File(directory).toURI().toURL());
    for (String entry :
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
            .split(File.pathSeparator)) {
      if (entry.endsWith(".jar")) {
        urls.add(new File(entry).toURI().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private static byte[] write(Object object) throws Exception {
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
              throws ClassNotFoundException {
            return Class.forName(descriptor.getName(), false, loader);
          }
        }) {
      return input.readObject();
    }
  }

  private static String collisionString(int bits) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      text.append(((bits >>> i) & 1) == 0 ? "Aa" : "BB");
    }
    return text.toString();
  }

  private static final class ProbeKey {
    private final int id;
    private final int hashMask;

    ProbeKey(int id, int hashMask) {
      this.id = id;
      this.hashMask = hashMask;
    }

    @Override
    public int hashCode() {
      return id & hashMask;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ProbeKey && id == ((ProbeKey) other).id;
    }

    @Override
    public String toString() {
      return "key-" + id;
    }
  }

  private static final class ObservedEntry implements Map.Entry<ProbeKey, Integer> {
    private final int id;
    private int value;
    int keyReads;
    int valueReads;

    ObservedEntry(int id) {
      this.id = id;
      this.value = id;
    }

    @Override
    public ProbeKey getKey() {
      keyReads++;
      return new ProbeKey(id, 0);
    }

    @Override
    public Integer getValue() {
      valueReads++;
      return value;
    }

    @Override
    public Integer setValue(Integer value) {
      throw new UnsupportedOperationException();
    }
  }
}

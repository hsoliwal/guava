// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.validation;

import com.google.common.collect.ArrayListMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

/** Public-API artifact check, usable from either classpath or the named Guava module. */
public final class ArtifactSmoke {
  private ArtifactSmoke() {}

  @SuppressWarnings("deprecation")
  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 1) throw new IllegalArgumentException("expected actual Guava artifact path");
    Path expected = Path.of(arguments[0]).toRealPath();
    check(Files.isRegularFile(expected), "expected CodeSource must be a packaged artifact");
    Path actual = Path.of(ArrayListMultimap.class.getProtectionDomain().getCodeSource()
        .getLocation().toURI()).toRealPath();
    check(expected.equals(actual), "exact packaged ArrayListMultimap CodeSource");
    Module module = ArrayListMultimap.class.getModule();
    check(!module.isNamed() || "com.google.common".equals(module.getName()), "original named module");
    check(ObjectStreamClass.lookup(ArrayListMultimap.class).getSerialVersionUID() == 0,
        "original serialization UID");
    ArrayListMultimap<String, String> map = ArrayListMultimap.create(22, 3);
    List<String> live = map.get("key");
    check(live instanceof RandomAccess && live.isEmpty(), "absent live RandomAccess list");
    check(!map.containsKey("key"), "absent get leaves key set unchanged");
    live.addAll(Arrays.asList("first", null, "first"));
    map.put(null, null);
    check(live.subList(0, 2) instanceof RandomAccess, "RandomAccess live sublist");
    List<String> previous = map.replaceValues("key", Arrays.asList("next", "next", null));
    check(previous instanceof RandomAccess && previous.equals(Arrays.asList("first", null, "first")),
        "replacement ordered duplicate/null snapshot");
    expect(UnsupportedOperationException.class, () -> previous.add("forbidden"));
    check(live.equals(Arrays.asList("next", "next", null)), "live view observes replacement");
    List<String> removed = map.removeAll("key");
    check(removed instanceof RandomAccess && removed.equals(Arrays.asList("next", "next", null)),
        "removeAll RandomAccess snapshot");
    expect(UnsupportedOperationException.class, removed::clear);
    check(live.isEmpty() && !map.containsKey("key"), "last removal cleans key");
    live.addAll(Arrays.asList("restored", null, "restored"));
    check(map.get("key").equals(live), "empty view reattaches");
    map.trimToSize();
    Iterator<String> iterator = live.iterator();
    check("restored".equals(iterator.next()), "fresh iterator before no-op trim");
    map.trimToSize();
    expect(ConcurrentModificationException.class, iterator::next);
    ArrayListMultimap<String, String> restored = roundTrip(map);
    check(restored.equals(map), "serialization roundtrip");
    check(restored.get("key").equals(Arrays.asList("restored", null, "restored")),
        "serialized duplicate/null order");
    check(restored.containsEntry(null, null), "serialized null key/value");
    check(restored.get("key") instanceof RandomAccess, "restored RandomAccess list");
    System.out.println("M3_GUAVA_ARTIFACT_SMOKE_OK module="
        + (module.isNamed() ? module.getName() : "unnamed"));
  }

  @SuppressWarnings("unchecked")
  private static ArrayListMultimap<String, String> roundTrip(ArrayListMultimap<String, String> map)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(map);
    }
    check(bytes.size() < 65536, "bounded in-memory serialization fixture");
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ArrayListMultimap<String, String>) input.readObject();
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void expect(Class<? extends Throwable> expected, ThrowingRunnable operation)
      throws Exception {
    try {
      operation.run();
    } catch (Throwable failure) {
      if (expected.isInstance(failure)) return;
      throw new AssertionError("expected " + expected.getName(), failure);
    }
    throw new AssertionError("expected " + expected.getName());
  }

  @FunctionalInterface private interface ThrowingRunnable {
    void run() throws Exception;
  }
}

/*
 * Copyright 2026 Synexia M3 contributors
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.common.collect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** Finite isolated-JVM oracle/candidate protocol for this one concrete Guava class. */
public final class M3ArrayListMultimapProtocol {
  private static final Path TASK_ROOT =
      Path.of("S:\\codex-work\\guava-array-multimap-one-file-20261018");
  private static final long RESERVE = 8L * 1024 * 1024 * 1024;
  private static final int STREAM_LIMIT = 1024 * 1024;
  private static int assertions;
  private static boolean candidate;
  private static final MessageDigest DIGEST = newDigest();

  private M3ArrayListMultimapProtocol() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 4
        || !(args[0].equals("oracle") || args[0].equals("candidate"))) {
      throw new IllegalArgumentException(
          "Usage: oracle|candidate expected-CodeSource [check|write|read] [stream-path]");
    }
    candidate = args[0].equals("candidate");
    Path expected = Path.of(args[1]).toRealPath();
    Path actual = Path.of(ArrayListMultimap.class.getProtectionDomain().getCodeSource()
        .getLocation().toURI()).toRealPath();
    check(actual.equals(expected), "exact ArrayListMultimap CodeSource");
    String action = args.length > 2 ? args[2] : "check";
    if (action.equals("check")) {
      check(args.length <= 3, "check arguments");
      apiAndFactories();
      nullsDuplicatesAndLists();
      liveViews();
      iteratorsAndTrim();
      collisionAndTombstoneChurn();
      serializationInMemory();
      System.out.println("PROTOCOL_DIGEST " + hex(DIGEST.digest()));
      System.out.println("PROTOCOL_ASSERTIONS " + assertions);
    } else if (action.equals("write") || action.equals("read")) {
      check(args.length == 4, "stream arguments");
      Path stream = confinedStreamPath(Path.of(args[3]), action.equals("write"));
      if (action.equals("write")) {
        byte[] bytes = serializedFixture();
        check(Files.getFileStore(stream.getParent()).getUsableSpace() - bytes.length >= RESERVE,
            "stream storage reserve");
        Files.write(stream, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        System.out.println("STREAM_WRITE bytes=" + bytes.length + " sha256="
            + hex(newDigest().digest(bytes)));
      } else {
        check(Files.size(stream) <= STREAM_LIMIT, "bounded stream input");
        verifyFixture(Files.readAllBytes(stream));
        System.out.println("STREAM_READ_OK");
      }
    } else {
      throw new IllegalArgumentException("Unknown action: " + action);
    }
  }

  private static void apiAndFactories() throws Exception {
    check(Modifier.isFinal(ArrayListMultimap.class.getModifiers()), "original final class");
    check(ArrayListMultimap.class.getSuperclass().getName().equals(
        "com.google.common.collect.ArrayListMultimapGwtSerializationDependencies"),
        "original serialization superclass");
    check(ArrayListMultimap.class.getDeclaredConstructors().length == 3, "three constructors");
    for (var constructor : ArrayListMultimap.class.getDeclaredConstructors()) {
      check(Modifier.isPrivate(constructor.getModifiers()), "private constructor");
    }
    long publicDeclarations = Arrays.stream(ArrayListMultimap.class.getDeclaredMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isBridge()).count();
    check(publicDeclarations == 4, "three create overloads and trimToSize");
    check(ObjectStreamClass.lookup(ArrayListMultimap.class).getSerialVersionUID() == 0,
        "original serialVersionUID");
    check(ObjectStreamClass.lookup(ArrayListMultimap.class).getFields().length == 0,
        "no serialized private storage fields");
    ArrayListMultimap<String, String> defaults = ArrayListMultimap.create();
    check(defaults.expectedValuesPerKey == 3 && defaults.isEmpty(), "default factory");
    ArrayListMultimap<String, String> sized = ArrayListMultimap.create(17, 19);
    check(sized.expectedValuesPerKey == 19 && sized.isEmpty(), "sized factory");
    sized.putAll("k", Arrays.asList("a", "a", "b"));
    ArrayListMultimap<String, String> copied = ArrayListMultimap.create(sized);
    check(copied.equals(sized) && copied.expectedValuesPerKey == 19, "copy factory hints");
    sized.put("k", "later");
    check(copied.get("k").equals(Arrays.asList("a", "a", "b")), "copy independence");
    HashMultimap<String, String> setMultimap = HashMultimap.create();
    setMultimap.put("set", "v");
    check(ArrayListMultimap.create(setMultimap).expectedValuesPerKey == 3,
        "foreign multimap default list hint");
    check(ArrayListMultimap.create(0, 0).isEmpty(), "zero hints");
    check(ArrayListMultimap.create(Integer.MAX_VALUE, 0).isEmpty(), "lazy large key hint");
    expect(IllegalArgumentException.class, () -> ArrayListMultimap.create(-1, 2),
        "expectedSize cannot be negative but was: -1");
    expect(IllegalArgumentException.class, () -> ArrayListMultimap.create(1, -2),
        "expectedValuesPerKey cannot be negative but was: -2");
    expect(IllegalArgumentException.class, () -> ArrayListMultimap.create(-1, -2),
        "expectedSize cannot be negative but was: -1");
    expect(NullPointerException.class, () -> ArrayListMultimap.create((Multimap<?, ?>) null), null);
    storage(sized);
    record("factories", canonical(copied));
  }

  private static void nullsDuplicatesAndLists() {
    ArrayListMultimap<String, String> m = ArrayListMultimap.create(0, 0);
    List<String> missing = m.get("missing");
    check(missing.isEmpty() && missing instanceof RandomAccess, "absent live RandomAccess list");
    check(!m.containsKey("missing") && m.asMap().get("missing") == null,
        "get does not create an empty map key");
    missing.add(null);
    m.put("missing", "a");
    m.put("missing", "a");
    m.put(null, null);
    check(m.size() == 4 && m.keys().count("missing") == 3, "duplicates count as pairs");
    check(m.containsKey(null) && m.containsValue(null) && m.containsEntry(null, null),
        "null key and value queries");
    check(missing.equals(Arrays.asList(null, "a", "a")), "per-key insertion order");
    check(m.remove("missing", "a") && missing.equals(Arrays.asList(null, "a")),
        "remove one duplicate");
    List<String> old = m.replaceValues("missing", Arrays.asList("x", "x", null));
    check(old instanceof RandomAccess && old.equals(Arrays.asList(null, "a")),
        "replace returns previous ordered list");
    expect(UnsupportedOperationException.class, () -> old.add("bad"), null);
    check(missing.equals(Arrays.asList("x", "x", null)), "get view survives replacement");
    m.get("missing").add("new");
    check(old.equals(Arrays.asList(null, "a")), "replacement result is detached");
    List<String> removed = m.removeAll("missing");
    check(removed instanceof RandomAccess && removed.equals(Arrays.asList("x", "x", null, "new")),
        "removeAll detached RandomAccess snapshot");
    expect(UnsupportedOperationException.class, () -> removed.clear(), null);
    check(missing.isEmpty() && !m.containsKey("missing"), "last value cleans map key");
    missing.add("reborn");
    check(m.get("missing").equals(Collections.singletonList("reborn")), "empty view reinserts key");
    check(m.replaceValues("missing", Collections.emptyList()).equals(
        Collections.singletonList("reborn")), "empty replacement returns old values");
    check(!m.containsKey("missing"), "empty replacement removes key");
    check(!m.putAll("absent", Collections.emptyList()) && !m.containsKey("absent"),
        "empty iterable does not add key");
    check(m.removeAll("absent").isEmpty() && !m.remove("absent", "value"), "absent removals");
    check(!m.containsEntry("absent", null), "absent entry query");
    ArrayListMultimap<String, String> source = ArrayListMultimap.create();
    source.putAll("added", Arrays.asList("1", "1", "2"));
    check(m.putAll(source) && m.get("added").equals(Arrays.asList("1", "1", "2")),
        "putAll multimap preserves duplicate order");
    ArrayListMultimap<String, String> copy = ArrayListMultimap.create(m);
    check(m.equals(copy) && copy.equals(m) && m.hashCode() == copy.hashCode(), "list equality/hash");
    check(source.equals(ImmutableListMultimap.copyOf(source)),
        "ListMultimap equality across implementations");
    check(!m.equals(HashMultimap.create(m)), "list and set multimaps differ");
    check(m.toString().contains("added=[1, 1, 2]"), "toString retains per-key list order");
    List<String> pairs = new ArrayList<>();
    m.forEach((k, v) -> pairs.add(token(k) + "=" + token(v)));
    Collections.sort(pairs);
    List<String> expectedPairs = new ArrayList<>();
    m.entries().forEach(e -> expectedPairs.add(token(e.getKey()) + "=" + token(e.getValue())));
    Collections.sort(expectedPairs);
    check(pairs.equals(expectedPairs), "forEach covers every pair");
    record("nulls-duplicates", canonical(m));
  }

  private static void liveViews() {
    ArrayListMultimap<String, Integer> m = ArrayListMultimap.create();
    m.putAll("k", Arrays.asList(1, 2, 3));
    ListIterator<Integer> li = m.get("k").listIterator();
    check(li.next() == 1, "listIterator next");
    li.set(10);
    li.add(11);
    check(li.next() == 2, "listIterator add cursor");
    li.remove();
    check(m.get("k").equals(Arrays.asList(10, 11, 3)), "listIterator mutates multimap");
    m.get("k").subList(1, 3).clear();
    check(m.get("k").equals(Collections.singletonList(10)) && m.size() == 1, "subList clear");
    Map<String, Collection<Integer>> asMap = m.asMap();
    asMap.get("k").add(12);
    check(m.get("k").equals(Arrays.asList(10, 12)), "asMap value is live");
    expect(UnsupportedOperationException.class, () -> asMap.put("bad", new ArrayList<>()), null);
    expect(UnsupportedOperationException.class,
        () -> asMap.putAll(Collections.singletonMap("bad", new ArrayList<>())), null);
    expect(UnsupportedOperationException.class,
        () -> asMap.entrySet().iterator().next().setValue(new ArrayList<>()), null);
    check(m.keys().count("k") == 2 && m.keys().remove("k", 1) == 2,
        "keys multiset remove returns previous count");
    check(m.get("k").equals(Collections.singletonList(12)), "keys remove consumes first values");
    expect(UnsupportedOperationException.class, () -> m.keys().add("k"), null);
    Iterator<String> keyOccurrences = m.keys().iterator();
    check(keyOccurrences.next().equals("k"), "keys occurrence iterator");
    keyOccurrences.remove();
    check(m.isEmpty() && asMap.isEmpty(), "keys iterator removes last mapping");
    m.putAll("entries", Arrays.asList(1, 1, 2));
    check(m.entries().remove(new AbstractMap.SimpleImmutableEntry<>("entries", 1))
        && m.get("entries").equals(Arrays.asList(1, 2)), "entries remove one pair");
    Iterator<Map.Entry<String, Integer>> entries = m.entries().iterator();
    Map.Entry<String, Integer> first = entries.next();
    expect(UnsupportedOperationException.class, () -> first.setValue(99), null);
    entries.remove();
    check(m.get("entries").equals(Collections.singletonList(2)), "entries iterator remove");
    check(m.values().remove(2) && m.isEmpty(), "values remove cleans key");
    m.putAll("values", Arrays.asList(1, 2, 3));
    Iterator<Integer> values = m.values().iterator();
    check(values.next() == 1, "values iterator first");
    values.remove();
    m.values().retainAll(Collections.singleton(3));
    check(m.get("values").equals(Collections.singletonList(3)), "values retainAll");
    check(m.keySet().remove("values") && m.isEmpty(), "keySet remove drops values");
    m.putAll("batch", Arrays.asList(4, 5));
    Collection<Integer> detached = asMap.remove("batch");
    check(detached.equals(Arrays.asList(4, 5)) && m.isEmpty(), "asMap remove detached values");
    detached.add(6);
    check(m.isEmpty(), "asMap removal result is detached");
    m.putAll("batch", Arrays.asList(7, 8));
    Iterator<Map.Entry<String, Collection<Integer>>> batches = asMap.entrySet().iterator();
    check(batches.next().getValue().equals(Arrays.asList(7, 8)), "asMap entry value");
    batches.remove();
    check(m.isEmpty(), "asMap iterator removes key and all values");
    m.putAll("view", Arrays.asList(3, 4));
    Iterator<String> keys = m.keySet().iterator();
    check(keys.next().equals("view"), "keySet iterator");
    keys.remove();
    check(m.isEmpty(), "keySet iterator removes values");
    m.putAll("clear", Arrays.asList(1, 2));
    m.keySet().clear();
    check(m.isEmpty(), "keySet clear");
    m.put("clear", 1);
    asMap.clear();
    check(m.isEmpty() && m.entries().isEmpty() && m.values().isEmpty() && m.keys().isEmpty(),
        "asMap clear updates all cached views");
    m.put("clear", 1);
    m.clear();
    check(m.isEmpty() && asMap.isEmpty(), "direct clear updates cached map view");
    record("live-views", canonical(m));
  }

  @SuppressWarnings("deprecation")
  private static void iteratorsAndTrim() {
    ArrayListMultimap<String, Integer> m = ArrayListMultimap.create();
    m.putAll("k", Arrays.asList(1, 2, 3));
    Iterator<Integer> iterator = m.get("k").iterator();
    m.put("k", 4);
    expect(ConcurrentModificationException.class, iterator::next, null);
    List<Integer> sub = m.get("k").subList(1, 3);
    m.put("k", 5);
    expect(ConcurrentModificationException.class, sub::isEmpty, null);
    List<Integer> replacedDelegate = m.get("k").subList(0, 1);
    m.removeAll("k");
    m.put("k", 9);
    expect(ConcurrentModificationException.class, () -> replacedDelegate.get(0), null);
    m.put("other", 7);
    Iterator<String> keyIterator = m.keySet().iterator();
    m.put("new-key", 8);
    expect(ConcurrentModificationException.class, keyIterator::next, null);
    m.trimToSize();
    Iterator<Integer> exactCapacity = m.get("k").iterator();
    List<Integer> trimSublist = m.get("k").subList(0, 1);
    m.trimToSize();
    expect(ConcurrentModificationException.class, exactCapacity::next, null);
    expect(ConcurrentModificationException.class, trimSublist::isEmpty, null);
    Iterator<Integer> nonStructural = m.get("k").iterator();
    m.get("k").set(0, 10);
    check(nonStructural.next() == 10, "set preserves iterator epoch");
    check(m.get("k") instanceof RandomAccess, "live list RandomAccess after trim");
    record("epochs", canonical(m));
  }

  private static void collisionAndTombstoneChurn() {
    ArrayListMultimap<CollisionKey, Integer> m = ArrayListMultimap.create(96, 3);
    TreeMap<Integer, List<Integer>> model = new TreeMap<>();
    for (int id = 0; id < 48; id++) {
      m.putAll(new CollisionKey(id), Arrays.asList(id, id, -id));
      model.put(id, new ArrayList<>(Arrays.asList(id, id, -id)));
    }
    for (int id = 0; id < 48; id += 2) {
      check(m.removeAll(new CollisionKey(id)).equals(model.remove(id)), "collision key deletion");
    }
    for (int id = 48; id < 72; id++) {
      m.put(new CollisionKey(id), id);
      model.put(id, new ArrayList<>(Collections.singletonList(id)));
    }
    SplittableRandom random = new SplittableRandom(0x3348_4d33_6172L);
    for (int step = 0; step < 1600; step++) {
      int id = random.nextInt(96);
      CollisionKey key = new CollisionKey(id);
      Integer value = random.nextInt(13) == 0 ? null : random.nextInt(17) - 8;
      int operation = random.nextInt(10);
      List<Integer> current = model.get(id);
      switch (operation) {
        case 0:
          check(m.put(key, value), "churn put");
          model.computeIfAbsent(id, unused -> new ArrayList<>()).add(value);
          break;
        case 1:
          check(m.remove(key, value) == (current != null && current.remove(value)), "churn remove");
          clean(model, id);
          break;
        case 2:
          int index = random.nextInt(current == null ? 1 : current.size() + 1);
          m.get(key).add(index, value);
          model.computeIfAbsent(id, unused -> new ArrayList<>()).add(index, value);
          break;
        case 3:
          if (current != null) {
            int removedIndex = random.nextInt(current.size());
            check(java.util.Objects.equals(m.get(key).remove(removedIndex), current.remove(removedIndex)),
                "churn indexed remove");
            clean(model, id);
          }
          break;
        case 4:
          List<Integer> replacement = new ArrayList<>();
          for (int n = random.nextInt(4); n > 0; n--) {
            replacement.add(random.nextInt(9) - 4);
          }
          check(m.replaceValues(key, replacement).equals(
              current == null ? Collections.emptyList() : current), "churn replace snapshot");
          if (replacement.isEmpty()) model.remove(id);
          else model.put(id, new ArrayList<>(replacement));
          break;
        case 5:
          check(m.removeAll(key).equals(current == null ? Collections.emptyList() : current),
              "churn removeAll snapshot");
          model.remove(id);
          break;
        case 6:
          int occurrences = random.nextInt(4);
          check(m.keys().remove(key, occurrences) == (current == null ? 0 : current.size()),
              "churn keys multiplicity");
          if (current != null) current.subList(0, Math.min(occurrences, current.size())).clear();
          clean(model, id);
          break;
        case 7:
          Collection<Integer> view = m.asMap().get(key);
          check((view == null) == (current == null), "churn asMap absence");
          if (view != null) {
            check(view.remove(value) == current.remove(value), "churn asMap removal");
            clean(model, id);
          }
          break;
        case 8:
          if (current != null) {
            int from = random.nextInt(current.size() + 1);
            int to = from + random.nextInt(current.size() - from + 1);
            m.get(key).subList(from, to).clear();
            current.subList(from, to).clear();
            clean(model, id);
          }
          break;
        default:
          check(!m.putAll(key, Collections.emptyList()), "churn empty iterable");
          check(m.entries().remove(new AbstractMap.SimpleImmutableEntry<>(key, value))
              == (current != null && current.remove(value)), "churn entries removal");
          clean(model, id);
      }
      TreeMap<Integer, List<Integer>> actual = new TreeMap<>();
      for (Map.Entry<CollisionKey, Collection<Integer>> entry : m.asMap().entrySet()) {
        check(!entry.getValue().isEmpty(), "no empty backing key after churn");
        actual.put(entry.getKey().id, new ArrayList<>(entry.getValue()));
      }
      check(actual.equals(model), "collision/tombstone model step " + step);
      int pairs = model.values().stream().mapToInt(List::size).sum();
      check(m.size() == pairs && m.entries().size() == pairs && m.values().size() == pairs
          && m.keys().size() == pairs && m.keySet().size() == model.size(), "churn pair/view sizes");
      record("step-" + step + "-" + operation, canonical(m));
    }
    storage(m);
  }

  private static void clean(Map<Integer, List<Integer>> model, int id) {
    List<Integer> values = model.get(id);
    if (values != null && values.isEmpty()) model.remove(id);
  }

  private static void serializationInMemory() throws Exception {
    byte[] bytes = serializedFixture();
    verifyFixture(bytes);
    record("serialization", "normal-and-self-reference-roundtrip");
  }

  private static ArrayListMultimap<Object, Object> normalFixture() {
    ArrayListMultimap<Object, Object> m = ArrayListMultimap.create(6, 23);
    m.putAll("ordered", Arrays.asList("first", null, "first", "last"));
    m.put(null, "null-key");
    m.put(new CollisionKey(9), 9);
    m.put(new CollisionKey(3), 3);
    return m;
  }

  private static byte[] serializedFixture() throws Exception {
    ArrayListMultimap<Object, Object> normal = normalFixture();
    ArrayListMultimap<String, Object> self = ArrayListMultimap.create(3, 7);
    self.put("self", self);
    self.put("plain", "tail");
    self.put("self", self);
    storage(normal);
    storage(self);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(bytes) {
      @Override protected void annotateClass(Class<?> type) throws IOException {
        if (type.getName().contains("com.m3.collections.")) {
          throw new IOException("M3 private storage descriptor leaked: " + type.getName());
        }
      }
    }) {
      stream.writeObject(new Object[] {normal, self});
    }
    byte[] result = bytes.toByteArray();
    check(result.length <= STREAM_LIMIT, "bounded serialization fixture");
    check(!new String(result, StandardCharsets.ISO_8859_1).contains("com.m3.collections."),
        "stream contains no private M3 class names");
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void verifyFixture(byte[] bytes) throws Exception {
    Object[] roots;
    try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
      @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)
          throws IOException, ClassNotFoundException {
        if (descriptor.getName().contains("com.m3.collections.")) {
          throw new IOException("Unexpected M3 private storage descriptor: " + descriptor.getName());
        }
        return super.resolveClass(descriptor);
      }
    }) {
      roots = (Object[]) stream.readObject();
      check(stream.read() == -1, "one complete stream fixture");
    }
    check(roots.length == 2, "two serialized roots");
    ArrayListMultimap<Object, Object> normal = (ArrayListMultimap<Object, Object>) roots[0];
    check(canonical(normal).equals(canonical(normalFixture())), "normal serialized data");
    check(normal.expectedValuesPerKey == 3, "original transient list-hint reset");
    storage(normal);
    ArrayListMultimap<String, Object> self = (ArrayListMultimap<String, Object>) roots[1];
    check(self.size() == 3 && self.keySet().size() == 2 && self.get("self").size() == 2,
        "self-valued multimap sizes without recursive equals/hash/toString");
    check(self.get("self").get(0) == self && self.get("self").get(1) == self,
        "both self values preserve root identity");
    check(self.get("plain").equals(Collections.singletonList("tail")), "self fixture plain value");
    check(self.expectedValuesPerKey == 3, "self fixture transient list-hint reset");
    storage(self);
  }

  private static Path confinedStreamPath(Path requested, boolean write) throws Exception {
    Path root = TASK_ROOT.toRealPath();
    Path target = requested.toAbsolutePath().normalize();
    Path parent = target.getParent().toRealPath();
    check(parent.startsWith(root), "stream remains in approved S task root");
    if (!write) check(target.toRealPath().startsWith(root), "stream read has no outward symlink");
    if (write) check(!Files.exists(target), "stream output is new");
    return parent.resolve(target.getFileName());
  }

  private static void storage(ArrayListMultimap<?, ?> m) {
    Object map = m.backingMap();
    String mapName = candidate ? "com.m3.collections.M3LinkedHashMap" : "java.util.HashMap";
    String listName = candidate ? "com.m3.collections.M3ArrayList" : "java.util.ArrayList";
    check(map.getClass().getName().equals(mapName), "actual private map owner " + mapName);
    if (candidate) arrayOwner(map, true);
    for (Collection<?> values : m.backingMap().values()) {
      check(values.getClass().getName().equals(listName), "actual private list owner " + listName);
      check(values instanceof RandomAccess, "private list owner RandomAccess");
      if (candidate) arrayOwner(values, false);
    }
  }

  private static void arrayOwner(Object owner, boolean requireSlots) {
    Object arrayStorage = owner;
    if (requireSlots) {
      String tableName = "com.m3.collections.M3LinkedHashMap$Table";
      check(owner.getClass().getName().equals("com.m3.collections.M3LinkedHashMap"),
          "actual M3 map wrapper owner");
      try {
        Field tableField = owner.getClass().getDeclaredField("table");
        check(Modifier.isPrivate(tableField.getModifiers())
            && Modifier.isFinal(tableField.getModifiers()), "private final map table field");
        check(tableField.getType().getName().equals(tableName), "declared M3 Table field type");
        check(tableField.trySetAccessible(), "private M3 Table access");
        arrayStorage = tableField.get(owner);
        check(arrayStorage != null, "actual M3 Table is nonnull");
        check(arrayStorage.getClass().getName().equals(tableName), "actual M3 Table owner");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("Cannot inspect the actual private M3 Table", e);
      }
    }
    boolean objects = false;
    boolean integers = false;
    for (Class<?> type = arrayStorage.getClass(); type != null; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) continue;
        objects |= field.getType() == Object[].class;
        integers |= field.getType() == int[].class;
        check(!(field.getType().isArray()
            && Map.Entry.class.isAssignableFrom(field.getType().getComponentType())),
            "private storage has no retained Entry array");
      }
    }
    check(objects, "actual private Object[] payload owner");
    check(!requireSlots || integers, "actual private int[] slot/index owner");
  }

  private static String canonical(Multimap<?, ?> m) {
    TreeMap<String, String> ordered = new TreeMap<>();
    for (Map.Entry<?, ? extends Collection<?>> e : m.asMap().entrySet()) {
      StringBuilder list = new StringBuilder("[");
      for (Object value : e.getValue()) list.append(token(value)).append(';');
      ordered.put(token(e.getKey()), list.append(']').toString());
    }
    return ordered.toString();
  }

  private static String token(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return "s" + ((String) value).length() + ":" + value;
    if (value instanceof Integer) return "i:" + value;
    if (value instanceof CollisionKey) return "k:" + ((CollisionKey) value).id;
    throw new AssertionError("Unsupported canonical fixture type: " + value.getClass().getName());
  }

  private static void record(String name, String state) {
    DIGEST.update((name + "=" + state + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static String hex(byte[] data) {
    return java.util.HexFormat.of().formatHex(data);
  }

  private static void check(boolean condition, String description) {
    if (!condition) throw new AssertionError(description);
    assertions++;
  }

  private static <T extends Throwable> void expect(
      Class<T> expected, ThrowingRunnable operation, String message) {
    try {
      operation.run();
    } catch (Throwable thrown) {
      check(expected.isInstance(thrown), "expected " + expected.getName() + " got " + thrown);
      if (message != null) check(message.equals(thrown.getMessage()), "exact exception message");
      return;
    }
    throw new AssertionError("Expected " + expected.getName());
  }

  @FunctionalInterface private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class CollisionKey implements Serializable {
    private static final long serialVersionUID = 1;
    final int id;
    CollisionKey(int id) { this.id = id; }
    @Override public int hashCode() { return 7; }
    @Override public boolean equals(Object other) {
      return other instanceof CollisionKey && ((CollisionKey) other).id == id;
    }
  }
}

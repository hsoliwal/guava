/*
 * Copyright 2026 Hitesh Soliwal and Contributors to the Synexia Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m3.collections.guava.primitives;

import static org.junit.Assert.assertThrows;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import java.lang.reflect.Field;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import junit.framework.TestCase;
import org.jspecify.annotations.NullUnmarked;

/** Contracts for primitive copying from immutable list views and arbitrary collections. */
@GwtIncompatible
@J2ktIncompatible
@NullUnmarked
public final class M3ImmutablePrimitiveArrayBulkCopyTest extends TestCase {

  public void testIntViewsCopyExactSliceAndOwnFreshCompactArray() throws Exception {
    int[] values = {Integer.MIN_VALUE, -257, 0, 128, Integer.MAX_VALUE};
    ImmutableIntArray source = ImmutableIntArray.copyOf(values);
    for (int start = 0; start <= values.length; start++) {
      for (int end = start; end <= values.length; end++) {
        List<Integer> view = source.asList().subList(start, end);
        int[] expected = Arrays.copyOfRange(values, start, end);
        ImmutableIntArray copied = ImmutableIntArray.copyOf((Collection<Integer>) view);
        assertIntValues(expected, copied.toArray());
        assertIntValues(expected, ImmutableIntArray.copyOf((Iterable<Integer>) view).toArray());
        if (expected.length == 0) {
          assertSame(ImmutableIntArray.of(), copied);
        } else {
          assertFreshCompactArray(source, copied, expected.length);
          int[] exported = copied.toArray();
          exported[0] = values[(start + 1) % values.length];
          assertIntValues(expected, copied.toArray());
        }
        ImmutableIntArray.Builder builder = ImmutableIntArray.builder(0).add(values[0]);
        assertSame(builder, builder.addAll((Collection<Integer>) view));
        assertSame(builder, builder.addAll((Iterable<Integer>) view));
        int[] appended = new int[1 + 2 * expected.length];
        appended[0] = values[0];
        System.arraycopy(expected, 0, appended, 1, expected.length);
        System.arraycopy(expected, 0, appended, 1 + expected.length, expected.length);
        assertIntValues(appended, builder.build().toArray());
      }
    }
    assertIntValues(values, source.toArray());
  }

  public void testIntBuilderAppendsAliasedViewsAcrossGrowth() {
    int[] values = {Integer.MIN_VALUE, -257, 0, 128, Integer.MAX_VALUE};
    ImmutableIntArray.Builder builder = ImmutableIntArray.builder(8).addAll(values);
    ImmutableIntArray original = builder.build();
    List<Integer> suffix = original.subArray(1, 4).asList();
    builder.addAll((Collection<Integer>) suffix);
    ImmutableIntArray first = builder.build();
    builder.addAll((Iterable<Integer>) original.asList());
    int[] firstExpected = new int[8];
    System.arraycopy(values, 0, firstExpected, 0, values.length);
    System.arraycopy(values, 1, firstExpected, values.length, 3);
    int[] finalExpected = Arrays.copyOf(firstExpected, 13);
    System.arraycopy(values, 0, finalExpected, 8, values.length);
    assertIntValues(values, original.toArray());
    assertIntValues(firstExpected, first.toArray());
    assertIntValues(finalExpected, builder.build().toArray());
  }

  public void testIntArbitraryCopyRetainsCallbacksAndFailureIdentity() {
    int[] values = {Integer.MIN_VALUE, -257, 0, 128, Integer.MAX_VALUE};
    RecordingCollection<Integer> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    assertIntValues(Arrays.copyOf(values, 2), ImmutableIntArray.copyOf(source).toArray());
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    source.calls.clear();
    RuntimeException failure = new IllegalStateException("array conversion failure");
    source.arrayFailure = failure;
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> ImmutableIntArray.copyOf(source)));
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    assertThrows(
        NullPointerException.class, () -> ImmutableIntArray.copyOf((Collection<Integer>) null));
    assertThrows(
        NullPointerException.class, () -> ImmutableIntArray.copyOf((Iterable<Integer>) null));
  }

  public void testIntArbitraryBuilderRetainsCallbacksAndPartialNullState() {
    int[] values = {Integer.MIN_VALUE, -257, 0, 128, Integer.MAX_VALUE};
    RecordingCollection<Integer> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    ImmutableIntArray.Builder builder = ImmutableIntArray.builder(8);
    builder.addAll(source);
    assertEquals(
        Arrays.asList("size", "iterator", "hasNext", "next", "hasNext", "next", "hasNext"),
        source.calls);
    assertIntValues(Arrays.copyOf(values, 2), builder.build().toArray());
    ImmutableIntArray.Builder failing = ImmutableIntArray.builder(8).add(values[0]);
    assertThrows(
        NullPointerException.class,
        () -> failing.addAll(Arrays.asList(values[1], null, values[2])));
    assertIntValues(new int[] {values[0], values[1], 0}, failing.build().toArray());
    ImmutableIntArray.Builder unaffected = ImmutableIntArray.builder(8).add(values[0]);
    assertThrows(NullPointerException.class, () -> unaffected.addAll((Collection<Integer>) null));
    assertIntValues(new int[] {values[0]}, unaffected.build().toArray());
  }

  private static void assertIntValues(int[] expected, int[] actual) {
    assertTrue(Arrays.equals(expected, actual));
  }

  public void testLongViewsCopyExactSliceAndOwnFreshCompactArray() throws Exception {
    long[] values = {Long.MIN_VALUE, -257L, 0L, 128L, Long.MAX_VALUE};
    ImmutableLongArray source = ImmutableLongArray.copyOf(values);
    for (int start = 0; start <= values.length; start++) {
      for (int end = start; end <= values.length; end++) {
        List<Long> view = source.asList().subList(start, end);
        long[] expected = Arrays.copyOfRange(values, start, end);
        ImmutableLongArray copied = ImmutableLongArray.copyOf((Collection<Long>) view);
        assertLongValues(expected, copied.toArray());
        assertLongValues(expected, ImmutableLongArray.copyOf((Iterable<Long>) view).toArray());
        if (expected.length == 0) {
          assertSame(ImmutableLongArray.of(), copied);
        } else {
          assertFreshCompactArray(source, copied, expected.length);
          long[] exported = copied.toArray();
          exported[0] = values[(start + 1) % values.length];
          assertLongValues(expected, copied.toArray());
        }
        ImmutableLongArray.Builder builder = ImmutableLongArray.builder(0).add(values[0]);
        assertSame(builder, builder.addAll((Collection<Long>) view));
        assertSame(builder, builder.addAll((Iterable<Long>) view));
        long[] appended = new long[1 + 2 * expected.length];
        appended[0] = values[0];
        System.arraycopy(expected, 0, appended, 1, expected.length);
        System.arraycopy(expected, 0, appended, 1 + expected.length, expected.length);
        assertLongValues(appended, builder.build().toArray());
      }
    }
    assertLongValues(values, source.toArray());
  }

  public void testLongBuilderAppendsAliasedViewsAcrossGrowth() {
    long[] values = {Long.MIN_VALUE, -257L, 0L, 128L, Long.MAX_VALUE};
    ImmutableLongArray.Builder builder = ImmutableLongArray.builder(8).addAll(values);
    ImmutableLongArray original = builder.build();
    List<Long> suffix = original.subArray(1, 4).asList();
    builder.addAll((Collection<Long>) suffix);
    ImmutableLongArray first = builder.build();
    builder.addAll((Iterable<Long>) original.asList());
    long[] firstExpected = new long[8];
    System.arraycopy(values, 0, firstExpected, 0, values.length);
    System.arraycopy(values, 1, firstExpected, values.length, 3);
    long[] finalExpected = Arrays.copyOf(firstExpected, 13);
    System.arraycopy(values, 0, finalExpected, 8, values.length);
    assertLongValues(values, original.toArray());
    assertLongValues(firstExpected, first.toArray());
    assertLongValues(finalExpected, builder.build().toArray());
  }

  public void testLongArbitraryCopyRetainsCallbacksAndFailureIdentity() {
    long[] values = {Long.MIN_VALUE, -257L, 0L, 128L, Long.MAX_VALUE};
    RecordingCollection<Long> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    assertLongValues(Arrays.copyOf(values, 2), ImmutableLongArray.copyOf(source).toArray());
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    source.calls.clear();
    RuntimeException failure = new IllegalStateException("array conversion failure");
    source.arrayFailure = failure;
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> ImmutableLongArray.copyOf(source)));
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    assertThrows(
        NullPointerException.class, () -> ImmutableLongArray.copyOf((Collection<Long>) null));
    assertThrows(
        NullPointerException.class, () -> ImmutableLongArray.copyOf((Iterable<Long>) null));
  }

  public void testLongArbitraryBuilderRetainsCallbacksAndPartialNullState() {
    long[] values = {Long.MIN_VALUE, -257L, 0L, 128L, Long.MAX_VALUE};
    RecordingCollection<Long> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    ImmutableLongArray.Builder builder = ImmutableLongArray.builder(8);
    builder.addAll(source);
    assertEquals(
        Arrays.asList("size", "iterator", "hasNext", "next", "hasNext", "next", "hasNext"),
        source.calls);
    assertLongValues(Arrays.copyOf(values, 2), builder.build().toArray());
    ImmutableLongArray.Builder failing = ImmutableLongArray.builder(8).add(values[0]);
    assertThrows(
        NullPointerException.class,
        () -> failing.addAll(Arrays.asList(values[1], null, values[2])));
    assertLongValues(new long[] {values[0], values[1], 0}, failing.build().toArray());
    ImmutableLongArray.Builder unaffected = ImmutableLongArray.builder(8).add(values[0]);
    assertThrows(NullPointerException.class, () -> unaffected.addAll((Collection<Long>) null));
    assertLongValues(new long[] {values[0]}, unaffected.build().toArray());
  }

  private static void assertLongValues(long[] expected, long[] actual) {
    assertTrue(Arrays.equals(expected, actual));
  }

  public void testDoubleViewsCopyExactSliceAndOwnFreshCompactArray() throws Exception {
    double[] values = {
      -0.0,
      Double.longBitsToDouble(0x7ff8000000000042L),
      Double.NEGATIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.longBitsToDouble(0x7ff0000000000001L)
    };
    ImmutableDoubleArray source = ImmutableDoubleArray.copyOf(values);
    for (int start = 0; start <= values.length; start++) {
      for (int end = start; end <= values.length; end++) {
        List<Double> view = source.asList().subList(start, end);
        double[] expected = Arrays.copyOfRange(values, start, end);
        ImmutableDoubleArray copied = ImmutableDoubleArray.copyOf((Collection<Double>) view);
        assertDoubleValues(expected, copied.toArray());
        assertDoubleValues(expected, ImmutableDoubleArray.copyOf((Iterable<Double>) view).toArray());
        if (expected.length == 0) {
          assertSame(ImmutableDoubleArray.of(), copied);
        } else {
          assertFreshCompactArray(source, copied, expected.length);
          double[] exported = copied.toArray();
          exported[0] = values[(start + 1) % values.length];
          assertDoubleValues(expected, copied.toArray());
        }
        ImmutableDoubleArray.Builder builder = ImmutableDoubleArray.builder(0).add(values[0]);
        assertSame(builder, builder.addAll((Collection<Double>) view));
        assertSame(builder, builder.addAll((Iterable<Double>) view));
        double[] appended = new double[1 + 2 * expected.length];
        appended[0] = values[0];
        System.arraycopy(expected, 0, appended, 1, expected.length);
        System.arraycopy(expected, 0, appended, 1 + expected.length, expected.length);
        assertDoubleValues(appended, builder.build().toArray());
      }
    }
    assertDoubleValues(values, source.toArray());
  }

  public void testDoubleBuilderAppendsAliasedViewsAcrossGrowth() {
    double[] values = {
      -0.0,
      Double.longBitsToDouble(0x7ff8000000000042L),
      Double.NEGATIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.longBitsToDouble(0x7ff0000000000001L)
    };
    ImmutableDoubleArray.Builder builder = ImmutableDoubleArray.builder(8).addAll(values);
    ImmutableDoubleArray original = builder.build();
    List<Double> suffix = original.subArray(1, 4).asList();
    builder.addAll((Collection<Double>) suffix);
    ImmutableDoubleArray first = builder.build();
    builder.addAll((Iterable<Double>) original.asList());
    double[] firstExpected = new double[8];
    System.arraycopy(values, 0, firstExpected, 0, values.length);
    System.arraycopy(values, 1, firstExpected, values.length, 3);
    double[] finalExpected = Arrays.copyOf(firstExpected, 13);
    System.arraycopy(values, 0, finalExpected, 8, values.length);
    assertDoubleValues(values, original.toArray());
    assertDoubleValues(firstExpected, first.toArray());
    assertDoubleValues(finalExpected, builder.build().toArray());
  }

  public void testDoubleArbitraryCopyRetainsCallbacksAndFailureIdentity() {
    double[] values = {
      -0.0,
      Double.longBitsToDouble(0x7ff8000000000042L),
      Double.NEGATIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.longBitsToDouble(0x7ff0000000000001L)
    };
    RecordingCollection<Double> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    assertDoubleValues(Arrays.copyOf(values, 2), ImmutableDoubleArray.copyOf(source).toArray());
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    source.calls.clear();
    RuntimeException failure = new IllegalStateException("array conversion failure");
    source.arrayFailure = failure;
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> ImmutableDoubleArray.copyOf(source)));
    assertEquals(Arrays.asList("isEmpty", "size", "toArray"), source.calls);
    assertThrows(
        NullPointerException.class, () -> ImmutableDoubleArray.copyOf((Collection<Double>) null));
    assertThrows(
        NullPointerException.class, () -> ImmutableDoubleArray.copyOf((Iterable<Double>) null));
  }

  public void testDoubleArbitraryBuilderRetainsCallbacksAndPartialNullState() {
    double[] values = {
      -0.0,
      Double.longBitsToDouble(0x7ff8000000000042L),
      Double.NEGATIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.longBitsToDouble(0x7ff0000000000001L)
    };
    RecordingCollection<Double> source =
        new RecordingCollection<>(Arrays.asList(values[0], values[1]));
    ImmutableDoubleArray.Builder builder = ImmutableDoubleArray.builder(8);
    builder.addAll(source);
    assertEquals(
        Arrays.asList("size", "iterator", "hasNext", "next", "hasNext", "next", "hasNext"),
        source.calls);
    assertDoubleValues(Arrays.copyOf(values, 2), builder.build().toArray());
    ImmutableDoubleArray.Builder failing = ImmutableDoubleArray.builder(8).add(values[0]);
    assertThrows(
        NullPointerException.class,
        () -> failing.addAll(Arrays.asList(values[1], null, values[2])));
    assertDoubleValues(new double[] {values[0], values[1], 0}, failing.build().toArray());
    ImmutableDoubleArray.Builder unaffected = ImmutableDoubleArray.builder(8).add(values[0]);
    assertThrows(NullPointerException.class, () -> unaffected.addAll((Collection<Double>) null));
    assertDoubleValues(new double[] {values[0]}, unaffected.build().toArray());
  }

  private static void assertDoubleValues(double[] expected, double[] actual) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(Double.doubleToRawLongBits(expected[i]), Double.doubleToRawLongBits(actual[i]));
    }
  }


  public void testIntOverflowRetainsCollectionFailureOrder() throws Exception {
    ImmutableIntArray.Builder builder = ImmutableIntArray.builder(1);
    Field count = builder.getClass().getDeclaredField("count");
    count.setAccessible(true);
    count.setInt(builder, Integer.MAX_VALUE);
    assertThrows(
        ArrayIndexOutOfBoundsException.class,
        () -> builder.addAll((Collection<Integer>) ImmutableIntArray.of(1).asList()));
    // Bounded arithmetic witness: no claim that a maximum-sized array was allocated.
    assertEquals(Integer.MIN_VALUE, count.getInt(builder));
  }

  public void testLongOverflowRetainsCollectionFailureOrder() throws Exception {
    ImmutableLongArray.Builder builder = ImmutableLongArray.builder(1);
    Field count = builder.getClass().getDeclaredField("count");
    count.setAccessible(true);
    count.setInt(builder, Integer.MAX_VALUE);
    assertThrows(
        ArrayIndexOutOfBoundsException.class,
        () -> builder.addAll((Collection<Long>) ImmutableLongArray.of(1).asList()));
    // Bounded arithmetic witness: no claim that a maximum-sized array was allocated.
    assertEquals(Integer.MIN_VALUE, count.getInt(builder));
  }

  public void testDoubleOverflowRetainsCollectionFailureOrder() throws Exception {
    ImmutableDoubleArray.Builder builder = ImmutableDoubleArray.builder(1);
    Field count = builder.getClass().getDeclaredField("count");
    count.setAccessible(true);
    count.setInt(builder, Integer.MAX_VALUE);
    assertThrows(
        ArrayIndexOutOfBoundsException.class,
        () -> builder.addAll((Collection<Double>) ImmutableDoubleArray.of(1).asList()));
    // Bounded arithmetic witness: no claim that a maximum-sized array was allocated.
    assertEquals(Integer.MIN_VALUE, count.getInt(builder));
  }


  @SuppressWarnings({"rawtypes", "unchecked"})
  public void testRawOtherPrimitiveViewsRetainOriginalFallback() {
    Collection longs = ImmutableLongArray.of(0x100000001L, -2L).asList();
    assertIntValues(new int[] {1, -2}, ImmutableIntArray.copyOf(longs).toArray());
    ImmutableIntArray.Builder ints = ImmutableIntArray.builder(4).add(7);
    assertThrows(ClassCastException.class, () -> ints.addAll(longs));
    assertIntValues(new int[] {7}, ints.build().toArray());

    Collection doubles = ImmutableDoubleArray.of(1.5, -2.5).asList();
    assertLongValues(new long[] {1, -2}, ImmutableLongArray.copyOf(doubles).toArray());
    ImmutableLongArray.Builder longBuilder = ImmutableLongArray.builder(4).add(7);
    assertThrows(ClassCastException.class, () -> longBuilder.addAll(doubles));
    assertLongValues(new long[] {7}, longBuilder.build().toArray());

    Collection integers = ImmutableIntArray.of(-257, 129).asList();
    assertDoubleValues(new double[] {-257, 129}, ImmutableDoubleArray.copyOf(integers).toArray());
    ImmutableDoubleArray.Builder doubleBuilder = ImmutableDoubleArray.builder(4).add(7);
    assertThrows(ClassCastException.class, () -> doubleBuilder.addAll(integers));
    assertDoubleValues(new double[] {7}, doubleBuilder.build().toArray());
  }

  private static void assertFreshCompactArray(Object source, Object copy, int length)
      throws Exception {
    Field field = source.getClass().getDeclaredField("array");
    field.setAccessible(true);
    Object before = field.get(source);
    Object after = field.get(copy);
    assertNotSame(before, after);
    assertEquals(length, java.lang.reflect.Array.getLength(after));
  }

  private static final class RecordingCollection<E> extends AbstractCollection<E> {
    final List<E> values;
    final List<String> calls = new ArrayList<>();
    RuntimeException arrayFailure;

    RecordingCollection(List<E> values) {
      this.values = values;
    }

    @Override
    public boolean isEmpty() {
      calls.add("isEmpty");
      return super.isEmpty();
    }

    @Override
    public int size() {
      calls.add("size");
      return values.size();
    }

    @Override
    public Object[] toArray() {
      calls.add("toArray");
      if (arrayFailure != null) {
        throw arrayFailure;
      }
      return values.toArray();
    }

    @Override
    public Iterator<E> iterator() {
      calls.add("iterator");
      Iterator<E> iterator = values.iterator();
      return new Iterator<E>() {
        @Override
        public boolean hasNext() {
          calls.add("hasNext");
          return iterator.hasNext();
        }

        @Override
        public E next() {
          calls.add("next");
          return iterator.next();
        }
      };
    }
  }
}

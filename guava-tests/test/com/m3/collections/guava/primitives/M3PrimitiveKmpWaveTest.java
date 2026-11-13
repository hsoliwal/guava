/*
 * Copyright (C) 2026 Contributors to the Synexia Project.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections.guava.primitives;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import junit.framework.TestCase;

/** Actual seven Guava primitive owners, independently checked against original nested loops. */
public final class M3PrimitiveKmpWaveTest extends TestCase {
  private static final Class<?>[] TYPES = {
    byte.class, short.class, char.class, long.class, boolean.class, float.class, double.class
  };

  private static Object[] alphabet(Class<?> type) {
    if (type == byte.class) {
      return new Object[] {(byte) -128, (byte) -1, (byte) 0, (byte) 1, (byte) 127};
    }
    if (type == short.class) {
      return new Object[] {(short) -32768, (short) -1, (short) 0, (short) 1, (short) 32767};
    }
    if (type == char.class) {
      return new Object[] {'\u0000', 'A', 'Z', '\uffff'};
    }
    if (type == long.class) {
      return new Object[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
    }
    if (type == boolean.class) {
      return new Object[] {false, true};
    }
    if (type == float.class) {
      return new Object[] {-0.0f, 0.0f, -1.0f, 1.0f, Float.NaN,
        Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY};
    }
    if (type == double.class) {
      return new Object[] {-0.0d, 0.0d, -1.0d, 1.0d, Double.NaN,
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
    }
    throw new AssertionError(type);
  }

  private static Object array(Class<?> type, int length) {
    return Array.newInstance(type, length);
  }

  private static Object copy(Object input) {
    int length = Array.getLength(input);
    Object cloned = array(input.getClass().getComponentType(), length);
    System.arraycopy(input, 0, cloned, 0, length);
    return cloned;
  }

  private static boolean samePrimitive(Object a, Object b) {
    if (a instanceof Float) {
      return ((Float) a).floatValue() == ((Float) b).floatValue();
    }
    if (a instanceof Double) {
      return ((Double) a).doubleValue() == ((Double) b).doubleValue();
    }
    return a.equals(b);
  }

  private static int originalIndexOf(Object array, Object target) {
    int length = Array.getLength(array);
    int needle = Array.getLength(target);
    if (needle == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < length - needle + 1; i++) {
      for (int j = 0; j < needle; j++) {
        if (!samePrimitive(Array.get(array, i + j), Array.get(target, j))) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int actualIndexOf(Object array, Object target) {
    if (array instanceof byte[]) {
      return Bytes.indexOf((byte[]) array, (byte[]) target);
    }
    if (array instanceof short[]) {
      return Shorts.indexOf((short[]) array, (short[]) target);
    }
    if (array instanceof char[]) {
      return Chars.indexOf((char[]) array, (char[]) target);
    }
    if (array instanceof long[]) {
      return Longs.indexOf((long[]) array, (long[]) target);
    }
    if (array instanceof boolean[]) {
      return Booleans.indexOf((boolean[]) array, (boolean[]) target);
    }
    if (array instanceof float[]) {
      return Floats.indexOf((float[]) array, (float[]) target);
    }
    if (array instanceof double[]) {
      return Doubles.indexOf((double[]) array, (double[]) target);
    }
    throw new AssertionError(array.getClass());
  }

  public void testSeededOriginalParityAcrossKmpBoundary() {
    Random random = new Random(0x51eefEEDL);
    int[] lengths = {0, 1, 31, 32, 4095, 4096, 8192};
    int[] needles = {0, 1, 2, 31, 32, 33, 128, 256};
    for (Class<?> type : TYPES) {
      Object[] values = alphabet(type);
      for (int length : lengths) {
        for (int needleLength : needles) {
          for (int trial = 0; trial < 16; trial++) {
            Object haystack = array(type, length);
            Object needle = array(type, needleLength);
            for (int i = 0; i < length; i++) {
              Array.set(haystack, i, values[random.nextInt(values.length)]);
            }
            for (int i = 0; i < needleLength; i++) {
              Array.set(needle, i, values[random.nextInt(values.length)]);
            }
            Object originalHaystack = copy(haystack);
            Object originalNeedle = copy(needle);
            assertEquals(type.getSimpleName() + " n=" + length + " m=" + needleLength,
                originalIndexOf(haystack, needle), actualIndexOf(haystack, needle));
            assertTrue("input array modified", Objects.deepEquals(haystack, originalHaystack));
            assertTrue("needle array modified", Objects.deepEquals(needle, originalNeedle));
          }
        }
      }
    }
  }

  public void testPeriodicPrefixMissAndFirstHit() {
    for (Class<?> type : TYPES) {
      Object[] values = alphabet(type);
      Object normal = type == boolean.class ? Boolean.TRUE : values[values.length - 1];
      Object mismatch = type == boolean.class ? Boolean.FALSE : values[0];
      if (type == float.class || type == double.class) {
        normal = values[3]; // 1.0; NaN must not be used as an assumed match.
        mismatch = values[2]; // -1.0.
      }
      Object haystack = array(type, 16_384);
      Object needle = array(type, 128);
      for (int i = 0; i < 16_384; i++) {
        Array.set(haystack, i, normal);
      }
      for (int i = 0; i < 128; i++) {
        Array.set(needle, i, normal);
      }
      Array.set(needle, 127, mismatch);
      assertEquals(-1, actualIndexOf(haystack, needle));
      Array.set(haystack, 2048 + 127, mismatch);
      Array.set(haystack, 8192 + 127, mismatch);
      assertEquals(2048, actualIndexOf(haystack, needle));
      assertEquals(2048, originalIndexOf(haystack, needle));
      Array.set(haystack, 2048 + 127, normal);
      assertEquals(8192, actualIndexOf(haystack, needle));
    }
  }

  public void testNullEmptyAndSelfAlias() {
    for (Class<?> type : TYPES) {
      Object empty = array(type, 0);
      Object single = array(type, 1);
      Object two = array(type, 2);
      assertEquals(0, actualIndexOf(empty, empty));
      assertEquals(0, actualIndexOf(single, empty));
      assertEquals(-1, actualIndexOf(empty, single));
      assertEquals(-1, actualIndexOf(single, two));
      Object large = array(type, 4096);
      Object[] values = alphabet(type);
      for (int i = 0; i < 4096; i++) {
        Array.set(large, i, values[i % values.length]);
      }
      if (type != float.class && type != double.class) {
        assertEquals(0, actualIndexOf(large, large));
      }
      try {
        actualIndexOf(null, single);
        fail("Null haystack must fail");
      } catch (NullPointerException expected) {
        assertNotNull(expected);
      }
    }
  }

  public void testFloatingNaNAndSignedZeroRetainPrimitiveEquality() {
    float[] floats = new float[4096];
    float[] needleFloats = new float[32];
    Arrays.fill(floats, -0.0f);
    Arrays.fill(needleFloats, +0.0f);
    assertEquals(0, Floats.indexOf(floats, needleFloats));
    needleFloats[31] = Float.NaN;
    assertEquals(-1, Floats.indexOf(floats, needleFloats));
    floats[31] = Float.NaN;
    assertEquals(-1, Floats.indexOf(floats, needleFloats));

    double[] doubles = new double[4096];
    double[] needleDoubles = new double[32];
    Arrays.fill(doubles, -0.0d);
    Arrays.fill(needleDoubles, +0.0d);
    assertEquals(0, Doubles.indexOf(doubles, needleDoubles));
    needleDoubles[31] = Double.NaN;
    doubles[31] = Double.NaN;
    assertEquals(-1, Doubles.indexOf(doubles, needleDoubles));
  }

  public void testOversizedNeedleUsesOriginalResult() {
    for (Class<?> type : TYPES) {
      Object[] values = alphabet(type);
      Object normal = type == boolean.class ? Boolean.TRUE : values[values.length - 1];
      Object mismatch = type == boolean.class ? Boolean.FALSE : values[0];
      if (type == float.class || type == double.class) {
        normal = values[3];
        mismatch = values[2];
      }
      Object haystack = array(type, 65_537);
      Object needle = array(type, 65_537);
      for (int i = 0; i < 65_537; i++) {
        Array.set(haystack, i, normal);
        Array.set(needle, i, normal);
      }
      Array.set(needle, 65_536, mismatch);
      assertEquals(-1, actualIndexOf(haystack, needle));
      Array.set(haystack, 65_536, mismatch);
      assertEquals(0, actualIndexOf(haystack, needle));
      assertEquals(0, originalIndexOf(haystack, needle));
    }
  }
}

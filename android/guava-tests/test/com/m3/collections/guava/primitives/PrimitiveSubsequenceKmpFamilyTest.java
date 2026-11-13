/*
 * Copyright (C) 2026 The Guava Contributors
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
package com.m3.collections.guava.primitives;

import java.util.Arrays;
import java.util.Random;
import junit.framework.TestCase;

/** Contract-preserving primitive subsequence matching across seven native storage owners. */
public final class PrimitiveSubsequenceKmpFamilyTest extends TestCase {

  private static int scalarBytes(byte[] a, byte[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testBytesLargePeriodicAndRandomSubsequences() {
    byte[] haystack = new byte[8192];
    byte[] target = new byte[128];
    Arrays.fill(haystack, (byte) 0);
    Arrays.fill(target, (byte) 0);
    target[target.length - 1] = (byte) 1;
    assertEquals(-1, Bytes.indexOf(haystack, target));
    haystack[haystack.length - 1] = (byte) 1;
    assertEquals(haystack.length - target.length, Bytes.indexOf(haystack, target));
    haystack[haystack.length - 1] = (byte) 0;
    target[target.length - 1] = (byte) 0;
    assertEquals(0, Bytes.indexOf(haystack, target));
    assertEquals(0, Bytes.indexOf(haystack, new byte[0]));
    assertEquals(-1, Bytes.indexOf(new byte[0], new byte[] {(byte) 1}));
    assertEquals(0, Bytes.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 5);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      byte[] a = new byte[n];
      byte[] b = new byte[m];
      for (int i = 0; i < n; i++) {
        a[i] = (byte) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (byte) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=byte trial=" + trial, scalarBytes(a, b), Bytes.indexOf(a, b));
    }
  }

  private static int scalarShorts(short[] a, short[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testShortsLargePeriodicAndRandomSubsequences() {
    short[] haystack = new short[8192];
    short[] target = new short[128];
    Arrays.fill(haystack, (short) 0);
    Arrays.fill(target, (short) 0);
    target[target.length - 1] = (short) 1;
    assertEquals(-1, Shorts.indexOf(haystack, target));
    haystack[haystack.length - 1] = (short) 1;
    assertEquals(haystack.length - target.length, Shorts.indexOf(haystack, target));
    haystack[haystack.length - 1] = (short) 0;
    target[target.length - 1] = (short) 0;
    assertEquals(0, Shorts.indexOf(haystack, target));
    assertEquals(0, Shorts.indexOf(haystack, new short[0]));
    assertEquals(-1, Shorts.indexOf(new short[0], new short[] {(short) 1}));
    assertEquals(0, Shorts.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 6);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      short[] a = new short[n];
      short[] b = new short[m];
      for (int i = 0; i < n; i++) {
        a[i] = (short) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (short) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=short trial=" + trial, scalarShorts(a, b), Shorts.indexOf(a, b));
    }
  }

  private static int scalarChars(char[] a, char[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testCharsLargePeriodicAndRandomSubsequences() {
    char[] haystack = new char[8192];
    char[] target = new char[128];
    Arrays.fill(haystack, (char) 0);
    Arrays.fill(target, (char) 0);
    target[target.length - 1] = (char) 1;
    assertEquals(-1, Chars.indexOf(haystack, target));
    haystack[haystack.length - 1] = (char) 1;
    assertEquals(haystack.length - target.length, Chars.indexOf(haystack, target));
    haystack[haystack.length - 1] = (char) 0;
    target[target.length - 1] = (char) 0;
    assertEquals(0, Chars.indexOf(haystack, target));
    assertEquals(0, Chars.indexOf(haystack, new char[0]));
    assertEquals(-1, Chars.indexOf(new char[0], new char[] {(char) 1}));
    assertEquals(0, Chars.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 5);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      char[] a = new char[n];
      char[] b = new char[m];
      for (int i = 0; i < n; i++) {
        a[i] = (char) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (char) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=char trial=" + trial, scalarChars(a, b), Chars.indexOf(a, b));
    }
  }

  private static int scalarLongs(long[] a, long[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testLongsLargePeriodicAndRandomSubsequences() {
    long[] haystack = new long[8192];
    long[] target = new long[128];
    Arrays.fill(haystack, 0L);
    Arrays.fill(target, 0L);
    target[target.length - 1] = 1L;
    assertEquals(-1, Longs.indexOf(haystack, target));
    haystack[haystack.length - 1] = 1L;
    assertEquals(haystack.length - target.length, Longs.indexOf(haystack, target));
    haystack[haystack.length - 1] = 0L;
    target[target.length - 1] = 0L;
    assertEquals(0, Longs.indexOf(haystack, target));
    assertEquals(0, Longs.indexOf(haystack, new long[0]));
    assertEquals(-1, Longs.indexOf(new long[0], new long[] {1L}));
    assertEquals(0, Longs.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 5);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      long[] a = new long[n];
      long[] b = new long[m];
      for (int i = 0; i < n; i++) {
        a[i] = (long) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (long) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=long trial=" + trial, scalarLongs(a, b), Longs.indexOf(a, b));
    }
  }

  private static int scalarBooleans(boolean[] a, boolean[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testBooleansLargePeriodicAndRandomSubsequences() {
    boolean[] haystack = new boolean[8192];
    boolean[] target = new boolean[128];
    Arrays.fill(haystack, false);
    Arrays.fill(target, false);
    target[target.length - 1] = true;
    assertEquals(-1, Booleans.indexOf(haystack, target));
    haystack[haystack.length - 1] = true;
    assertEquals(haystack.length - target.length, Booleans.indexOf(haystack, target));
    haystack[haystack.length - 1] = false;
    target[target.length - 1] = false;
    assertEquals(0, Booleans.indexOf(haystack, target));
    assertEquals(0, Booleans.indexOf(haystack, new boolean[0]));
    assertEquals(-1, Booleans.indexOf(new boolean[0], new boolean[] {true}));
    assertEquals(0, Booleans.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 8);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      boolean[] a = new boolean[n];
      boolean[] b = new boolean[m];
      for (int i = 0; i < n; i++) {
        a[i] = random.nextBoolean();
      }
      for (int i = 0; i < m; i++) {
        b[i] = random.nextBoolean();
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=boolean trial=" + trial, scalarBooleans(a, b), Booleans.indexOf(a, b));
    }
  }

  private static int scalarFloats(float[] a, float[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testFloatsLargePeriodicAndRandomSubsequences() {
    float[] haystack = new float[8192];
    float[] target = new float[128];
    Arrays.fill(haystack, 0.0f);
    Arrays.fill(target, 0.0f);
    target[target.length - 1] = 1.0f;
    assertEquals(-1, Floats.indexOf(haystack, target));
    haystack[haystack.length - 1] = 1.0f;
    assertEquals(haystack.length - target.length, Floats.indexOf(haystack, target));
    haystack[haystack.length - 1] = 0.0f;
    target[target.length - 1] = 0.0f;
    assertEquals(0, Floats.indexOf(haystack, target));
    assertEquals(0, Floats.indexOf(haystack, new float[0]));
    assertEquals(-1, Floats.indexOf(new float[0], new float[] {1.0f}));
    assertEquals(0, Floats.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 6);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      float[] a = new float[n];
      float[] b = new float[m];
      for (int i = 0; i < n; i++) {
        a[i] = (float) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (float) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=float trial=" + trial, scalarFloats(a, b), Floats.indexOf(a, b));
    }
  }

  private static int scalarDoubles(double[] a, double[] b) {
    if (b.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < a.length - b.length + 1; i++) {
      for (int j = 0; j < b.length; j++) {
        if (a[i + j] != b[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  public void testDoublesLargePeriodicAndRandomSubsequences() {
    double[] haystack = new double[8192];
    double[] target = new double[128];
    Arrays.fill(haystack, 0.0d);
    Arrays.fill(target, 0.0d);
    target[target.length - 1] = 1.0d;
    assertEquals(-1, Doubles.indexOf(haystack, target));
    haystack[haystack.length - 1] = 1.0d;
    assertEquals(haystack.length - target.length, Doubles.indexOf(haystack, target));
    haystack[haystack.length - 1] = 0.0d;
    target[target.length - 1] = 0.0d;
    assertEquals(0, Doubles.indexOf(haystack, target));
    assertEquals(0, Doubles.indexOf(haystack, new double[0]));
    assertEquals(-1, Doubles.indexOf(new double[0], new double[] {1.0d}));
    assertEquals(0, Doubles.indexOf(haystack, haystack));

    Random random = new Random(0x31975L + 7);
    for (int trial = 0; trial < 300; trial++) {
      int n = trial % 5 == 0 ? random.nextInt(260) : 4096 + random.nextInt(2048);
      int m = random.nextInt(130);
      double[] a = new double[n];
      double[] b = new double[m];
      for (int i = 0; i < n; i++) {
        a[i] = (double) random.nextInt(5);
      }
      for (int i = 0; i < m; i++) {
        b[i] = (double) random.nextInt(5);
      }
      if (m > 0 && m <= n && trial % 3 == 0) {
        System.arraycopy(a, random.nextInt(n - m + 1), b, 0, m);
      }
      assertEquals("type=double trial=" + trial, scalarDoubles(a, b), Doubles.indexOf(a, b));
    }
  }

  public void testFloatingNanAndSignedZeroAreStillPrimitiveEquality() {
    float[] floatText = new float[8192];
    float[] floatNeedle = new float[128];
    floatNeedle[63] = Float.NaN;
    assertEquals(-1, Floats.indexOf(floatText, floatNeedle));
    floatNeedle[63] = -0.0f;
    assertEquals(0, Floats.indexOf(floatText, floatNeedle));
    floatNeedle[63] = Float.POSITIVE_INFINITY;
    floatText[63] = Float.POSITIVE_INFINITY;
    assertEquals(0, Floats.indexOf(floatText, floatNeedle));
    assertEquals(0, Floats.indexOf(new float[] {Float.NaN}, new float[0]));

    double[] doubleText = new double[8192];
    double[] doubleNeedle = new double[128];
    doubleNeedle[63] = Double.NaN;
    assertEquals(-1, Doubles.indexOf(doubleText, doubleNeedle));
    doubleNeedle[63] = -0.0d;
    assertEquals(0, Doubles.indexOf(doubleText, doubleNeedle));
    doubleNeedle[63] = Double.NEGATIVE_INFINITY;
    doubleText[63] = Double.NEGATIVE_INFINITY;
    assertEquals(0, Doubles.indexOf(doubleText, doubleNeedle));
  }

  public void testNullValidationPrecedesEmptyNeedle() {
    try {
      Bytes.indexOf(null, new byte[0]);
      fail("array parameter must be validated before needle");
    } catch (NullPointerException expected) {
      assertEquals("array", expected.getMessage());
    }
    try {
      Floats.indexOf(new float[0], null);
      fail("null needle must be rejected even for an empty array");
    } catch (NullPointerException expected) {
      assertEquals("target", expected.getMessage());
    }
  }
}

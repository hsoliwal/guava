// SPDX-License-Identifier: Apache-2.0
// Standalone benchmark using the real Guava primitive classes. NOT JMH.
package com.m3.collections.guava.primitives;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public final class M3PrimitiveKmpBenchmark {
  private static volatile long sink;
  private static final Class<?>[] TYPES = {
    byte.class, short.class, char.class, long.class, boolean.class, float.class, double.class
  };
  private static final String[] NAMES = {
    "Bytes", "Shorts", "Chars", "Longs", "Booleans", "Floats", "Doubles"
  };

  private M3PrimitiveKmpBenchmark() {}

  private static int baselineBytes(byte[] array, byte[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineShorts(short[] array, short[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineChars(char[] array, char[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineLongs(long[] array, long[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineBooleans(boolean[] array, boolean[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineFloats(float[] array, float[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int baselineDoubles(double[] array, double[] target) {
    if (target.length == 0) {
      return 0;
    }
    outer:
    for (int i = 0; i < array.length - target.length + 1; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static int search(int kind, boolean baseline, Object array, Object target) {
    switch (kind) {
      case 0:
        return baseline ? baselineBytes((byte[]) array, (byte[]) target)
            : Bytes.indexOf((byte[]) array, (byte[]) target);
      case 1:
        return baseline ? baselineShorts((short[]) array, (short[]) target)
            : Shorts.indexOf((short[]) array, (short[]) target);
      case 2:
        return baseline ? baselineChars((char[]) array, (char[]) target)
            : Chars.indexOf((char[]) array, (char[]) target);
      case 3:
        return baseline ? baselineLongs((long[]) array, (long[]) target)
            : Longs.indexOf((long[]) array, (long[]) target);
      case 4:
        return baseline ? baselineBooleans((boolean[]) array, (boolean[]) target)
            : Booleans.indexOf((boolean[]) array, (boolean[]) target);
      case 5:
        return baseline ? baselineFloats((float[]) array, (float[]) target)
            : Floats.indexOf((float[]) array, (float[]) target);
      case 6:
        return baseline ? baselineDoubles((double[]) array, (double[]) target)
            : Doubles.indexOf((double[]) array, (double[]) target);
      default: throw new AssertionError(kind);
    }
  }

  private static Object value(int kind, int x) {
    switch (kind) {
      case 0: return (byte) x;
      case 1: return (short) x;
      case 2: return (char) x;
      case 3: return (long) x;
      case 4: return (x & 1) != 0;
      case 5: return (float) x;
      case 6: return (double) x;
      default: throw new AssertionError(kind);
    }
  }

  private static long timed(int kind, boolean old, Object array, Object target, int iterations) {
    long start = System.nanoTime();
    int result = 0;
    for (int i = 0; i < iterations; i++) {
      result += search(kind, old, array, target);
    }
    long ns = System.nanoTime() - start;
    sink += result;
    return ns / iterations;
  }

  public static void main(String[] args) {
    final int length = 120000, patternLength = 128, samples = 7;
    for (int kind = 0; kind < TYPES.length; kind++) {
      for (String workload : new String[] {"periodic", "random"}) {
        Object array = Array.newInstance(TYPES[kind], length);
        Object target = Array.newInstance(TYPES[kind], patternLength);
        Random random = new Random(123457L + kind);
        for (int i = 0; i < length; i++) {
          int v = workload.equals("periodic") ? 7 : random.nextInt(1 << 24);
          Array.set(array, i, value(kind, v));
        }
        for (int i = 0; i < patternLength; i++) {
          int v = workload.equals("periodic")
              ? (i == patternLength - 1 ? (kind == 4 ? 8 : 9) : 7)
              : random.nextInt(1 << 24);
          Array.set(target, i, value(kind, v));
        }
        int expected = search(kind, true, array, target);
        int observed = search(kind, false, array, target);
        if (expected != observed) {
          throw new AssertionError("INDEX_OF_CONTRACT_DRIFT:" + NAMES[kind] + workload);
        }
        int iterations = workload.equals("periodic") ? 1 : 60;
        for (int k = 0; k < 3; k++) {
          timed(kind, true, array, target, iterations);
          timed(kind, false, array, target, iterations);
        }
        long[] oldNs = new long[samples], candidateNs = new long[samples];
        for (int i = 0; i < samples; i++) {
          if ((i & 1) == 0) {
            oldNs[i] = timed(kind, true, array, target, iterations);
            candidateNs[i] = timed(kind, false, array, target, iterations);
          } else {
            candidateNs[i] = timed(kind, false, array, target, iterations);
            oldNs[i] = timed(kind, true, array, target, iterations);
          }
        }
        Arrays.sort(oldNs);
        Arrays.sort(candidateNs);
        System.out.printf(Locale.ROOT,
            "PRIMITIVE_KMP_BENCH type=%s workload=%s length=%d needle=%d old_ns=%d new_ns=%d ratio=%.2f result=%d%n",
            NAMES[kind], workload, length, patternLength, oldNs[samples/2],
            candidateNs[samples/2], (double) oldNs[samples/2] / candidateNs[samples/2], expected);
      }
    }
    System.out.println("PRIMITIVE_KMP_BENCH_DONE sink=" + sink);
  }
}

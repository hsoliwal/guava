// SPDX-License-Identifier: Apache-2.0
// Exploratory benchmark using the actual Guava Ints class. Not JMH.
package com.m3.collections.guava.primitives;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Reproducible legacy versus adaptive array-search comparison; no library edits. */
public final class IntSubsequenceKmpBenchmark {
  private static volatile long sink;

  private IntSubsequenceKmpBenchmark() {}

  private static int baseline(int[] array, int[] target) {
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

  private static long time(int[] array, int[] target, boolean optimized, int expected) {
    long started = System.nanoTime();
    int result = optimized ? Ints.indexOf(array, target) : baseline(array, target);
    long elapsed = System.nanoTime() - started;
    if (result != expected) {
      throw new AssertionError("PRIMITIVE_INT_BENCH_RESULT_MISMATCH");
    }
    sink += result;
    return elapsed;
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      throw new IllegalArgumentException("USAGE n m periodic|random|initial samples");
    }
    int n = Integer.parseInt(args[0]);
    int m = Integer.parseInt(args[1]);
    String workload = args[2];
    int samples = Integer.parseInt(args[3]);
    if (n <= 0 || m <= 0 || m > n || samples < 3
        || (!workload.equals("periodic") && !workload.equals("random")
            && !workload.equals("initial"))) {
      throw new IllegalArgumentException("INVALID_ARGUMENTS");
    }
    int[] array = new int[n];
    int[] target = new int[m];
    int expected = -1;
    if (workload.equals("periodic")) {
      Arrays.fill(array, 7);
      Arrays.fill(target, 7);
      target[m - 1] = 9;
    } else {
      Random random = new Random(987654321L);
      for (int i = 0; i < n; i++) {
        array[i] = random.nextInt();
      }
      for (int i = 0; i < m; i++) {
        target[i] = random.nextInt();
      }
      if (workload.equals("initial")) {
        System.arraycopy(target, 0, array, 0, m);
        expected = 0;
      }
    }
    for (int warmup = 0; warmup < 3; warmup++) {
      time(array, target, false, expected);
      time(array, target, true, expected);
    }
    long[] before = new long[samples];
    long[] after = new long[samples];
    for (int i = 0; i < samples; i++) {
      if ((i & 1) == 0) {
        before[i] = time(array, target, false, expected);
        after[i] = time(array, target, true, expected);
      } else {
        after[i] = time(array, target, true, expected);
        before[i] = time(array, target, false, expected);
      }
    }
    Arrays.sort(before);
    Arrays.sort(after);
    System.out.printf(Locale.ROOT,
        "INT_ADAPTIVE_BENCH n=%d m=%d workload=%s baseline_median_ns=%d"
            + " optimized_median_ns=%d samples=%d sink=%d%n",
        n, m, workload, before[samples / 2], after[samples / 2], samples, sink);
  }
}

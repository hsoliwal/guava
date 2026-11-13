// SPDX-License-Identifier: Apache-2.0
// Exploratory source-level benchmark against the actual fork Ints implementation; not JMH.
import com.m3.collections.guava.primitives.Ints;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Baseline nested-loop versus optimized Ints.indexOf(int[], int[]). */
public final class IntsKmpBenchmark {
    private static volatile int sink;

    private static int original(int[] array, int[] target) {
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

    private static long measure(int[] array, int[] target, boolean optimized) {
        long started = System.nanoTime();
        int index = optimized ? Ints.indexOf(array, target) : original(array, target);
        long elapsed = System.nanoTime() - started;
        if (index != -1) {
            throw new AssertionError("EXPECTED_SUBSEQUENCE_ABSENT");
        }
        sink += index;
        return elapsed;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("USAGE: arrayLength needleLength periodic|random");
        }
        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        boolean periodic = args[2].equals("periodic");
        if (n < 1 || m < 1 || m > n) {
            throw new IllegalArgumentException("INVALID_BENCH_LENGTH");
        }
        int[] array = new int[n];
        int[] needle = new int[m];
        if (periodic) {
            Arrays.fill(array, 7);
            Arrays.fill(needle, 7);
            needle[m - 1] = 8;
        } else {
            Random random = new Random(987654321L);
            for (int i = 0; i < n; i++) {
                array[i] = random.nextInt();
            }
            for (int i = 0; i < m; i++) {
                needle[i] = random.nextInt();
            }
        }
        for (int i = 0; i < 2; i++) {
            measure(array, needle, false);
            measure(array, needle, true);
        }
        long[] old = new long[7];
        long[] candidate = new long[7];
        for (int i = 0; i < old.length; i++) {
            if ((i & 1) == 0) {
                old[i] = measure(array, needle, false);
                candidate[i] = measure(array, needle, true);
            } else {
                candidate[i] = measure(array, needle, true);
                old[i] = measure(array, needle, false);
            }
        }
        Arrays.sort(old);
        Arrays.sort(candidate);
        System.out.printf(Locale.ROOT,
                "INTS_KMP_BENCH n=%d m=%d kind=%s old_median_ns=%d"
                        + " optimized_median_ns=%d samples=7 sink=%d%n",
                n, m, args[2], old[3], candidate[3], sink);
    }
}

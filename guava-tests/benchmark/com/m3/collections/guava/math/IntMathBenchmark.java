/*
 * Copyright (C) 2011 The Guava Authors
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

package com.m3.collections.guava.math;

import static com.m3.collections.guava.math.MathBenchmarking.ARRAY_MASK;
import static com.m3.collections.guava.math.MathBenchmarking.ARRAY_SIZE;
import static com.m3.collections.guava.math.MathBenchmarking.RANDOM_SOURCE;
import static com.m3.collections.guava.math.MathBenchmarking.randomExponent;
import static com.m3.collections.guava.math.MathBenchmarking.randomNonNegativeBigInteger;
import static com.m3.collections.guava.math.MathBenchmarking.randomPositiveBigInteger;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import org.jspecify.annotations.NullUnmarked;

/**
 * Benchmarks for the non-rounding methods of {@code IntMath}.
 *
 * @author Louis Wasserman
 */
@NullUnmarked
public class IntMathBenchmark {
  private static int[] exponent = new int[ARRAY_SIZE];
  private static int[] factorial = new int[ARRAY_SIZE];
  private static int[] binomial = new int[ARRAY_SIZE];
  private static final int[] positive = new int[ARRAY_SIZE];
  private static final int[] nonnegative = new int[ARRAY_SIZE];
  private static final int[] ints = new int[ARRAY_SIZE];

  @BeforeExperiment
  void setUp() {
    for (int i = 0; i < ARRAY_SIZE; i++) {
      exponent[i] = randomExponent();
      factorial[i] = RANDOM_SOURCE.nextInt(50);
      binomial[i] = RANDOM_SOURCE.nextInt(factorial[i] + 1);
      positive[i] = randomPositiveBigInteger(Integer.SIZE - 1).intValue();
      nonnegative[i] = randomNonNegativeBigInteger(Integer.SIZE - 1).intValue();
      ints[i] = RANDOM_SOURCE.nextInt();
    }
  }

  @Benchmark
  int pow(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      tmp += IntMath.pow(positive[j], exponent[j]);
    }
    return tmp;
  }

  @Benchmark
  int mod(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      tmp += IntMath.mod(ints[j], positive[j]);
    }
    return tmp;
  }

  @Benchmark
  int gCD(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      tmp += IntMath.gcd(nonnegative[j], positive[j]);
    }
    return tmp;
  }

  @Benchmark
  int factorial(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      tmp += IntMath.factorial(factorial[j]);
    }
    return tmp;
  }

  @Benchmark
  int binomial(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      tmp += IntMath.binomial(factorial[j], binomial[j]);
    }
    return tmp;
  }

  @Benchmark
  int isPrime(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      int j = i & ARRAY_MASK;
      if (IntMath.isPrime(positive[j])) {
        tmp++;
      }
    }
    return tmp;
  }
}

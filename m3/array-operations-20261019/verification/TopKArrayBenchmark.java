// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.collections.guava.collect;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TopKArrayBenchmark {
    @Param({"1", "8", "64", "1024"}) public int k;
    private Integer[] input;
    @Setup public void setup() {
        input = new Integer[4 * k];
        for (int i = 0; i < input.length; i++) input[i] = input.length - i;
    }
    @Benchmark public Object select() {
        TopKSelector<Integer> top = TopKSelector.least(k, Comparator.naturalOrder());
        for (Integer value : input) top.offer(value);
        return top.topK();
    }
}

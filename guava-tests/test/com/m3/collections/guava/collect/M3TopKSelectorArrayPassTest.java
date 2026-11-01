/*
 * Copyright 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections.guava.collect;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Same deterministic selector proof is used for relocated JRE and Android sources. */
public final class M3TopKSelectorArrayPassTest {
  @Test
  public void quickselectAndCombineMatchSortedOracleWithDuplicates() {
    for (int k = 0; k <= 32; k++) {
      Random random = new Random(0x5eedL + k);
      List<Integer> data = new ArrayList<>();
      TopKSelector<Integer> sequential = TopKSelector.least(k, Comparator.naturalOrder());
      TopKSelector<Integer> bulk = TopKSelector.least(k, Comparator.naturalOrder());
      TopKSelector<Integer> left = TopKSelector.least(k, Comparator.naturalOrder());
      TopKSelector<Integer> right = TopKSelector.least(k, Comparator.naturalOrder());
      for (int i = 0; i < 512; i++) {
        Integer value = random.nextInt(37) - 18;
        data.add(value);
        sequential.offer(value);
        if ((i & 1) == 0) {
          left.offer(value);
        } else {
          right.offer(value);
        }
      }
      bulk.offerAll(data);
      left.combine(right);
      Collections.sort(data);
      List<Integer> expected = data.subList(0, k);
      assertEquals(expected, sequential.topK());
      assertEquals(expected, bulk.topK());
      assertEquals(expected, left.topK());

      List<Integer> detached = sequential.topK();
      sequential.offer(-1_000);
      assertEquals(expected, detached);
      List<Integer> updated = new ArrayList<>(expected);
      if (k > 0) {
        updated.set(k - 1, -1_000);
        Collections.sort(updated);
      }
      assertEquals(updated, sequential.topK());
    }
  }
}

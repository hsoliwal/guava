/*
 * Copyright (C) 2026 Contributors to the Synexia Project.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.m3.collections.guava.primitives;

import java.util.Arrays;
import java.util.Random;
import junit.framework.TestCase;

/** First-match, mutation, and bounded adaptive KMP parity for the existing Ints API. */
public final class M3IntSubsequenceKmpCompletionTest extends TestCase {
  private static int originalIndexOf(int[] array, int[] target) {
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

  public void testSeededOriginalParityAcrossAdaptiveBoundary() {
    Random random = new Random(0x51eefeedL);
    int[] lengths = {0, 1, 31, 32, 4095, 4096, 16384, 32768};
    int[] needles = {0, 1, 2, 31, 32, 33, 128, 512, 1024};
    for (int length : lengths) {
      for (int needleLength : needles) {
        for (int trial = 0; trial < 12; trial++) {
          int[] array = new int[length];
          int[] target = new int[needleLength];
          for (int i = 0; i < length; i++) {
            array[i] = random.nextInt(5) - 2;
          }
          for (int i = 0; i < needleLength; i++) {
            target[i] = random.nextInt(5) - 2;
          }
          int[] originalArray = array.clone();
          int[] originalTarget = target.clone();
          assertEquals("array=" + length + " target=" + needleLength,
              originalIndexOf(array, target), Ints.indexOf(array, target));
          assertTrue(Arrays.equals(originalArray, array));
          assertTrue(Arrays.equals(originalTarget, target));
        }
      }
    }
  }

  public void testLargeRepeatedPrefixFirstAndLaterMatch() {
    int[] array = new int[65_536];
    int[] target = new int[512];
    Arrays.fill(array, 7);
    Arrays.fill(target, 7);
    target[511] = 9;
    assertEquals(-1, Ints.indexOf(array, target));
    array[10_000 + 511] = 9;
    array[40_000 + 511] = 9;
    assertEquals(10_000, Ints.indexOf(array, target));
    assertEquals(originalIndexOf(array, target), Ints.indexOf(array, target));
    array[10_000 + 511] = 7;
    assertEquals(40_000, Ints.indexOf(array, target));
  }

  public void testNonrepeatingNeedleAndFirstIndexZero() {
    int[] array = new int[16_384];
    int[] target = new int[128];
    for (int i = 0; i < target.length; i++) {
      target[i] = i + 10_000;
    }
    Arrays.fill(array, -1);
    assertEquals(-1, Ints.indexOf(array, target));
    System.arraycopy(target, 0, array, 0, target.length);
    System.arraycopy(target, 0, array, 2_000, target.length);
    assertEquals(0, Ints.indexOf(array, target));
    array[0] = -1;
    assertEquals(2_000, Ints.indexOf(array, target));
  }

  public void testOverlapsSignedExtremesAndAliasing() {
    int[] repeated = new int[16_384];
    int[] needle = new int[32];
    for (int i = 0; i < repeated.length; i++) {
      repeated[i] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    for (int i = 0; i < needle.length; i++) {
      needle[i] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    needle[31] = 0;
    assertEquals(-1, Ints.indexOf(repeated, needle));
    repeated[4096 + 31] = 0;
    assertEquals(4096, Ints.indexOf(repeated, needle));
    assertEquals(0, Ints.indexOf(repeated, repeated));
    assertEquals(0, Ints.indexOf(new int[0], new int[0]));
    assertEquals(-1, Ints.indexOf(new int[0], new int[] {1}));
  }

  public void testMaximumKmpBudgetAndFallbackOverBudget() {
    int[] haystack = new int[65_536];
    int[] target = new int[65_536];
    Arrays.fill(haystack, -8);
    Arrays.fill(target, -8);
    target[65_535] = 11;
    assertEquals(-1, Ints.indexOf(haystack, target));
    haystack[65_535] = 11;
    assertEquals(0, Ints.indexOf(haystack, target));
    int[] bigger = new int[65_537];
    Arrays.fill(bigger, -8);
    bigger[65_536] = 11;
    int[] oneLonger = new int[65_537];
    Arrays.fill(oneLonger, -8);
    oneLonger[65_536] = 11;
    assertEquals(0, Ints.indexOf(bigger, oneLonger));
    assertEquals(0, originalIndexOf(bigger, oneLonger));
  }

  public void testNullChecksRetainOriginalPrecedence() {
    try {
      Ints.indexOf(null, null);
      fail("Both null arguments must fail at array");
    } catch (NullPointerException expected) {
      assertEquals("array", expected.getMessage());
    }
    try {
      Ints.indexOf(new int[0], null);
      fail("Null needle must fail");
    } catch (NullPointerException expected) {
      assertEquals("target", expected.getMessage());
    }
  }
}

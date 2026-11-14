/*
 * Copyright (C) 2026 Contributors to the Synexia Project.
 * Licensed under the Apache License, Version 2.0.
 */
package com.m3.collections.guava.primitives;

import java.util.Arrays;
import java.util.Random;
import junit.framework.TestCase;

/** Differential tests for bounded primitive KMP without changing the public Ints API. */
public final class IntsPrimitiveKmpTest extends TestCase {
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

  public void testSeededFirstMatchParityAtEveryGate() {
    Random random = new Random(0x5eede21L);
    int[] lengths = {0, 1, 31, 32, 4095, 4096, 8192};
    int[] needles = {0, 1, 2, 31, 32, 33, 64, 128, 1024};
    for (int length : lengths) {
      for (int needleLength : needles) {
        for (int trial = 0; trial < 80; trial++) {
          int[] array = new int[length];
          int[] target = new int[needleLength];
          for (int i = 0; i < length; i++) {
            array[i] = random.nextInt(5) - 2;
          }
          for (int i = 0; i < needleLength; i++) {
            target[i] = random.nextInt(5) - 2;
          }
          int expected = originalIndexOf(array, target);
          assertEquals("length=" + length + " needle=" + needleLength
                  + " trial=" + trial, expected, Ints.indexOf(array, target));
        }
      }
    }
  }

  public void testRepeatedPrefixAdversarialAndFirstHit() {
    int[] array = new int[80_000];
    int[] target = new int[128];
    Arrays.fill(array, 7);
    Arrays.fill(target, 7);
    target[target.length - 1] = 8;
    assertEquals(-1, Ints.indexOf(array, target));
    array[45_000 + target.length - 1] = 8;
    array[60_000 + target.length - 1] = 8;
    assertEquals(45_000, Ints.indexOf(array, target));
    assertEquals(45_000, originalIndexOf(array, target));
  }

  public void testOverlappingPrefixesAndExtremes() {
    int[] target = {Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0,
            Integer.MIN_VALUE, Integer.MAX_VALUE};
    int[] array = new int[8192];
    Arrays.fill(array, Integer.MIN_VALUE);
    for (int i = 3_000; i < 3_006; i++) {
      array[i] = target[i - 3_000];
    }
    assertEquals(3_000, Ints.indexOf(array, target));
    assertEquals(originalIndexOf(array, target), Ints.indexOf(array, target));
    assertEquals(0, Ints.indexOf(array, array));
    assertEquals(0, Ints.indexOf(new int[0], new int[0]));
    assertEquals(-1, Ints.indexOf(new int[0], new int[]{1}));
  }

  public void testExcessiveNeedleRetainsBoundedFallback() {
    int[] array = new int[75_000];
    int[] target = new int[65_537]; // Beyond the 256 KiB failure-link ceiling.
    Arrays.fill(array, 11);
    Arrays.fill(target, 11);
    target[target.length - 1] = 12;
    assertEquals(-1, Ints.indexOf(array, target));
    array[target.length - 1] = 12;
    assertEquals(0, Ints.indexOf(array, target));
    assertEquals(0, originalIndexOf(array, target));
  }

  public void testNullArgumentAdmissionOrder() {
    try {
      Ints.indexOf(null, new int[0]);
      fail("Null haystack must fail");
    } catch (NullPointerException expected) {
      assertNotNull(expected);
    }
    try {
      Ints.indexOf(new int[0], null);
      fail("Null needle must fail");
    } catch (NullPointerException expected) {
      assertNotNull(expected);
    }
  }
}

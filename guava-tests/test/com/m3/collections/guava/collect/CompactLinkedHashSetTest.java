/*
 * Copyright (C) 2012 The Guava Authors
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

package com.m3.collections.guava.collect;

import static com.m3.collections.guava.truth.Truth.assertThat;
import static java.lang.Math.max;
import static java.util.Arrays.asList;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.collect.testing.SetTestSuiteBuilder;
import com.m3.collections.guava.collect.testing.TestStringSetGenerator;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import com.m3.collections.guava.collect.testing.features.Feature;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.List;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jspecify.annotations.NullUnmarked;

/**
 * Tests for CompactLinkedHashSet.
 *
 * @author Dimitris Andreou
 */
@GwtIncompatible // java.util.Arrays#copyOf(Object[], int), java.lang.reflect.Array
@NullUnmarked
public class CompactLinkedHashSetTest extends TestCase {
  @AndroidIncompatible // test-suite builders
  public static Test suite() {
    List<Feature<?>> allFeatures =
        Arrays.asList(
            CollectionSize.ANY,
            CollectionFeature.ALLOWS_NULL_VALUES,
            CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
            CollectionFeature.GENERAL_PURPOSE,
            CollectionFeature.REMOVE_OPERATIONS,
            CollectionFeature.SERIALIZABLE,
            CollectionFeature.KNOWN_ORDER,
            CollectionFeature.SUPPORTS_ADD,
            CollectionFeature.SUPPORTS_REMOVE);

    TestSuite suite = new TestSuite();
    suite.addTestSuite(CompactLinkedHashSetTest.class);
    suite.addTest(
        SetTestSuiteBuilder.using(
                new TestStringSetGenerator() {
                  @Override
                  protected Set<String> create(String[] elements) {
                    return CompactLinkedHashSet.create(asList(elements));
                  }
                })
            .named("CompactLinkedHashSet")
            .withFeatures(allFeatures)
            .createTestSuite());
    suite.addTest(
        SetTestSuiteBuilder.using(
                new TestStringSetGenerator() {
                  @Override
                  protected Set<String> create(String[] elements) {
                    CompactLinkedHashSet<String> set = CompactLinkedHashSet.create();
                    set.convertToHashFloodingResistantImplementation();
                    Collections.addAll(set, elements);
                    return set;
                  }
                })
            .named("CompactLinkedHashSet with flooding protection")
            .withFeatures(allFeatures)
            .createTestSuite());
    return suite;
  }

  /**
   * Repeated clear must not make stale primitive predecessor/successor entries observable.
   * The source uses array-backed linked indices rather than retained node objects.
   */
  public void testClearRebindsOwnedIndexLanesInInsertionOrder() {
    Random random = new Random(0x1eed5eedL);
    for (int capacity : new int[] {0, 1, 2, 7, 8, 31, 32, 64, 256}) {
      CompactLinkedHashSet<Integer> actual = CompactLinkedHashSet.createWithExpectedSize(capacity);
      LinkedHashSet<Integer> expected = new LinkedHashSet<>();
      for (int wave = 0; wave < 128; wave++) {
        for (int step = 0; step < 36; step++) {
          Integer value = random.nextInt(16) == 0 ? null : random.nextInt(97) - 48;
          switch (random.nextInt(4)) {
            case 0:
            case 1:
              assertEquals(expected.add(value), actual.add(value));
              break;
            case 2:
              assertEquals(expected.remove(value), actual.remove(value));
              break;
            default:
              assertEquals(expected.contains(value), actual.contains(value));
          }
        }
        assertEquals(expected, actual);
        assertTrue(Arrays.equals(expected.toArray(), actual.toArray()));
        assertTrue(Arrays.equals(expected.toArray(new Integer[0]), actual.toArray(new Integer[0])));

        // Both clear paths must leave an empty iteration and release reference payloads.
        actual.clear();
        expected.clear();
        assertTrue(actual.isEmpty());
        assertEquals(0, actual.toArray().length);

        // A cleared physical entry is subsequently reused, including after shrinkage.
        if ((wave & 7) == 0) {
          actual.trimToSize();
        }
        Integer replacement = wave == 0 ? null : Integer.valueOf(wave - 64);
        assertEquals(expected.add(replacement), actual.add(replacement));
        assertTrue(Arrays.equals(expected.toArray(), actual.toArray()));
        actual.clear();
        expected.clear();
      }
    }
  }

  /** Hash-flooding delegate mode still follows the original super.clear() contract. */
  public void testClearAfterHashFloodingConversionAndReinsertion() {
    CompactLinkedHashSet<String> actual = CompactLinkedHashSet.create();
    LinkedHashSet<String> expected = new LinkedHashSet<>();
    for (int i = 0; i < 64; i++) {
      actual.add(Integer.toString(i));
      expected.add(Integer.toString(i));
    }
    actual.convertToHashFloodingResistantImplementation();
    assertTrue(Arrays.equals(expected.toArray(), actual.toArray()));
    actual.clear();
    expected.clear();
    actual.add(null);
    expected.add(null);
    actual.add("again");
    expected.add("again");
    assertTrue(Arrays.equals(expected.toArray(), actual.toArray()));
    assertEquals(expected, actual);
  }

  public void testAllocArraysDefault() {
    CompactHashSet<Integer> set = CompactHashSet.create();
    assertThat(set.needsAllocArrays()).isTrue();
    assertThat(set.elements).isNull();

    set.add(1);
    assertThat(set.needsAllocArrays()).isFalse();
    assertThat(set.elements).hasLength(CompactHashing.DEFAULT_SIZE);
  }

  public void testAllocArraysExpectedSize() {
    for (int i = 0; i <= CompactHashing.DEFAULT_SIZE; i++) {
      CompactHashSet<Integer> set = CompactHashSet.createWithExpectedSize(i);
      assertThat(set.needsAllocArrays()).isTrue();
      assertThat(set.elements).isNull();

      set.add(1);
      assertThat(set.needsAllocArrays()).isFalse();
      int expectedSize = max(1, i);
      assertThat(set.elements).hasLength(expectedSize);
    }
  }
}

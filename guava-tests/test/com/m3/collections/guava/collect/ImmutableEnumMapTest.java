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

import static com.m3.collections.guava.base.Preconditions.checkState;
import static com.m3.collections.guava.collect.Maps.toImmutableEnumMap;
import static com.m3.collections.guava.collect.ReflectionFreeAssertThrows.assertThrows;
import static com.m3.collections.guava.collect.testing.Helpers.mapEntry;
import static com.m3.collections.guava.collect.testing.features.CollectionFeature.ALLOWS_NULL_QUERIES;
import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SERIALIZABLE;
import static com.google.common.truth.Truth.assertThat;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.base.Equivalence;
import com.m3.collections.guava.base.Function;
import com.m3.collections.guava.collect.testing.AnEnum;
import com.m3.collections.guava.collect.testing.MapTestSuiteBuilder;
import com.m3.collections.guava.collect.testing.TestEnumMapGenerator;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import com.m3.collections.guava.testing.CollectorTester;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Stream;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jspecify.annotations.NullMarked;

/**
 * Tests for {@code ImmutableEnumMap}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible(emulated = true)
@NullMarked
public class ImmutableEnumMapTest extends TestCase {
  @J2ktIncompatible
  @AndroidIncompatible // test-suite builders
  public static class ImmutableEnumMapGenerator extends TestEnumMapGenerator {
    @Override
    protected Map<AnEnum, String> create(Entry<AnEnum, String>[] entries) {
      Map<AnEnum, String> map = Maps.newHashMap();
      for (Entry<AnEnum, String> entry : entries) {
        map.put(entry.getKey(), entry.getValue());
      }
      return Maps.immutableEnumMap(map);
    }
  }

  @J2ktIncompatible
  @GwtIncompatible // suite
  @AndroidIncompatible // test-suite builders
  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(
        MapTestSuiteBuilder.using(new ImmutableEnumMapGenerator())
            .named("Maps.immutableEnumMap")
            .withFeatures(CollectionSize.ANY, SERIALIZABLE, ALLOWS_NULL_QUERIES)
            .createTestSuite());
    suite.addTestSuite(ImmutableEnumMapTest.class);
    return suite;
  }

  public void testIteratesOnce() {
    Map<AnEnum, AnEnum> map =
        Maps.asMap(
            ImmutableSet.of(AnEnum.A),
            new Function<AnEnum, AnEnum>() {
              boolean used = false;

              @Override
              public AnEnum apply(AnEnum ae) {
                checkState(!used, "should not be applied more than once");
                used = true;
                return ae;
              }
            });
    ImmutableMap<AnEnum, AnEnum> copy = Maps.immutableEnumMap(map);
    assertThat(copy.entrySet()).containsExactly(mapEntry(AnEnum.A, AnEnum.A));
  }

  public void testEmptyImmutableEnumMap() {
    ImmutableMap<AnEnum, String> map = Maps.immutableEnumMap(ImmutableMap.<AnEnum, String>of());
    assertEquals(ImmutableMap.of(), map);
  }

  public void testImmutableEnumMapOrdering() {
    ImmutableMap<AnEnum, String> map =
        Maps.immutableEnumMap(ImmutableMap.of(AnEnum.C, "c", AnEnum.A, "a", AnEnum.E, "e"));

    assertThat(map.entrySet())
        .containsExactly(mapEntry(AnEnum.A, "a"), mapEntry(AnEnum.C, "c"), mapEntry(AnEnum.E, "e"))
        .inOrder();
  }

  public void testToImmutableEnumMap() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        toImmutableEnumMap(Entry::getKey, Entry::getValue);
    Equivalence<ImmutableMap<AnEnum, Integer>> equivalence =
        Equivalence.equals().<Entry<AnEnum, Integer>>pairwise().onResultOf(ImmutableMap::entrySet);
    CollectorTester.of(collector, equivalence)
        .expectCollects(
            ImmutableMap.of(AnEnum.A, 1, AnEnum.C, 2, AnEnum.E, 3),
            mapEntry(AnEnum.A, 1),
            mapEntry(AnEnum.C, 2),
            mapEntry(AnEnum.E, 3));
  }

  public void testToImmutableMap_exceptionOnDuplicateKey() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        toImmutableEnumMap(Entry::getKey, Entry::getValue);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(mapEntry(AnEnum.A, 1), mapEntry(AnEnum.A, 11)).collect(collector));
  }

  public void testToImmutableMapMerging() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        toImmutableEnumMap(Entry::getKey, Entry::getValue, Integer::sum);
    Equivalence<ImmutableMap<AnEnum, Integer>> equivalence =
        Equivalence.equals().<Entry<AnEnum, Integer>>pairwise().onResultOf(ImmutableMap::entrySet);
    CollectorTester.of(collector, equivalence)
        .expectCollects(
            ImmutableMap.of(AnEnum.A, 1, AnEnum.B, 4, AnEnum.C, 3),
            mapEntry(AnEnum.A, 1),
            mapEntry(AnEnum.B, 2),
            mapEntry(AnEnum.C, 3),
            mapEntry(AnEnum.B, 2));
  }
}

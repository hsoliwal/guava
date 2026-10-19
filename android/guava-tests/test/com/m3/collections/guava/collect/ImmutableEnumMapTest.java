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
import static com.m3.collections.guava.collect.Maps.asMap;
import static com.m3.collections.guava.collect.Maps.immutableEnumMap;
import static com.m3.collections.guava.collect.testing.Helpers.mapEntry;
import static com.m3.collections.guava.collect.testing.features.CollectionFeature.ALLOWS_NULL_QUERIES;
import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SERIALIZABLE;
import static com.m3.collections.guava.truth.Truth.assertThat;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.base.Function;
import com.m3.collections.guava.collect.testing.AnEnum;
import com.m3.collections.guava.collect.testing.MapTestSuiteBuilder;
import com.m3.collections.guava.collect.testing.TestEnumMapGenerator;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jspecify.annotations.NullMarked;

/**
 * Tests for {@code ImmutableEnumMap}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
@NullMarked
public class ImmutableEnumMapTest extends TestCase {
  @J2ktIncompatible
  @AndroidIncompatible // test-suite builders
  public static class ImmutableEnumMapGenerator extends TestEnumMapGenerator {
    @Override
    protected Map<AnEnum, String> create(Entry<AnEnum, String>[] entries) {
      Map<AnEnum, String> map = new HashMap<>();
      for (Entry<AnEnum, String> entry : entries) {
        map.put(entry.getKey(), entry.getValue());
      }
      return immutableEnumMap(map);
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
        asMap(
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
    ImmutableMap<AnEnum, AnEnum> copy = immutableEnumMap(map);
    assertThat(copy.entrySet()).containsExactly(mapEntry(AnEnum.A, AnEnum.A));
  }

  public void testEmptyImmutableEnumMap() {
    ImmutableMap<AnEnum, String> map = immutableEnumMap(ImmutableMap.<AnEnum, String>of());
    assertEquals(ImmutableMap.of(), map);
  }

  public void testImmutableEnumMapOrdering() {
    ImmutableMap<AnEnum, String> map =
        immutableEnumMap(ImmutableMap.of(AnEnum.C, "c", AnEnum.A, "a", AnEnum.E, "e"));

    assertThat(map.entrySet())
        .containsExactly(mapEntry(AnEnum.A, "a"), mapEntry(AnEnum.C, "c"), mapEntry(AnEnum.E, "e"))
        .inOrder();
  }
}

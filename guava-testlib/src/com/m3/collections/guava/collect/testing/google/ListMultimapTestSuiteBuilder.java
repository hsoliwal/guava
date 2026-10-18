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

package com.m3.collections.guava.collect.testing.google;

import static com.m3.collections.guava.collect.testing.Helpers.copyToList;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.collect.ListMultimap;
import com.m3.collections.guava.collect.testing.AbstractTester;
import com.m3.collections.guava.collect.testing.FeatureSpecificTestSuiteBuilder;
import com.m3.collections.guava.collect.testing.ListTestSuiteBuilder;
import com.m3.collections.guava.collect.testing.OneSizeTestContainerGenerator;
import com.m3.collections.guava.collect.testing.TestListGenerator;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import com.m3.collections.guava.collect.testing.features.Feature;
import com.m3.collections.guava.collect.testing.features.ListFeature;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import junit.framework.TestSuite;

/**
 * Creates, based on your criteria, a JUnit test suite that exhaustively tests a {@code
 * ListMultimap} implementation.
 *
 * @author Louis Wasserman
 */
@GwtIncompatible
public class ListMultimapTestSuiteBuilder<K, V>
    extends MultimapTestSuiteBuilder<K, V, ListMultimap<K, V>> {

  public static <K, V> ListMultimapTestSuiteBuilder<K, V> using(
      TestListMultimapGenerator<K, V> generator) {
    ListMultimapTestSuiteBuilder<K, V> result = new ListMultimapTestSuiteBuilder<>();
    result.usingGenerator(generator);
    return result;
  }

  @SuppressWarnings("rawtypes") // class literals
  @Override
  protected List<Class<? extends AbstractTester>> getTesters() {
    List<Class<? extends AbstractTester>> testers = copyToList(super.getTesters());
    testers.add(ListMultimapAsMapTester.class);
    testers.add(ListMultimapEqualsTester.class);
    testers.add(ListMultimapPutTester.class);
    testers.add(ListMultimapPutAllTester.class);
    testers.add(ListMultimapRemoveTester.class);
    testers.add(ListMultimapReplaceValuesTester.class);
    return testers;
  }

  @Override
  TestSuite computeMultimapGetTestSuite(
      FeatureSpecificTestSuiteBuilder<
              ?, ? extends OneSizeTestContainerGenerator<ListMultimap<K, V>, Entry<K, V>>>
          parentBuilder) {
    return ListTestSuiteBuilder.using(
            new MultimapGetGenerator<K, V>(parentBuilder.getSubjectGenerator()))
        .withFeatures(computeMultimapGetFeatures(parentBuilder.getFeatures()))
        .named(parentBuilder.getName() + ".get[key]")
        .suppressing(parentBuilder.getSuppressedTests())
        .createTestSuite();
  }

  @Override
  TestSuite computeMultimapAsMapGetTestSuite(
      FeatureSpecificTestSuiteBuilder<
              ?, ? extends OneSizeTestContainerGenerator<ListMultimap<K, V>, Entry<K, V>>>
          parentBuilder) {
    Set<Feature<?>> features = computeMultimapAsMapGetFeatures(parentBuilder.getFeatures());
    if (Collections.disjoint(features, EnumSet.allOf(CollectionSize.class))) {
      return new TestSuite();
    } else {
      return ListTestSuiteBuilder.using(
              new MultimapAsMapGetGenerator<K, V>(parentBuilder.getSubjectGenerator()))
          .withFeatures(features)
          .named(parentBuilder.getName() + ".asMap[].get[key]")
          .suppressing(parentBuilder.getSuppressedTests())
          .createTestSuite();
    }
  }

  @Override
  final Set<Feature<?>> computeMultimapGetFeatures(Set<Feature<?>> multimapFeatures) {
    Set<Feature<?>> derivedFeatures = super.computeMultimapGetFeatures(multimapFeatures);
    if (derivedFeatures.contains(CollectionFeature.SUPPORTS_ADD)) {
      derivedFeatures.add(ListFeature.SUPPORTS_ADD_WITH_INDEX);
    }
    if (derivedFeatures.contains(CollectionFeature.SUPPORTS_REMOVE)) {
      derivedFeatures.add(ListFeature.SUPPORTS_REMOVE_WITH_INDEX);
    }
    if (derivedFeatures.contains(CollectionFeature.GENERAL_PURPOSE)) {
      derivedFeatures.add(ListFeature.GENERAL_PURPOSE);
    }
    return derivedFeatures;
  }

  private static final class MultimapGetGenerator<K, V>
      extends MultimapTestSuiteBuilder.MultimapGetGenerator<K, V, ListMultimap<K, V>>
      implements TestListGenerator<V> {
    MultimapGetGenerator(
        OneSizeTestContainerGenerator<ListMultimap<K, V>, Entry<K, V>> multimapGenerator) {
      super(multimapGenerator);
    }

    @Override
    public List<V> create(Object... elements) {
      return (List<V>) super.create(elements);
    }
  }

  private static final class MultimapAsMapGetGenerator<K, V>
      extends MultimapTestSuiteBuilder.MultimapAsMapGetGenerator<K, V, ListMultimap<K, V>>
      implements TestListGenerator<V> {
    MultimapAsMapGetGenerator(
        OneSizeTestContainerGenerator<ListMultimap<K, V>, Entry<K, V>> multimapGenerator) {
      super(multimapGenerator);
    }

    @Override
    public List<V> create(Object... elements) {
      return (List<V>) super.create(elements);
    }
  }
}

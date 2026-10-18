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

package com.m3.collections.guava.collect.testing.testers;

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.SERIALIZABLE;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.AbstractMapTester;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.testing.EqualsTester;
import com.m3.collections.guava.testing.SerializableTester;
import java.util.Map;
import org.junit.Ignore;

/**
 * Basic serialization test for maps.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
@Ignore("test runners must not instantiate and run this directly, only via suites we build")
// @Ignore affects the Android test runner, which respects JUnit 4 annotations on JUnit 3 tests.
@SuppressWarnings("JUnit4ClassUsedInJUnit3")
public class MapSerializationTester<K, V> extends AbstractMapTester<K, V> {
  @CollectionFeature.Require(SERIALIZABLE)
  public void testReserializeMap() {
    Map<K, V> deserialized = SerializableTester.reserialize(getMap());
    new EqualsTester().addEqualityGroup(getMap(), deserialized).testEquals();
  }
}

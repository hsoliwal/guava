/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.m3.collections.guava.collect.testing.google;

import static com.m3.collections.guava.collect.testing.Helpers.mapEntry;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.SEVERAL;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.SetMultimap;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import com.m3.collections.guava.testing.EqualsTester;

/**
 * Testers for {@link SetMultimap#equals(Object)}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
public class SetMultimapEqualsTester<K, V> extends AbstractMultimapTester<K, V, SetMultimap<K, V>> {
  @CollectionSize.Require(SEVERAL)
  public void testOrderingDoesntAffectEqualsComparisons() {
    SetMultimap<K, V> multimap1 =
        getSubjectGenerator()
            .create(mapEntry(k0(), v0()), mapEntry(k0(), v1()), mapEntry(k0(), v4()));
    SetMultimap<K, V> multimap2 =
        getSubjectGenerator()
            .create(mapEntry(k0(), v1()), mapEntry(k0(), v0()), mapEntry(k0(), v4()));
    new EqualsTester().addEqualityGroup(multimap1, multimap2).testEquals();
  }
}

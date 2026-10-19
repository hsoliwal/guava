/*
 * Copyright (C) 2008 The Guava Authors
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

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.base.Joiner;
import com.m3.collections.guava.collect.testing.MapInterfaceTest;
import com.m3.collections.guava.collect.testing.MinimalSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.NullUnmarked;

@GwtCompatible
@NullUnmarked
abstract class AbstractImmutableMapMapInterfaceTest<K, V> extends MapInterfaceTest<K, V> {
  AbstractImmutableMapMapInterfaceTest() {
    super(false, false, false, false, false);
  }

  @Override
  protected Map<K, V> makeEmptyMap() {
    throw new UnsupportedOperationException();
  }

  private static final Joiner JOINER = Joiner.on(", ");

  @Override
  protected final void assertMoreInvariants(Map<K, V> map) {
    // TODO: can these be moved to MapInterfaceTest?
    for (Entry<K, V> entry : map.entrySet()) {
      assertThat(entry.toString()).isEqualTo(entry.getKey() + "=" + entry.getValue());
    }

    assertThat(map.toString()).isEqualTo("{" + JOINER.join(map.entrySet()) + "}");
    assertThat(map.entrySet().toString()).isEqualTo("[" + JOINER.join(map.entrySet()) + "]");
    assertThat(map.keySet().toString()).isEqualTo("[" + JOINER.join(map.keySet()) + "]");
    assertThat(map.values().toString()).isEqualTo("[" + JOINER.join(map.values()) + "]");

    assertEquals(MinimalSet.from(map.entrySet()), map.entrySet());
    assertEquals(new HashSet<>(map.keySet()), map.keySet());
  }
}

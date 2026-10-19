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

import static com.m3.collections.guava.collect.Maps.transformValues;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.base.Function;
import com.m3.collections.guava.base.Functions;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Tests for {@link Maps#transformValues(Map, Function)}.
 *
 * @author Isaac Shum
 */
@GwtCompatible
@NullMarked
public class MapsTransformValuesTest extends AbstractMapsTransformValuesTest {
  @Override
  protected Map<String, String> makeEmptyMap() {
    return transformValues(new HashMap<String, String>(), Functions.identity());
  }

  @Override
  protected Map<String, String> makePopulatedMap() {
    Map<String, Integer> underlying = new HashMap<>();
    underlying.put("a", 1);
    underlying.put("b", 2);
    underlying.put("c", 3);
    return transformValues(underlying, Functions.toStringFunction());
  }
}

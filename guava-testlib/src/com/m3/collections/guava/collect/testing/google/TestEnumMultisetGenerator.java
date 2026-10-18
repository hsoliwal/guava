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

package com.m3.collections.guava.collect.testing.google;

import static java.util.Collections.sort;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.Multiset;
import com.m3.collections.guava.collect.testing.AnEnum;
import com.m3.collections.guava.collect.testing.SampleElements;
import com.m3.collections.guava.collect.testing.SampleElements.Enums;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * An abstract {@code TestMultisetGenerator} for generating multisets containing enum values.
 *
 * @author Jared Levy
 */
@GwtCompatible
@NullMarked
public abstract class TestEnumMultisetGenerator implements TestMultisetGenerator<AnEnum> {
  @Override
  public SampleElements<AnEnum> samples() {
    return new Enums();
  }

  @Override
  public Multiset<AnEnum> create(Object... elements) {
    AnEnum[] array = new AnEnum[elements.length];
    int i = 0;
    for (Object e : elements) {
      array[i++] = (AnEnum) e;
    }
    return create(array);
  }

  protected abstract Multiset<AnEnum> create(AnEnum[] elements);

  @Override
  public AnEnum[] createArray(int length) {
    return new AnEnum[length];
  }

  /** Sorts the enums according to their natural ordering. */
  /*
   * While the current implementation returns `this`, that's not something we mean to guarantee.
   * Callers of TestContainerGenerator.order need to be prepared for implementations to return a new
   * collection.
   */
  @SuppressWarnings("CanIgnoreReturnValueSuggester")
  @Override
  public List<AnEnum> order(List<AnEnum> insertionOrder) {
    sort(insertionOrder);
    return insertionOrder;
  }
}

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

package com.m3.collections.guava.collect.testing.testers;

import static com.m3.collections.guava.collect.testing.features.CollectionFeature.KNOWN_ORDER;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ONE;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.SEVERAL;
import static com.m3.collections.guava.collect.testing.features.CollectionSize.ZERO;
import static org.junit.Assert.assertThrows;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.testing.features.CollectionFeature;
import com.m3.collections.guava.collect.testing.features.CollectionSize;
import java.util.NoSuchElementException;

/**
 * A generic JUnit test which tests {@code element()} operations on a queue. Can't be invoked
 * directly; please see {@link com.m3.collections.guava.collect.testing.CollectionTestSuiteBuilder}.
 *
 * @author Jared Levy
 */
@GwtCompatible
public class QueueElementTester<E> extends AbstractQueueTester<E> {
  @CollectionSize.Require(ZERO)
  public void testElement_empty() {
    assertThrows(NoSuchElementException.class, () -> getQueue().element());
    expectUnchanged();
  }

  @CollectionSize.Require(ONE)
  public void testElement_size1() {
    assertEquals("size1Queue.element() should return first element", e0(), getQueue().element());
    expectUnchanged();
  }

  @CollectionFeature.Require(KNOWN_ORDER)
  @CollectionSize.Require(SEVERAL)
  public void testElement_sizeMany() {
    assertEquals("sizeManyQueue.element() should return first element", e0(), getQueue().element());
    expectUnchanged();
  }
}

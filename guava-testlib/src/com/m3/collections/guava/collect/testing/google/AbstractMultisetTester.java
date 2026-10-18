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

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.Multiset;
import com.m3.collections.guava.collect.testing.AbstractCollectionTester;
import org.junit.Ignore;

/**
 * Base class for multiset collection tests.
 *
 * @author Jared Levy
 */
@GwtCompatible
@Ignore("test runners must not instantiate and run this directly, only via suites we build")
// @Ignore affects the Android test runner, which respects JUnit 4 annotations on JUnit 3 tests.
@SuppressWarnings("JUnit4ClassUsedInJUnit3")
public class AbstractMultisetTester<E> extends AbstractCollectionTester<E> {
  protected final Multiset<E> getMultiset() {
    return (Multiset<E>) collection;
  }

  protected void initThreeCopies() {
    collection = getSubjectGenerator().create(e0(), e0(), e0());
  }
}

/*
 * Copyright (C) 2015 The Guava Authors
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

package com.m3.collections.guava.util.concurrent;

import static com.m3.collections.guava.truth.Truth.assertThat;
import static com.m3.collections.guava.util.concurrent.Futures.immediateFailedFuture;
import static com.m3.collections.guava.util.concurrent.Futures.successfulAsList;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import java.util.List;
import junit.framework.TestCase;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * A simple test of basic {@link AggregateFutureState} functionality, suitable for running even in a
 * test binary that has been optimized by a tool like R8.
 */
@NullUnmarked
@GwtIncompatible
@J2ktIncompatible
public class AggregateFutureStatePreliminaryTest extends TestCase {
  public void testInit() throws Exception {
    ListenableFuture<List<@Nullable Object>> future =
        successfulAsList(immediateFailedFuture(new Exception()));
    assertThat(future.get()).containsExactly((Object) null);
  }
}

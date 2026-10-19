/*
 * Copyright (C) 2011 The Guava Authors
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

package com.m3.collections.guava.cache;

import static com.m3.collections.guava.truth.Truth.assertThat;
import static com.m3.collections.guava.util.concurrent.Futures.immediateFuture;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.collect.ImmutableList;
import com.m3.collections.guava.collect.ImmutableMap;
import com.m3.collections.guava.util.concurrent.ListenableFuture;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import org.jspecify.annotations.NullUnmarked;

/**
 * Unit tests for {@link CacheLoader}.
 *
 * @author Charles Fry
 */
@GwtIncompatible
@NullUnmarked
public class CacheLoaderTest extends TestCase {

  private static class QueuingExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }

    private void runNext() {
      tasks.removeFirst().run();
    }
  }

  public void testAsyncReload() throws Exception {
    AtomicInteger loadCount = new AtomicInteger();
    AtomicInteger reloadCount = new AtomicInteger();
    AtomicInteger loadAllCount = new AtomicInteger();

    CacheLoader<Object, Object> baseLoader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            loadCount.incrementAndGet();
            return new Object();
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            reloadCount.incrementAndGet();
            return immediateFuture(new Object());
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<? extends Object> keys) {
            loadAllCount.incrementAndGet();
            return ImmutableMap.of();
          }
        };

    assertThat(loadCount.get()).isEqualTo(0);
    assertThat(reloadCount.get()).isEqualTo(0);
    assertThat(loadAllCount.get()).isEqualTo(0);

    Object unused1 = baseLoader.load(new Object());
    @SuppressWarnings("unused") // https://errorprone.info/bugpattern/FutureReturnValueIgnored
    Future<?> possiblyIgnoredError = baseLoader.reload(new Object(), new Object());
    Map<Object, Object> unused2 = baseLoader.loadAll(ImmutableList.of(new Object()));
    assertThat(loadCount.get()).isEqualTo(1);
    assertThat(reloadCount.get()).isEqualTo(1);
    assertThat(loadAllCount.get()).isEqualTo(1);

    QueuingExecutor executor = new QueuingExecutor();
    CacheLoader<Object, Object> asyncReloader = CacheLoader.asyncReloading(baseLoader, executor);

    Object unused3 = asyncReloader.load(new Object());
    @SuppressWarnings("unused") // https://errorprone.info/bugpattern/FutureReturnValueIgnored
    Future<?> possiblyIgnoredError1 = asyncReloader.reload(new Object(), new Object());
    Map<Object, Object> unused4 = asyncReloader.loadAll(ImmutableList.of(new Object()));
    assertThat(loadCount.get()).isEqualTo(2);
    assertThat(reloadCount.get()).isEqualTo(1);
    assertThat(loadAllCount.get()).isEqualTo(2);

    executor.runNext();
    assertThat(loadCount.get()).isEqualTo(2);
    assertThat(reloadCount.get()).isEqualTo(2);
    assertThat(loadAllCount.get()).isEqualTo(2);
  }
}

/*
 * Copyright (C) 2011 The Guava Authors
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

package com.m3.collections.guava.cache;

import static com.m3.collections.guava.cache.CacheTesting.checkEmpty;
import static com.m3.collections.guava.cache.TestingCacheLoaders.constantLoader;
import static com.m3.collections.guava.cache.TestingCacheLoaders.exceptionLoader;
import static com.m3.collections.guava.cache.TestingRemovalListeners.queuingRemovalListener;
import static com.m3.collections.guava.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.cache.CacheLoader.InvalidCacheLoadException;
import com.m3.collections.guava.cache.TestingRemovalListeners.QueuingRemovalListener;
import com.m3.collections.guava.util.concurrent.UncheckedExecutionException;
import junit.framework.TestCase;
import org.jspecify.annotations.NullUnmarked;

/**
 * {@link LoadingCache} tests for caches with a maximum size of zero.
 *
 * @author mike nonemacher
 */
@GwtIncompatible
@J2ktIncompatible
@NullUnmarked
public class NullCacheTest extends TestCase {
  QueuingRemovalListener<Object, Object> listener;

  @Override
  protected void setUp() {
    listener = queuingRemovalListener();
  }

  public void testGet() {
    Object computed = new Object();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .maximumSize(0)
            .removalListener(listener)
            .build(constantLoader(computed));

    Object key = new Object();
    assertThat(cache.getUnchecked(key)).isSameInstanceAs(computed);
    RemovalNotification<Object, Object> notification = listener.remove();
    assertThat(notification.getKey()).isSameInstanceAs(key);
    assertThat(notification.getValue()).isSameInstanceAs(computed);
    assertThat(notification.getCause()).isEqualTo(RemovalCause.SIZE);
    assertThat(listener.isEmpty()).isTrue();
    checkEmpty(cache);
  }

  public void testGet_expireAfterWrite() {
    Object computed = new Object();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(0, SECONDS)
            .removalListener(listener)
            .build(constantLoader(computed));

    Object key = new Object();
    assertThat(cache.getUnchecked(key)).isSameInstanceAs(computed);
    RemovalNotification<Object, Object> notification = listener.remove();
    assertThat(notification.getKey()).isSameInstanceAs(key);
    assertThat(notification.getValue()).isSameInstanceAs(computed);
    assertThat(notification.getCause()).isEqualTo(RemovalCause.SIZE);
    assertThat(listener.isEmpty()).isTrue();
    checkEmpty(cache);
  }

  public void testGet_expireAfterAccess() {
    Object computed = new Object();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(0, SECONDS)
            .removalListener(listener)
            .build(constantLoader(computed));

    Object key = new Object();
    assertThat(cache.getUnchecked(key)).isSameInstanceAs(computed);
    RemovalNotification<Object, Object> notification = listener.remove();
    assertThat(notification.getKey()).isSameInstanceAs(key);
    assertThat(notification.getValue()).isSameInstanceAs(computed);
    assertThat(notification.getCause()).isEqualTo(RemovalCause.SIZE);
    assertThat(listener.isEmpty()).isTrue();
    checkEmpty(cache);
  }

  public void testGet_computeNull() {
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .maximumSize(0)
            .removalListener(listener)
            .build(constantLoader(null));

    assertThrows(InvalidCacheLoadException.class, () -> cache.getUnchecked(new Object()));

    assertThat(listener.isEmpty()).isTrue();
    checkEmpty(cache);
  }

  public void testGet_runtimeException() {
    RuntimeException e = new RuntimeException();
    LoadingCache<Object, Object> map =
        CacheBuilder.newBuilder()
            .maximumSize(0)
            .removalListener(listener)
            .build(exceptionLoader(e));

    UncheckedExecutionException uee =
        assertThrows(UncheckedExecutionException.class, () -> map.getUnchecked(new Object()));
    assertThat(uee).hasCauseThat().isEqualTo(e);
    assertThat(listener.isEmpty()).isTrue();
    checkEmpty(map);
  }
}

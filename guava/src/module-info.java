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

/** Google Guava */
module com.m3.collections.guava {
  requires java.logging;
  requires transitive com.m3.collections.guava.util.concurrent.internal;
  requires static jdk.unsupported;
  requires static com.google.errorprone.annotations;
  requires static com.google.j2objc.annotations;
  requires static org.jspecify;

  exports com.m3.collections.guava.annotations;
  exports com.m3.collections.guava.base;
  exports com.m3.collections.guava.cache;
  exports com.m3.collections.guava.collect;
  exports com.m3.collections.guava.escape;
  exports com.m3.collections.guava.eventbus;
  exports com.m3.collections.guava.graph;
  exports com.m3.collections.guava.hash;
  exports com.m3.collections.guava.html;
  exports com.m3.collections.guava.io;
  exports com.m3.collections.guava.math;
  exports com.m3.collections.guava.net;
  exports com.m3.collections.guava.primitives;
  exports com.m3.collections.guava.reflect;
  exports com.m3.collections.guava.util.concurrent;
  exports com.m3.collections.guava.xml;
}

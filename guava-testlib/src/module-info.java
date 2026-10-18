/*
 * Copyright (C) 2024 The Guava Authors
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

/** Guava Testlib */
module com.m3.collections.guava.testlib {
  requires java.logging;
  requires transitive com.m3.collections.guava;
  requires transitive junit;
  requires static com.google.errorprone.annotations;
  requires static com.google.j2objc.annotations;
  requires static org.jspecify;

  exports com.m3.collections.guava.collect.testing;
  exports com.m3.collections.guava.collect.testing.features;
  exports com.m3.collections.guava.collect.testing.google;
  exports com.m3.collections.guava.collect.testing.testers;
  exports com.m3.collections.guava.escape.testing;
  exports com.m3.collections.guava.testing;
  exports com.m3.collections.guava.util.concurrent.testing;
}

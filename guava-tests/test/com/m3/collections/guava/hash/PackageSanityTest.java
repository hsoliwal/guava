/*
 * Copyright (C) 2012 The Guava Authors
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

package com.m3.collections.guava.hash;

import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.hash.BloomFilterStrategies.LockFreeBitArray;
import com.m3.collections.guava.testing.AbstractPackageSanityTests;
import org.jspecify.annotations.NullUnmarked;

/**
 * Basic sanity tests for the entire package.
 *
 * @author Ben Yu
 */

@NullUnmarked
@J2ktIncompatible
public class PackageSanityTest extends AbstractPackageSanityTests {
  public PackageSanityTest() {
    setDefault(LockFreeBitArray.class, new LockFreeBitArray(1));
    setDefault(HashCode.class, HashCode.fromInt(1));
    setDefault(String.class, "MD5");
    setDefault(int.class, 32);
  }
}

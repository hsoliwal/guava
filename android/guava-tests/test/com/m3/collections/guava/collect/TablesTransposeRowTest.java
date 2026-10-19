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

import static com.m3.collections.guava.collect.Tables.transpose;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.collect.TableCollectionTest.RowTests;
import org.jspecify.annotations.NullMarked;

@GwtCompatible
@NullMarked
public class TablesTransposeRowTest extends RowTests {
  public TablesTransposeRowTest() {
    super(false, true, true, true, false);
  }

  @Override
  Table<Character, String, Integer> makeTable() {
    Table<String, Character, Integer> original = TreeBasedTable.create();
    return transpose(original);
  }
}

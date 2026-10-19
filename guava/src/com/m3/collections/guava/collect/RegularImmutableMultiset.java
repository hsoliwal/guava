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

// M3 immutable-count array adaptation; provenance: m3/immutable-count-arrays-20261019/CONTRACT.md
package com.m3.collections.guava.collect;

import static com.m3.collections.guava.base.Preconditions.checkNotNull;

import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.annotations.VisibleForTesting;
import com.m3.collections.guava.primitives.Ints;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@link ImmutableMultiset} with zero or more elements.
 *
 * @author Jared Levy
 * @author Louis Wasserman
 */
@GwtCompatible
@SuppressWarnings("serial") // uses writeReplace(), not default serialization
final class RegularImmutableMultiset<E> extends ImmutableMultiset<E> {
  static final ImmutableMultiset<Object> EMPTY =
      new RegularImmutableMultiset<>(ObjectCountHashMap.createWithExpectedSize(0), 0, 0);

  // Preserve the source owner's flooding constants and its explicit JDK result fallback.
  @VisibleForTesting static final double MAX_LOAD_FACTOR = 1.0;
  @VisibleForTesting static final double HASH_FLOODING_FPP = 0.001;
  @VisibleForTesting static final int MAX_HASH_BUCKET_LENGTH = 9;

  private final transient ObjectCountHashMap<E> contents;
  private final transient int size;
  private final transient int hashCode;

  static <E> ImmutableMultiset<E> create(Collection<? extends Entry<? extends E>> entries) {
    ObjectCountHashMap<E> contents = ObjectCountHashMap.createWithExpectedSize(entries.size());
    int hashCode = 0;
    long size = 0;
    for (Entry<? extends E> entry : entries) {
      E element = checkNotNull(entry.getElement());
      int count = entry.getCount();
      contents.put(element, count);
      hashCode += element.hashCode() ^ count;
      size += count;
    }
    if (contents.usesHashFloodingResistance()) {
      // Snapshot the already-read columns; do not reread potentially mutable source entries.
      return JdkBackedImmutableMultiset.create(
          new ImmutableList<Entry<E>>() {
            @Override
            public Entry<E> get(int index) {
              return Multisets.immutableEntry(contents.getKey(index), contents.getValue(index));
            }

            @Override
            public int size() {
              return contents.size();
            }

            @Override
            boolean isPartialView() {
              return true;
            }

            @SuppressWarnings("RedundantOverride")
            @Override
            @J2ktIncompatible
            @GwtIncompatible
            Object writeReplace() {
              return super.writeReplace();
            }
          });
    }
    return new RegularImmutableMultiset<>(contents, Ints.saturatedCast(size), hashCode);
  }

  private RegularImmutableMultiset(ObjectCountHashMap<E> contents, int size, int hashCode) {
    this.contents = contents;
    this.size = size;
    this.hashCode = hashCode;
  }

  @Override
  boolean isPartialView() {
    return false;
  }

  @Override
  public int count(@Nullable Object element) {
    return element == null ? 0 : contents.get(element);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public ImmutableSet<E> elementSet() {
    return isEmpty() ? ImmutableSet.of() : new ElementSet();
  }

  private final class ElementSet extends IndexedImmutableSet<E> {
    @Override
    E get(int index) {
      return contents.getKey(index);
    }

    @Override
    public boolean contains(@Nullable Object object) {
      return RegularImmutableMultiset.this.contains(object);
    }

    @Override
    boolean isPartialView() {
      return true;
    }

    @Override
    public int size() {
      return contents.size();
    }

    @SuppressWarnings("RedundantOverride")
    @Override
    @J2ktIncompatible
    @GwtIncompatible
    Object writeReplace() {
      return super.writeReplace();
    }
  }

  @Override
  Entry<E> getEntry(int index) {
    return Multisets.immutableEntry(contents.getKey(index), contents.getValue(index));
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  // Preserve the JRE SerializedForm, including entry-set and element-set compatibility.
  @SuppressWarnings("RedundantOverride")
  @Override
  @J2ktIncompatible
  @GwtIncompatible
  Object writeReplace() {
    return super.writeReplace();
  }
}

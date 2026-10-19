/*
 * Copyright (C) 2007 The Guava Authors
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

// M3 array-storage adaptation; provenance: m3/array-storage-20261019/CONTRACT.md
package com.m3.collections.guava.collect;

import static com.m3.collections.guava.base.Preconditions.checkArgument;
import static com.m3.collections.guava.base.Preconditions.checkNotNull;
import static com.m3.collections.guava.collect.CollectPreconditions.checkNonnegative;
import static com.m3.collections.guava.collect.CollectPreconditions.checkRemove;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.primitives.Ints;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Basic implementation of {@code Multiset<E>} backed by an instance of {@code
 * ObjectCountHashMap<E>}.
 *
 * <p>For serialization to work, the subclass must specify explicit {@code readObject} and {@code
 * writeObject} methods.
 *
 * @author Kevin Bourrillion
 */
@GwtCompatible
abstract class AbstractMapBasedMultiset<E extends @Nullable Object> extends AbstractMultiset<E>
    implements Serializable {

  private transient ObjectCountHashMap<E> backingMap;
  private transient long size;

  AbstractMapBasedMultiset(ObjectCountHashMap<E> backingMap) {
    checkArgument(backingMap.size() == 0);
    this.backingMap = backingMap;
  }

  /** Used during deserialization only. The backing map must be empty. */
  final void setBackingMap(ObjectCountHashMap<E> backingMap) {
    this.backingMap = backingMap;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Invoking {@link Multiset.Entry#getCount} on an entry in the returned set always returns the
   * current count of that element in the multiset, as opposed to the count at the time the entry
   * was retrieved.
   */
  @Override
  public final Set<Entry<E>> entrySet() {
    return super.entrySet();
  }

  @Override
  public final void forEachEntry(ObjIntConsumer<? super E> action) {
    checkNotNull(action);
    int expectedModCount = backingMap.modCount;
    for (int index = backingMap.firstIndex(); index >= 0; index = backingMap.nextIndex(index)) {
      action.accept(backingMap.getKey(index), backingMap.getValue(index));
      if (backingMap.modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }
  }

  @Override
  public final int count(@Nullable Object element) {
    try {
      return backingMap.get(element);
    } catch (ClassCastException | NullPointerException e) {
      return 0;
    }
  }

  // Optional Operations - Modification Operations

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if the call would result in more than {@link
   *     Integer#MAX_VALUE} occurrences of {@code element} in this multiset.
   */
  @CanIgnoreReturnValue
  @Override
  public final int add(@ParametricNullness E element, int occurrences) {
    if (occurrences == 0) {
      return count(element);
    }
    checkArgument(occurrences > 0, "occurrences cannot be negative: %s", occurrences);
    int entryIndex = backingMap.indexOf(element);
    if (entryIndex == -1) {
      backingMap.put(element, occurrences);
      size += occurrences;
      return 0;
    }
    int oldCount = backingMap.getValue(entryIndex);
    long newCount = (long) oldCount + (long) occurrences;
    checkArgument(newCount <= Integer.MAX_VALUE, "too many occurrences: %s", newCount);
    backingMap.setValue(entryIndex, (int) newCount);
    size += occurrences;
    return oldCount;
  }

  @CanIgnoreReturnValue
  @Override
  public final int remove(@Nullable Object element, int occurrences) {
    if (occurrences == 0) {
      return count(element);
    }
    checkArgument(occurrences > 0, "occurrences cannot be negative: %s", occurrences);
    int entryIndex = backingMap.indexOf(element);
    if (entryIndex == -1) {
      return 0;
    }
    int oldCount = backingMap.getValue(entryIndex);
    int numberRemoved;
    if (oldCount > occurrences) {
      numberRemoved = occurrences;
      backingMap.setValue(entryIndex, oldCount - occurrences);
    } else {
      numberRemoved = oldCount;
      backingMap.removeEntry(entryIndex);
    }
    size -= numberRemoved;
    return oldCount;
  }

  @CanIgnoreReturnValue
  @Override
  public final int setCount(@ParametricNullness E element, int count) {
    checkNonnegative(count, "count");
    int oldCount = (count == 0) ? backingMap.remove(element) : backingMap.put(element, count);
    size += (count - oldCount);
    return oldCount;
  }

  @Override
  public final void clear() {
    backingMap.clear();
    size = 0;
  }

  /**
   * Skeleton of per-entry iterators. We could push this down and win a few bytes, but it's complex
   * enough it's not especially worth it.
   */
  abstract class Itr<T extends @Nullable Object> implements Iterator<T> {
    int entryIndex = backingMap.firstIndex();
    int toRemove = -1;
    int expectedModCount = backingMap.modCount;

    @ParametricNullness
    abstract T result(int entryIndex);

    private void checkForConcurrentModification() {
      if (backingMap.modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    @Override
    public boolean hasNext() {
      checkForConcurrentModification();
      return entryIndex >= 0;
    }

    @Override
    @ParametricNullness
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      T result = result(entryIndex);
      toRemove = entryIndex;
      entryIndex = backingMap.nextIndex(entryIndex);
      return result;
    }

    @Override
    public void remove() {
      checkForConcurrentModification();
      checkRemove(toRemove != -1);
      size -= backingMap.removeEntry(toRemove);
      entryIndex = backingMap.nextIndexAfterRemove(entryIndex, toRemove);
      toRemove = -1;
      expectedModCount = backingMap.modCount;
    }
  }

  @Override
  final Iterator<E> elementIterator() {
    return new Itr<E>() {
      @Override
      @ParametricNullness
      E result(int entryIndex) {
        return backingMap.getKey(entryIndex);
      }
    };
  }

  @Override
  final Iterator<Entry<E>> internalEntryIterator() {
    return new Itr<Entry<E>>() {
      @Override
      Entry<E> result(int entryIndex) {
        return backingMap.getEntry(entryIndex);
      }
    };
  }

  @Override
  final int internalDistinctElements() {
    return backingMap.size();
  }

  @Override
  public final Iterator<E> iterator() {
    return new MapBasedMultisetIterator();
  }

  @Override
  public final int size() {
    return Ints.saturatedCast(size);
  }

  /** Occurrence iteration keeps count edits nonstructural and repairs moved slots on removal. */
  private final class MapBasedMultisetIterator implements Iterator<E> {
    private final Iterator<Entry<E>> entryIterator = internalEntryIterator();
    private @Nullable Entry<E> currentEntry;
    private int occurrencesLeft;
    private boolean canRemove;

    @Override
    public boolean hasNext() {
      return occurrencesLeft > 0 || entryIterator.hasNext();
    }

    @Override
    @ParametricNullness
    public E next() {
      if (occurrencesLeft == 0) {
        currentEntry = entryIterator.next();
        occurrencesLeft = currentEntry.getCount();
      }
      occurrencesLeft--;
      canRemove = true;
      return java.util.Objects.requireNonNull(currentEntry).getElement();
    }

    @Override
    public void remove() {
      checkRemove(canRemove);
      Entry<E> entry = java.util.Objects.requireNonNull(currentEntry);
      int frequency = entry.getCount();
      if (frequency <= 0) {
        throw new ConcurrentModificationException();
      }
      if (frequency == 1) {
        entryIterator.remove();
      } else {
        backingMap.setValue(backingMap.indexOf(entry.getElement()), frequency - 1);
        size--;
      }
      canRemove = false;
    }
  }

  // Don't allow default serialization.
  @GwtIncompatible
  @J2ktIncompatible
  private void readObjectNoData() throws ObjectStreamException {
    throw new InvalidObjectException("Stream data required");
  }

  @GwtIncompatible @J2ktIncompatible
  private static final long serialVersionUID = -2250766705698539974L;
}

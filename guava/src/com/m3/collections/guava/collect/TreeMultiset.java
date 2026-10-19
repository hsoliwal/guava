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

// M3 intrinsic AVL array adaptation; provenance: m3/tree-multiset-arrays-20261019/CONTRACT.md
package com.m3.collections.guava.collect;

import static com.m3.collections.guava.base.Preconditions.checkArgument;
import static com.m3.collections.guava.base.Preconditions.checkNotNull;
import static com.m3.collections.guava.base.Preconditions.checkState;
import static com.m3.collections.guava.collect.CollectPreconditions.checkNonnegative;
import static com.m3.collections.guava.collect.NullnessCasts.uncheckedCastNullableTToT;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.m3.collections.guava.annotations.GwtCompatible;
import com.m3.collections.guava.annotations.GwtIncompatible;
import com.m3.collections.guava.annotations.J2ktIncompatible;
import com.m3.collections.guava.primitives.Ints;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.ObjIntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A multiset which maintains the ordering of its elements, according to either their natural order
 * or an explicit {@link Comparator}. In all cases, this implementation uses {@link
 * Comparable#compareTo} or {@link Comparator#compare} instead of {@link Object#equals} to determine
 * equivalence of instances.
 *
 * <p><b>Warning:</b> The comparison must be <i>consistent with equals</i> as explained by the
 * {@link Comparable} class specification. Otherwise, the resulting multiset will violate the {@link
 * java.util.Collection} contract, which is specified in terms of {@link Object#equals}.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/NewCollectionTypesExplained#multiset">{@code Multiset}</a>.
 *
 * @author Louis Wasserman
 * @author Jared Levy
 * @since 2.0
 */
@GwtCompatible
public final class TreeMultiset<E extends @Nullable Object> extends AbstractSortedMultiset<E>
    implements Serializable {

  /**
   * Creates a new, empty multiset, sorted according to the elements' natural order. All elements
   * inserted into the multiset must implement the {@code Comparable} interface. Furthermore, all
   * such elements must be <i>mutually comparable</i>: {@code e1.compareTo(e2)} must not throw a
   * {@code ClassCastException} for any elements {@code e1} and {@code e2} in the multiset. If the
   * user attempts to add an element to the multiset that violates this constraint (for example, the
   * user attempts to add a string element to a set whose elements are integers), the {@code
   * add(Object)} call will throw a {@code ClassCastException}.
   *
   * <p>The type specification is {@code <E extends Comparable>}, instead of the more specific
   * {@code <E extends Comparable<? super E>>}, to support classes defined without generics.
   */
  @SuppressWarnings("rawtypes") // https://github.com/google/guava/issues/989
  public static <E extends Comparable> TreeMultiset<E> create() {
    return new TreeMultiset<>(Ordering.natural());
  }

  /**
   * Creates a new, empty multiset, sorted according to the specified comparator. All elements
   * inserted into the multiset must be <i>mutually comparable</i> by the specified comparator:
   * {@code comparator.compare(e1, e2)} must not throw a {@code ClassCastException} for any elements
   * {@code e1} and {@code e2} in the multiset. If the user attempts to add an element to the
   * multiset that violates this constraint, the {@code add(Object)} call will throw a {@code
   * ClassCastException}.
   *
   * @param comparator the comparator that will be used to sort this multiset. A null value
   *     indicates that the elements' <i>natural ordering</i> should be used.
   */
  @SuppressWarnings("unchecked")
  public static <E extends @Nullable Object> TreeMultiset<E> create(
      @Nullable Comparator<? super E> comparator) {
    return (comparator == null)
        ? new TreeMultiset<E>((Comparator) Ordering.natural())
        : new TreeMultiset<E>(comparator);
  }

  /**
   * Creates an empty multiset containing the given initial elements, sorted according to the
   * elements' natural order.
   *
   * <p>This implementation is highly efficient when {@code elements} is itself a {@link Multiset}.
   *
   * <p>The type specification is {@code <E extends Comparable>}, instead of the more specific
   * {@code <E extends Comparable<? super E>>}, to support classes defined without generics.
   */
  @SuppressWarnings("rawtypes") // https://github.com/google/guava/issues/989
  public static <E extends Comparable> TreeMultiset<E> create(Iterable<? extends E> elements) {
    TreeMultiset<E> multiset = create();
    Iterables.addAll(multiset, elements);
    return multiset;
  }

  private static final int NONE = -1;
  private static final int HEADER = 0;
  private final transient SlotStore<E> rootReference;
  private final transient GeneralRange<E> range;

  /**
   * An instance created in {@link #readObject} to be returned from {@link #readResolve}. This field
   * is used only by those methods, and it is never set in a "normal" instance.
   *
   * <p>For more background, see {@code ConcurrentHashMultiset.deserializationReplacement}.
   */
  private transient @Nullable TreeMultiset<E> deserializationReplacement;

  private TreeMultiset(SlotStore<E> rootReference, GeneralRange<E> range) {
    super(range.comparator());
    this.rootReference = rootReference;
    this.range = range;
  }

  private TreeMultiset(Comparator<? super E> comparator) {
    super(comparator);
    this.range = GeneralRange.all(comparator);
    this.rootReference = new SlotStore<>();
  }

  private boolean rangeContains(@ParametricNullness E element) {
    rootReference.beginCallback();
    try {
      return range.contains(element);
    } finally {
      rootReference.endCallback();
    }
  }

  private boolean rangeTooLow(@ParametricNullness E element) {
    rootReference.beginCallback();
    try {
      return range.tooLow(element);
    } finally {
      rootReference.endCallback();
    }
  }

  private boolean rangeTooHigh(@ParametricNullness E element) {
    rootReference.beginCallback();
    try {
      return range.tooHigh(element);
    } finally {
      rootReference.endCallback();
    }
  }

  /** A function which can be summed across an indexed subtree. */
  private enum Aggregate {
    SIZE {
      @Override
      int nodeAggregate(SlotStore<?> slots, int slot) {
        return slots.elemCount[slot];
      }

      @Override
      long treeAggregate(SlotStore<?> slots, int slot) {
        return slots.totalCount(slot);
      }
    },
    DISTINCT {
      @Override
      int nodeAggregate(SlotStore<?> slots, int slot) {
        return 1;
      }

      @Override
      long treeAggregate(SlotStore<?> slots, int slot) {
        return slots.distinctElements(slot);
      }
    };

    abstract int nodeAggregate(SlotStore<?> slots, int slot);

    abstract long treeAggregate(SlotStore<?> slots, int slot);
  }

  private long aggregateForEntries(Aggregate aggr) {
    rootReference.enter();
    try {
      int root = rootReference.get();
      long total = aggr.treeAggregate(rootReference, root);
      if (range.hasLowerBound()) {
        total -= aggregateBelowRange(aggr, root);
      }
      if (range.hasUpperBound()) {
        total -= aggregateAboveRange(aggr, root);
      }
      return total;
    } finally {
      rootReference.exit();
    }
  }

  private long aggregateBelowRange(Aggregate aggr, int node) {
    if (node == NONE) {
      return 0;
    }
    // The cast is safe because we call this method only if hasLowerBound().
    int cmp =
        rootReference.compare(
            comparator(),
            uncheckedCastNullableTToT(range.getLowerEndpoint()),
            rootReference.element(node));
    if (cmp < 0) {
      return aggregateBelowRange(aggr, rootReference.left[node]);
    } else if (cmp == 0) {
      switch (range.getLowerBoundType()) {
        case OPEN:
          return aggr.nodeAggregate(rootReference, node)
              + aggr.treeAggregate(rootReference, rootReference.left[node]);
        case CLOSED:
          return aggr.treeAggregate(rootReference, rootReference.left[node]);
      }
      throw new AssertionError();
    } else {
      return aggr.treeAggregate(rootReference, rootReference.left[node])
          + aggr.nodeAggregate(rootReference, node)
          + aggregateBelowRange(aggr, rootReference.right[node]);
    }
  }

  private long aggregateAboveRange(Aggregate aggr, int node) {
    if (node == NONE) {
      return 0;
    }
    // The cast is safe because we call this method only if hasUpperBound().
    int cmp =
        rootReference.compare(
            comparator(),
            uncheckedCastNullableTToT(range.getUpperEndpoint()),
            rootReference.element(node));
    if (cmp > 0) {
      return aggregateAboveRange(aggr, rootReference.right[node]);
    } else if (cmp == 0) {
      switch (range.getUpperBoundType()) {
        case OPEN:
          return aggr.nodeAggregate(rootReference, node)
              + aggr.treeAggregate(rootReference, rootReference.right[node]);
        case CLOSED:
          return aggr.treeAggregate(rootReference, rootReference.right[node]);
      }
      throw new AssertionError();
    } else {
      return aggr.treeAggregate(rootReference, rootReference.right[node])
          + aggr.nodeAggregate(rootReference, node)
          + aggregateAboveRange(aggr, rootReference.left[node]);
    }
  }

  @Override
  public int size() {
    return Ints.saturatedCast(aggregateForEntries(Aggregate.SIZE));
  }

  @Override
  int internalDistinctElements() {
    return Ints.saturatedCast(aggregateForEntries(Aggregate.DISTINCT));
  }

  @Override
  public int count(@Nullable Object element) {
    rootReference.enter();
    try {
      try {
        @SuppressWarnings("unchecked")
        E e = (E) element;
        int root = rootReference.get();
        if (!rangeContains(e) || root == NONE) {
          return 0;
        }
        return rootReference.count(root, comparator(), e);
      } catch (ClassCastException | NullPointerException e) {
        return 0;
      }
    } finally {
      rootReference.exit();
    }
  }

  @CanIgnoreReturnValue
  @Override
  public int add(@ParametricNullness E element, int occurrences) {
    rootReference.enter();
    try {
      checkNonnegative(occurrences, "occurrences");
      if (occurrences == 0) {
        return count(element);
      }
      checkArgument(rangeContains(element));
      int root = rootReference.get();
      if (root == NONE) {
        int unused = rootReference.compare(comparator(), element, element);
        int newRoot = rootReference.newNode(element, occurrences);
        rootReference.successor(HEADER, newRoot, HEADER);
        rootReference.checkAndSet(root, newRoot);
        return 0;
      }
      int[] result = new int[1]; // used as a mutable int reference to hold result
      int newRoot = rootReference.add(root, comparator(), element, occurrences, result);
      rootReference.checkAndSet(root, newRoot);
      return result[0];
    } finally {
      rootReference.exit();
    }
  }

  @CanIgnoreReturnValue
  @Override
  public int remove(@Nullable Object element, int occurrences) {
    rootReference.enter();
    try {
      checkNonnegative(occurrences, "occurrences");
      if (occurrences == 0) {
        return count(element);
      }
      int root = rootReference.get();
      int[] result = new int[1]; // used as a mutable int reference to hold result
      int newRoot;
      try {
        @SuppressWarnings("unchecked")
        E e = (E) element;
        if (!rangeContains(e) || root == NONE) {
          return 0;
        }
        newRoot = rootReference.remove(root, comparator(), e, occurrences, result);
      } catch (ClassCastException | NullPointerException e) {
        return 0;
      }
      rootReference.checkAndSet(root, newRoot);
      return result[0];
    } finally {
      rootReference.exit();
    }
  }

  @CanIgnoreReturnValue
  @Override
  public int setCount(@ParametricNullness E element, int count) {
    rootReference.enter();
    try {
      checkNonnegative(count, "count");
      if (!rangeContains(element)) {
        checkArgument(count == 0);
        return 0;
      }

      int root = rootReference.get();
      if (root == NONE) {
        if (count > 0) {
          add(element, count);
        }
        return 0;
      }
      int[] result = new int[1]; // used as a mutable int reference to hold result
      int newRoot = rootReference.setCount(root, comparator(), element, count, result);
      rootReference.checkAndSet(root, newRoot);
      return result[0];
    } finally {
      rootReference.exit();
    }
  }

  @CanIgnoreReturnValue
  @Override
  public boolean setCount(@ParametricNullness E element, int oldCount, int newCount) {
    rootReference.enter();
    try {
      checkNonnegative(newCount, "newCount");
      checkNonnegative(oldCount, "oldCount");
      checkArgument(rangeContains(element));

      int root = rootReference.get();
      if (root == NONE) {
        if (oldCount == 0) {
          if (newCount > 0) {
            add(element, newCount);
          }
          return true;
        } else {
          // Validate the element even when its count cannot match.
          int unused = rootReference.compare(comparator(), element, element);
          return false;
        }
      }
      int[] result = new int[1]; // used as a mutable int reference to hold result
      int newRoot = rootReference.setCount(root, comparator(), element, oldCount, newCount, result);
      rootReference.checkAndSet(root, newRoot);
      return result[0] == oldCount;
    } finally {
      rootReference.exit();
    }
  }

  @Override
  public void clear() {
    rootReference.enter();
    try {
      if (!range.hasLowerBound() && !range.hasUpperBound()) {
        rootReference.clear();
      } else {
        Iterators.clear(internalEntryIterator());
      }
    } finally {
      rootReference.exit();
    }
  }

  private Entry<E> wrapEntry(
      int slot,
      long generation,
      @ParametricNullness E element,
      @Nullable ExportedCount capturedCount) {
    ExportedCount exportedCount =
        rootReference.isCurrent(slot, generation)
            ? rootReference.exportCount(slot)
            : requireNonNull(capturedCount);
    return new Multisets.AbstractEntry<E>() {
      @Override
      @ParametricNullness
      public E getElement() {
        return element;
      }

      @Override
      public int getCount() {
        int result = rootReference.liveCount(slot, generation);
        if (result == 0) {
          result = exportedCount.detachedCount;
        }
        return result == 0 ? count(element) : result;
      }
    };
  }

  /** Returns the first node in the tree that is in range. */
  private int firstNode() {
    rootReference.enter();
    try {
      int root = rootReference.get();
      if (root == NONE) {
        return NONE;
      }
      int node;
      if (range.hasLowerBound()) {
        // The cast is safe because of the hasLowerBound check.
        E endpoint = uncheckedCastNullableTToT(range.getLowerEndpoint());
        node = rootReference.ceiling(root, comparator(), endpoint);
        if (node == NONE) {
          return NONE;
        }
        if (range.getLowerBoundType() == BoundType.OPEN
            && rootReference.compare(comparator(), endpoint, rootReference.element(node)) == 0) {
          node = rootReference.succ(node);
        }
      } else {
        node = rootReference.succ(HEADER);
      }
      return (node == HEADER || !rangeContains(rootReference.element(node))) ? NONE : node;
    } finally {
      rootReference.exit();
    }
  }

  private int lastNode() {
    rootReference.enter();
    try {
      int root = rootReference.get();
      if (root == NONE) {
        return NONE;
      }
      int node;
      if (range.hasUpperBound()) {
        // The cast is safe because of the hasUpperBound check.
        E endpoint = uncheckedCastNullableTToT(range.getUpperEndpoint());
        node = rootReference.floor(root, comparator(), endpoint);
        if (node == NONE) {
          return NONE;
        }
        if (range.getUpperBoundType() == BoundType.OPEN
            && rootReference.compare(comparator(), endpoint, rootReference.element(node)) == 0) {
          node = rootReference.pred(node);
        }
      } else {
        node = rootReference.pred(HEADER);
      }
      return (node == HEADER || !rangeContains(rootReference.element(node))) ? NONE : node;
    } finally {
      rootReference.exit();
    }
  }

  @Override
  Iterator<E> elementIterator() {
    return Multisets.elementIterator(internalEntryIterator());
  }

  @Override
  Iterator<Entry<E>> internalEntryIterator() {
    return entryIterator(false);
  }

  @Override
  Iterator<Entry<E>> descendingEntryIterator() {
    return entryIterator(true);
  }

  /** Direction selects the same primitive linked-order traversal for both existing views. */
  private Iterator<Entry<E>> entryIterator(boolean descending) {
    rootReference.enter();
    try {
      return new Iterator<Entry<E>>() {
        private int current = descending ? lastNode() : firstNode();
        private long generation = current == NONE ? 0 : rootReference.generation(current);
        private @Nullable E currentElement;
        private @Nullable CursorSnapshot<E> captured;
        private @Nullable Entry<E> prevEntry;

        {
          captureCurrent();
          captureDetachment(true);
        }

        private int capturedRow() {
          int row = captured == null ? NONE : captured.find(generation);
          if (row == NONE) {
            throw new ConcurrentModificationException();
          }
          return row;
        }

        private void captureCurrent() {
          if (captured != null && captured.registered) {
            captured.current = current;
            captured.currentGeneration = generation;
          }
          if (current == NONE) {
            currentElement = null;
            captured = null;
          } else if (rootReference.isCurrent(current, generation)) {
            currentElement = rootReference.element(current);
          } else {
            currentElement = requireNonNull(captured).element(capturedRow());
          }
        }

        /** Freeze the direction chain before this cursor's own outer frame releases generations. */
        private void captureDetachment(boolean constructing) {
          if (current != NONE && constructing && rootReference.activeOperations > 1) {
            captured = rootReference.registerCursor(current, generation, descending, captured);
          }
          if (current != NONE
              && (constructing || rootReference.activeOperations == 1)
              && (rootReference.reconcileNeeded
                  || (rootReference.isCurrent(current, generation)
                      && rootReference.isRetired(current)))) {
            CursorSnapshot<E> refreshed =
                rootReference.captureChain(current, generation, descending, captured);
            if (captured != null && captured.registered) {
              captured.replaceWith(refreshed);
            } else {
              captured = refreshed;
            }
          }
        }

        @Override
        public boolean hasNext() {
          rootReference.enter();
          try {
            if (current == NONE) {
              return false;
            }
            if (!rootReference.isCurrent(current, generation)
                && (captured == null || captured.find(generation) == NONE)) {
              throw new ConcurrentModificationException();
            }
            if (descending
                ? rangeTooLow(uncheckedCastNullableTToT(currentElement))
                : rangeTooHigh(uncheckedCastNullableTToT(currentElement))) {
              current = NONE;
              captureCurrent();
              return false;
            }
            return true;
          } finally {
            try {
              captureDetachment(false);
            } finally {
              rootReference.exit();
            }
          }
        }

        @Override
        public Entry<E> next() {
          rootReference.enter();
          try {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            boolean live = rootReference.isCurrent(current, generation);
            int row = live ? NONE : capturedRow();
            Entry<E> result =
                wrapEntry(
                    current,
                    generation,
                    uncheckedCastNullableTToT(currentElement),
                    live ? null : requireNonNull(captured).counts[row]);
            prevEntry = result;
            int next =
                live
                    ? (descending ? rootReference.pred[current] : rootReference.succ[current])
                    : requireNonNull(captured).nextSlots[row];
            if (next == NONE) {
              throw new NullPointerException();
            }
            long nextGeneration =
                next == HEADER
                    ? 0
                    : live
                        ? rootReference.generation(next)
                        : requireNonNull(captured).nextGenerations[row];
            current = next == HEADER ? NONE : next;
            generation = nextGeneration;
            captureCurrent();
            return result;
          } finally {
            try {
              captureDetachment(false);
            } finally {
              rootReference.exit();
            }
          }
        }

        @Override
        public void remove() {
          checkState(prevEntry != null, "no calls to next() since the last call to remove()");
          setCount(prevEntry.getElement(), 0);
          prevEntry = null;
        }
      };
    } finally {
      rootReference.exit();
    }
  }

  @Override
  public void forEachEntry(ObjIntConsumer<? super E> action) {
    rootReference.enter();
    try {
      checkNotNull(action);
      for (int node = firstNode();
          node != HEADER && node != NONE && !rangeTooHigh(rootReference.element(node));
          node = rootReference.succ(node)) {
        rootReference.beginCallback();
        try {
          action.accept(rootReference.element(node), rootReference.elemCount[node]);
        } finally {
          rootReference.endCallback();
        }
      }
    } finally {
      rootReference.exit();
    }
  }

  @Override
  public Iterator<E> iterator() {
    return Multisets.iteratorImpl(this);
  }

  @Override
  public SortedMultiset<E> headMultiset(@ParametricNullness E upperBound, BoundType boundType) {
    return new TreeMultiset<>(
        rootReference, range.intersect(GeneralRange.upTo(comparator(), upperBound, boundType)));
  }

  @Override
  public SortedMultiset<E> tailMultiset(@ParametricNullness E lowerBound, BoundType boundType) {
    return new TreeMultiset<>(
        rootReference, range.intersect(GeneralRange.downTo(comparator(), lowerBound, boundType)));
  }

  /** Count compatibility allocated only for an exported entry or a detached endpoint cursor. */
  private static final class ExportedCount {
    int detachedCount;
  }

  /** Direction chain owned by its iterator after the creating operation releases its slots. */
  private static final class CursorSnapshot<E extends @Nullable Object> {
    private Object[] elements = new Object[8];
    private ExportedCount[] counts = new ExportedCount[8];
    private long[] generations = new long[8];
    private int[] nextSlots = new int[8];
    private long[] nextGenerations = new long[8];
    private int[] index = new int[16];
    private int size;
    private int current = NONE;
    private long currentGeneration;
    private boolean descending;
    private boolean registered;

    void replaceWith(CursorSnapshot<E> refreshed) {
      elements = refreshed.elements;
      counts = refreshed.counts;
      generations = refreshed.generations;
      nextSlots = refreshed.nextSlots;
      nextGenerations = refreshed.nextGenerations;
      index = refreshed.index;
      size = refreshed.size;
    }

    private static int hash(long generation) {
      return Hashing.smear((int) (generation ^ (generation >>> 32)));
    }

    int find(long generation) {
      int bucket = hash(generation) & (index.length - 1);
      while (index[bucket] != 0) {
        int row = index[bucket] - 1;
        if (generations[row] == generation) {
          return row;
        }
        bucket = (bucket + 1) & (index.length - 1);
      }
      return NONE;
    }

    @SuppressWarnings("unchecked")
    @ParametricNullness
    E element(int row) {
      return (E) elements[row];
    }

    private void grow() {
      if (elements.length >= (1 << 29)) {
        throw new OutOfMemoryError("TreeMultiset cursor capacity exhausted");
      }
      int capacity = elements.length * 2;
      Object[] newElements = Arrays.copyOf(elements, capacity);
      ExportedCount[] newCounts = Arrays.copyOf(counts, capacity);
      long[] newGenerations = Arrays.copyOf(generations, capacity);
      int[] newNextSlots = Arrays.copyOf(nextSlots, capacity);
      long[] newNextGenerations = Arrays.copyOf(nextGenerations, capacity);
      int[] newIndex = new int[capacity * 2];
      for (int row = 0; row < size; row++) {
        int bucket = hash(generations[row]) & (newIndex.length - 1);
        while (newIndex[bucket] != 0) {
          bucket = (bucket + 1) & (newIndex.length - 1);
        }
        newIndex[bucket] = row + 1;
      }
      elements = newElements;
      counts = newCounts;
      generations = newGenerations;
      nextSlots = newNextSlots;
      nextGenerations = newNextGenerations;
      index = newIndex;
    }

    void add(
        @ParametricNullness E element,
        ExportedCount count,
        long generation,
        int nextSlot,
        long nextGeneration) {
      if (size == elements.length) {
        grow();
      }
      elements[size] = element;
      counts[size] = count;
      generations[size] = generation;
      nextSlots[size] = nextSlot;
      nextGenerations[size] = nextGeneration;
      int bucket = hash(generation) & (index.length - 1);
      while (index[bucket] != 0) {
        bucket = (bucket + 1) & (index.length - 1);
      }
      index[bucket] = ++size;
    }
  }

  /** One AVL topology arena shared by the root multiset and every range view. */
  private static final class SlotStore<E extends @Nullable Object> {
    private static final int RETIRED = 2;
    private static final int REACHABLE = 4;
    private static final int OCCUPIED = 8;
    private static final int TREE_REACHABLE = 16;
    private static final int ORDER_REACHABLE = 32;
    private Object[] elements = new Object[8];
    private int[] elemCount = new int[8];
    private int[] distinctElements = new int[8];
    private long[] totalCount = new long[8];
    private byte[] height = new byte[8];
    private int[] left = new int[8];
    private int[] right = new int[8];
    private int[] pred = new int[8];
    private int[] succ = new int[8];
    private int[] freeNext = new int[8];
    private long[] generation = new long[8];
    private byte[] state = new byte[8];
    private ExportedCount[] exportedCounts = new ExportedCount[8];
    private @Nullable CursorSnapshot<?>[] activeCursors;
    private int activeCursorCount;
    private int used = 1;
    private int free = NONE;
    private int retired = NONE;
    private int activeOperations;
    private int callbackDepth;
    private boolean reconcileNeeded;
    private boolean unusualTopology;
    private int root = NONE;
    private long nextGeneration = 1;

    SlotStore() {
      Arrays.fill(left, NONE);
      Arrays.fill(right, NONE);
      Arrays.fill(pred, NONE);
      Arrays.fill(succ, NONE);
      Arrays.fill(freeNext, NONE);
      elemCount[HEADER] = 1;
      successor(HEADER, HEADER);
    }

    int get() {
      return root;
    }

    void checkAndSet(int expected, int newValue) {
      if (root != expected) {
        reconcileNeeded = true;
        throw new ConcurrentModificationException();
      }
      root = newValue;
    }

    void enter() {
      activeOperations++;
    }

    void beginCallback() {
      callbackDepth++;
    }

    void endCallback() {
      callbackDepth--;
    }

    private void structuralChange() {
      if (callbackDepth != 0 || unusualTopology) {
        reconcileNeeded = true;
      }
    }

    int compare(
        Comparator<? super E> comparator,
        @ParametricNullness E first,
        @ParametricNullness E second) {
      beginCallback();
      try {
        return comparator.compare(first, second);
      } finally {
        endCallback();
      }
    }

    /** Retired links survive comparator and visitor reentry until their last active frame exits. */
    void exit() {
      if (--activeOperations == 0) {
        try {
          if (reconcileNeeded || retired != NONE) {
            refreshActiveCursors();
          }
          if (reconcileNeeded) {
            reconcile();
            reconcileNeeded = false;
            return;
          }
          while (retired != NONE) {
            int slot = retired;
            retired = freeNext[slot];
            detachExportedCount(slot);
            elements[slot] = null;
            elemCount[slot] = 0;
            left[slot] = right[slot] = pred[slot] = succ[slot] = NONE;
            distinctElements[slot] = 0;
            totalCount[slot] = 0;
            height[slot] = 0;
            state[slot] = 0;
            freeNext[slot] = free;
            free = slot;
          }
        } finally {
          if (activeCursors != null) {
            for (int cursor = 0; cursor < activeCursorCount; cursor++) {
              activeCursors[cursor].registered = false;
              activeCursors[cursor] = null;
            }
            activeCursors = null;
            activeCursorCount = 0;
          }
        }
      }
    }

    /**
     * A callback may create a cursor before changing any links. Keep its descriptor only until this
     * active operation can refresh the final chain, before any retired generation is reused.
     */
    CursorSnapshot<E> registerCursor(
        int slot, long stamp, boolean descending, @Nullable CursorSnapshot<E> previous) {
      CursorSnapshot<E> cursor = previous == null ? new CursorSnapshot<>() : previous;
      cursor.current = slot;
      cursor.currentGeneration = stamp;
      cursor.descending = descending;
      if (!cursor.registered) {
        if (activeCursors == null) {
          activeCursors = new CursorSnapshot<?>[8];
        } else if (activeCursorCount == activeCursors.length) {
          if (activeCursors.length >= (1 << 29)) {
            throw new OutOfMemoryError("TreeMultiset active cursor capacity exhausted");
          }
          activeCursors = Arrays.copyOf(activeCursors, activeCursors.length * 2);
        }
        activeCursors[activeCursorCount++] = cursor;
        cursor.registered = true;
      }
      return cursor;
    }

    @SuppressWarnings("unchecked") // Every descriptor was registered by this SlotStore<E>.
    private void refreshActiveCursors() {
      if (activeCursors != null) {
        for (int index = 0; index < activeCursorCount; index++) {
          CursorSnapshot<E> cursor = (CursorSnapshot<E>) activeCursors[index];
          if (cursor.current != NONE) {
            cursor.replaceWith(
                captureChain(cursor.current, cursor.currentGeneration, cursor.descending, cursor));
          }
        }
      }
    }

    /**
     * Reentry can reattach a retired node without changing the root identity, or detach an
     * allocated subtree before the outer root check fails. Trace both original authoritative roots
     * and all four links only on that path; ordinary AVL mutation never pays this scan.
     */
    private void reconcile() {
      int[] stack = new int[used];
      for (int slot = 0; slot < used; slot++) {
        state[slot] &= ~(REACHABLE | TREE_REACHABLE | ORDER_REACHABLE);
      }
      int size = enqueue(root, stack, 0, TREE_REACHABLE);
      while (size != 0) {
        int slot = stack[--size];
        size = enqueue(left[slot], stack, size, TREE_REACHABLE);
        size = enqueue(right[slot], stack, size, TREE_REACHABLE);
      }
      size = enqueue(HEADER, stack, 0, ORDER_REACHABLE);
      while (size != 0) {
        int slot = stack[--size];
        size = enqueue(pred[slot], stack, size, ORDER_REACHABLE);
        size = enqueue(succ[slot], stack, size, ORDER_REACHABLE);
      }
      size = 0;
      state[HEADER] |= REACHABLE;
      stack[size++] = HEADER;
      if (root != NONE && (state[root] & REACHABLE) == 0) {
        state[root] |= REACHABLE;
        stack[size++] = root;
      }
      while (size != 0) {
        int slot = stack[--size];
        size = enqueue(left[slot], stack, size, REACHABLE);
        size = enqueue(right[slot], stack, size, REACHABLE);
        size = enqueue(pred[slot], stack, size, REACHABLE);
        size = enqueue(succ[slot], stack, size, REACHABLE);
      }
      free = NONE;
      retired = NONE;
      unusualTopology = false;
      for (int slot = 1; slot < used; slot++) {
        if ((state[slot] & REACHABLE) != 0) {
          if ((state[slot] & (TREE_REACHABLE | ORDER_REACHABLE))
                  != (TREE_REACHABLE | ORDER_REACHABLE)
              || elemCount[slot] <= 0
              || pred[slot] == NONE
              || succ[slot] == NONE
              || succ[pred[slot]] != slot
              || pred[succ[slot]] != slot) {
            unusualTopology = true;
          }
          state[slot] &= ~(REACHABLE | RETIRED | TREE_REACHABLE | ORDER_REACHABLE);
          freeNext[slot] = NONE;
        } else {
          detachExportedCount(slot);
          elements[slot] = null;
          elemCount[slot] = distinctElements[slot] = 0;
          totalCount[slot] = 0;
          height[slot] = 0;
          left[slot] = right[slot] = pred[slot] = succ[slot] = NONE;
          state[slot] = 0;
          freeNext[slot] = free;
          free = slot;
        }
      }
      state[HEADER] = 0;
    }

    private int enqueue(int slot, int[] stack, int size, int flag) {
      if (slot != NONE && (state[slot] & flag) == 0) {
        state[slot] |= flag;
        stack[size++] = slot;
      }
      return size;
    }

    private void detachExportedCount(int slot) {
      ExportedCount exported = exportedCounts[slot];
      if (exported != null) {
        exported.detachedCount = elemCount[slot];
        exportedCounts[slot] = null;
      }
    }

    /** Publish a complete resized lane set only after all allocations succeed. */
    private void grow() {
      long proposed = Math.max(8L, elements.length + (long) (elements.length >>> 1));
      int capacity = (int) Math.min(Integer.MAX_VALUE - 8L, proposed);
      if (capacity <= elements.length) {
        throw new OutOfMemoryError("TreeMultiset slot capacity exhausted");
      }
      Object[] newElements = Arrays.copyOf(elements, capacity);
      int[] newCounts = Arrays.copyOf(elemCount, capacity);
      int[] newDistinct = Arrays.copyOf(distinctElements, capacity);
      long[] newTotals = Arrays.copyOf(totalCount, capacity);
      byte[] newHeights = Arrays.copyOf(height, capacity);
      int[] newLeft = Arrays.copyOf(left, capacity);
      int[] newRight = Arrays.copyOf(right, capacity);
      int[] newPred = Arrays.copyOf(pred, capacity);
      int[] newSucc = Arrays.copyOf(succ, capacity);
      int[] newFreeNext = Arrays.copyOf(freeNext, capacity);
      long[] newGeneration = Arrays.copyOf(generation, capacity);
      byte[] newState = Arrays.copyOf(state, capacity);
      ExportedCount[] newExportedCounts = Arrays.copyOf(exportedCounts, capacity);
      elements = newElements;
      elemCount = newCounts;
      distinctElements = newDistinct;
      totalCount = newTotals;
      height = newHeights;
      left = newLeft;
      right = newRight;
      pred = newPred;
      succ = newSucc;
      freeNext = newFreeNext;
      generation = newGeneration;
      state = newState;
      exportedCounts = newExportedCounts;
    }

    int newNode(@ParametricNullness E element, int count) {
      checkArgument(count > 0);
      if (nextGeneration == Long.MAX_VALUE) {
        throw new OutOfMemoryError("TreeMultiset slot generation exhausted");
      }
      int slot;
      if (free != NONE) {
        slot = free;
        free = freeNext[slot];
      } else {
        if (used == elements.length) {
          grow();
        }
        slot = used++;
      }
      elements[slot] = element;
      elemCount[slot] = count;
      distinctElements[slot] = 1;
      totalCount[slot] = count;
      height[slot] = 1;
      left[slot] = right[slot] = pred[slot] = succ[slot] = NONE;
      freeNext[slot] = NONE;
      generation[slot] = nextGeneration++;
      state[slot] = OCCUPIED;
      structuralChange();
      return slot;
    }

    private void retire(int slot) {
      structuralChange();
      elemCount[slot] = 0;
      if ((state[slot] & RETIRED) == 0) {
        state[slot] |= RETIRED;
        freeNext[slot] = retired;
        retired = slot;
      }
    }

    void clear() {
      structuralChange();
      for (int current = succ(HEADER); current != HEADER; ) {
        int next = succ(current);
        retire(current);
        left[current] = right[current] = pred[current] = succ[current] = NONE;
        current = next;
      }
      successor(HEADER, HEADER);
      root = NONE;
    }

    @SuppressWarnings("unchecked")
    @ParametricNullness
    E element(int slot) {
      return (E) elements[slot];
    }

    long generation(int slot) {
      return generation[slot];
    }

    ExportedCount exportCount(int slot) {
      ExportedCount result = exportedCounts[slot];
      if (result == null) {
        result = new ExportedCount();
        exportedCounts[slot] = result;
      }
      return result;
    }

    CursorSnapshot<E> captureChain(
        int slot, long stamp, boolean descending, @Nullable CursorSnapshot<E> previous) {
      CursorSnapshot<E> captured = new CursorSnapshot<>();
      while (slot != NONE && slot != HEADER && captured.find(stamp) == NONE) {
        int next;
        long nextStamp;
        if (isCurrent(slot, stamp)) {
          next = descending ? pred[slot] : succ[slot];
          nextStamp = next == NONE || next == HEADER ? 0 : generation[next];
          captured.add(element(slot), exportCount(slot), stamp, next, nextStamp);
        } else {
          int row = previous == null ? NONE : previous.find(stamp);
          if (row == NONE) {
            throw new ConcurrentModificationException();
          }
          next = previous.nextSlots[row];
          nextStamp = previous.nextGenerations[row];
          captured.add(previous.element(row), previous.counts[row], stamp, next, nextStamp);
        }
        slot = next;
        stamp = nextStamp;
      }
      return captured;
    }

    boolean isCurrent(int slot, long expectedGeneration) {
      return generation[slot] == expectedGeneration && (state[slot] & OCCUPIED) != 0;
    }

    boolean isRetired(int slot) {
      return (state[slot] & RETIRED) != 0;
    }

    int liveCount(int slot, long expectedGeneration) {
      return generation[slot] == expectedGeneration ? elemCount[slot] : 0;
    }

    void checkCurrent(int slot, long expectedGeneration) {
      if (slot != NONE && !isCurrent(slot, expectedGeneration)) {
        throw new ConcurrentModificationException();
      }
    }

    int pred(int slot) {
      int result = pred[slot];
      if (result == NONE) {
        throw new NullPointerException();
      }
      return result;
    }

    int succ(int slot) {
      int result = succ[slot];
      if (result == NONE) {
        throw new NullPointerException();
      }
      return result;
    }

    void successor(int before, int after) {
      succ[before] = after;
      pred[after] = before;
    }

    void successor(int before, int middle, int after) {
      successor(before, middle);
      successor(middle, after);
    }

    int distinctElements(int slot) {
      return slot == NONE ? 0 : distinctElements[slot];
    }

    long totalCount(int slot) {
      return slot == NONE ? 0 : totalCount[slot];
    }

    int height(int slot) {
      return slot == NONE ? 0 : height[slot];
    }

    int count(int slot, Comparator<? super E> comparator, @ParametricNullness E element) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        return left[slot] == NONE ? 0 : count(left[slot], comparator, element);
      } else if (cmp > 0) {
        return right[slot] == NONE ? 0 : count(right[slot], comparator, element);
      }
      return elemCount[slot];
    }

    private int addRightChild(int slot, @ParametricNullness E element, int count) {
      int child = newNode(element, count);
      right[slot] = child;
      successor(slot, child, succ(slot));
      height[slot] = (byte) max(2, height[slot]);
      distinctElements[slot]++;
      totalCount[slot] += count;
      return slot;
    }

    private int addLeftChild(int slot, @ParametricNullness E element, int count) {
      int child = newNode(element, count);
      left[slot] = child;
      successor(pred(slot), child, slot);
      height[slot] = (byte) max(2, height[slot]);
      distinctElements[slot]++;
      totalCount[slot] += count;
      return slot;
    }

    int add(
        int slot,
        Comparator<? super E> comparator,
        @ParametricNullness E element,
        int count,
        int[] result) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        int initLeft = left[slot];
        if (initLeft == NONE) {
          result[0] = 0;
          return addLeftChild(slot, element, count);
        }
        int initHeight = height[initLeft];
        // The recursive call may grow or reenter the arena: reload the lane before publishing.
        int child = add(initLeft, comparator, element, count, result);
        left[slot] = child;
        if (result[0] == 0) {
          distinctElements[slot]++;
        }
        totalCount[slot] += count;
        return height[child] == initHeight ? slot : rebalance(slot);
      } else if (cmp > 0) {
        int initRight = right[slot];
        if (initRight == NONE) {
          result[0] = 0;
          return addRightChild(slot, element, count);
        }
        int initHeight = height[initRight];
        int child = add(initRight, comparator, element, count, result);
        right[slot] = child;
        if (result[0] == 0) {
          distinctElements[slot]++;
        }
        totalCount[slot] += count;
        return height[child] == initHeight ? slot : rebalance(slot);
      }
      result[0] = elemCount[slot];
      long resultCount = (long) elemCount[slot] + count;
      checkArgument(resultCount <= Integer.MAX_VALUE);
      elemCount[slot] += count;
      totalCount[slot] += count;
      return slot;
    }

    int remove(
        int slot,
        Comparator<? super E> comparator,
        @ParametricNullness E element,
        int count,
        int[] result) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        int child = left[slot];
        if (child == NONE) {
          result[0] = 0;
          return slot;
        }
        int replacement = remove(child, comparator, element, count, result);
        left[slot] = replacement;
        if (result[0] > 0) {
          if (count >= result[0]) {
            distinctElements[slot]--;
            totalCount[slot] -= result[0];
          } else {
            totalCount[slot] -= count;
          }
        }
        return result[0] == 0 ? slot : rebalance(slot);
      } else if (cmp > 0) {
        int child = right[slot];
        if (child == NONE) {
          result[0] = 0;
          return slot;
        }
        int replacement = remove(child, comparator, element, count, result);
        right[slot] = replacement;
        if (result[0] > 0) {
          if (count >= result[0]) {
            distinctElements[slot]--;
            totalCount[slot] -= result[0];
          } else {
            totalCount[slot] -= count;
          }
        }
        return rebalance(slot);
      }
      result[0] = elemCount[slot];
      if (count >= elemCount[slot]) {
        return deleteMe(slot);
      }
      elemCount[slot] -= count;
      totalCount[slot] -= count;
      return slot;
    }

    int setCount(
        int slot,
        Comparator<? super E> comparator,
        @ParametricNullness E element,
        int count,
        int[] result) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        int child = left[slot];
        if (child == NONE) {
          result[0] = 0;
          return count > 0 ? addLeftChild(slot, element, count) : slot;
        }
        int replacement = setCount(child, comparator, element, count, result);
        left[slot] = replacement;
      } else if (cmp > 0) {
        int child = right[slot];
        if (child == NONE) {
          result[0] = 0;
          return count > 0 ? addRightChild(slot, element, count) : slot;
        }
        int replacement = setCount(child, comparator, element, count, result);
        right[slot] = replacement;
      } else {
        result[0] = elemCount[slot];
        if (count == 0) {
          return deleteMe(slot);
        }
        totalCount[slot] += count - elemCount[slot];
        elemCount[slot] = count;
        return slot;
      }
      if (count == 0 && result[0] != 0) {
        distinctElements[slot]--;
      } else if (count > 0 && result[0] == 0) {
        distinctElements[slot]++;
      }
      totalCount[slot] += count - result[0];
      return rebalance(slot);
    }

    int setCount(
        int slot,
        Comparator<? super E> comparator,
        @ParametricNullness E element,
        int expectedCount,
        int newCount,
        int[] result) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        int child = left[slot];
        if (child == NONE) {
          result[0] = 0;
          return expectedCount == 0 && newCount > 0 ? addLeftChild(slot, element, newCount) : slot;
        }
        int replacement = setCount(child, comparator, element, expectedCount, newCount, result);
        left[slot] = replacement;
      } else if (cmp > 0) {
        int child = right[slot];
        if (child == NONE) {
          result[0] = 0;
          return expectedCount == 0 && newCount > 0 ? addRightChild(slot, element, newCount) : slot;
        }
        int replacement = setCount(child, comparator, element, expectedCount, newCount, result);
        right[slot] = replacement;
      } else {
        result[0] = elemCount[slot];
        if (expectedCount == elemCount[slot]) {
          if (newCount == 0) {
            return deleteMe(slot);
          }
          totalCount[slot] += newCount - elemCount[slot];
          elemCount[slot] = newCount;
        }
        return slot;
      }
      if (result[0] == expectedCount) {
        if (newCount == 0 && result[0] != 0) {
          distinctElements[slot]--;
        } else if (newCount > 0 && result[0] == 0) {
          distinctElements[slot]++;
        }
        totalCount[slot] += newCount - result[0];
      }
      return rebalance(slot);
    }

    private int deleteMe(int slot) {
      int oldElemCount = elemCount[slot];
      retire(slot);
      successor(pred(slot), succ(slot));
      if (left[slot] == NONE) {
        return right[slot];
      } else if (right[slot] == NONE) {
        return left[slot];
      } else if (height[left[slot]] >= height[right[slot]]) {
        int newTop = pred(slot);
        int replacement = removeMax(left[slot], newTop);
        left[newTop] = replacement;
        right[newTop] = right[slot];
        distinctElements[newTop] = distinctElements[slot] - 1;
        totalCount[newTop] = totalCount[slot] - oldElemCount;
        return rebalance(newTop);
      } else {
        int newTop = succ(slot);
        int replacement = removeMin(right[slot], newTop);
        right[newTop] = replacement;
        left[newTop] = left[slot];
        distinctElements[newTop] = distinctElements[slot] - 1;
        totalCount[newTop] = totalCount[slot] - oldElemCount;
        return rebalance(newTop);
      }
    }

    private int removeMin(int slot, int moving) {
      if (left[slot] == NONE) {
        return right[slot];
      }
      left[slot] = removeMin(left[slot], moving);
      distinctElements[slot]--;
      totalCount[slot] -= elemCount[moving];
      return rebalance(slot);
    }

    private int removeMax(int slot, int moving) {
      if (right[slot] == NONE) {
        return left[slot];
      }
      right[slot] = removeMax(right[slot], moving);
      distinctElements[slot]--;
      totalCount[slot] -= elemCount[moving];
      return rebalance(slot);
    }

    private void recomputeHeight(int slot) {
      height[slot] = (byte) (1 + max(height(left[slot]), height(right[slot])));
    }

    private void recompute(int slot) {
      distinctElements[slot] = 1 + distinctElements(left[slot]) + distinctElements(right[slot]);
      totalCount[slot] = elemCount[slot] + totalCount(left[slot]) + totalCount(right[slot]);
      recomputeHeight(slot);
    }

    private int rebalance(int slot) {
      switch (balanceFactor(slot)) {
        case -2:
          if (balanceFactor(right[slot]) > 0) {
            right[slot] = rotateRight(right[slot]);
          }
          return rotateLeft(slot);
        case 2:
          if (balanceFactor(left[slot]) < 0) {
            left[slot] = rotateLeft(left[slot]);
          }
          return rotateRight(slot);
        default:
          recomputeHeight(slot);
          return slot;
      }
    }

    private int balanceFactor(int slot) {
      return height(left[slot]) - height(right[slot]);
    }

    private int rotateLeft(int slot) {
      checkState(right[slot] != NONE);
      int newTop = right[slot];
      right[slot] = left[newTop];
      left[newTop] = slot;
      totalCount[newTop] = totalCount[slot];
      distinctElements[newTop] = distinctElements[slot];
      recompute(slot);
      recomputeHeight(newTop);
      return newTop;
    }

    private int rotateRight(int slot) {
      checkState(left[slot] != NONE);
      int newTop = left[slot];
      left[slot] = right[newTop];
      right[newTop] = slot;
      totalCount[newTop] = totalCount[slot];
      distinctElements[newTop] = distinctElements[slot];
      recompute(slot);
      recomputeHeight(newTop);
      return newTop;
    }

    int ceiling(int slot, Comparator<? super E> comparator, @ParametricNullness E element) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp < 0) {
        if (left[slot] == NONE) {
          return slot;
        }
        int candidate = ceiling(left[slot], comparator, element);
        return candidate == NONE ? slot : candidate;
      } else if (cmp == 0) {
        return slot;
      }
      return right[slot] == NONE ? NONE : ceiling(right[slot], comparator, element);
    }

    int floor(int slot, Comparator<? super E> comparator, @ParametricNullness E element) {
      int cmp = compare(comparator, element, element(slot));
      if (cmp > 0) {
        if (right[slot] == NONE) {
          return slot;
        }
        int candidate = floor(right[slot], comparator, element);
        return candidate == NONE ? slot : candidate;
      } else if (cmp == 0) {
        return slot;
      }
      return left[slot] == NONE ? NONE : floor(left[slot], comparator, element);
    }
  }

  /*
   * TODO(jlevy): Decide whether entrySet() should return entries with an equals() method that
   * calls the comparator to compare the two keys. If that change is made,
   * AbstractMultiset.equals() can simply check whether two multisets have equal entry sets.
   */

  /**
   * @serialData the comparator, the number of distinct elements, the first element, its count, the
   *     second element, its count, and so on
   */
  @GwtIncompatible
  @J2ktIncompatible
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    stream.writeObject(elementSet().comparator());
    Serialization.writeMultiset(this, stream);
  }

  @J2ktIncompatible
  @GwtIncompatible
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    @SuppressWarnings("unchecked")
    // reading data stored by writeObject
    Comparator<? super E> comparator = (Comparator<? super E>) requireNonNull(stream.readObject());
    deserializationReplacement = new TreeMultiset<>(comparator);
    Serialization.populateMultiset(deserializationReplacement, stream);
  }

  @J2ktIncompatible
  @GwtIncompatible
  private Object readResolve() {
    return requireNonNull(deserializationReplacement); // set by readObject
  }

  @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 1;
}

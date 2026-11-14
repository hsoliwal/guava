<!-- SPDX-License-Identifier: Apache-2.0 -->
# Direct Guava LinkedListMultimap value-link arrayification

**Product owner changed:** real Guava JRE and Android
`com.m3.collections.guava.collect.LinkedListMultimap` sources in fork
`hsoliwal/guava` [PR #36](https://github.com/hsoliwal/guava/pull/36).
Original `Platform.newHashMapWithExpectedSize` / `CompactHashMap` is
reused; a redundant key-directory attempt was rejected (PR #29).

## Storage invariant

Each logical mapping has a stable physical integer slot; keys and
values are `Object[]`, global/sibling directions and free slots are
`int[]`. The public mutable `Map.Entry` boundary remains an existing
nested `Node` type and is materialized only on explicit `entries()`
access. A separate nullable `Object[] entryBoundaries` array is allocated
**only at first escaped entry**, not for `put/get/values/keySet`. The
entry cache detaches on removal/clear, so a stale escaped entry retains
its last value when its physical slot is reused.

The original `Node` serialized UID is explicitly preserved as
`-2359543990797351600L`, independently verified with Java
`ObjectStreamClass.lookup` on a synthetic original nested class under
the same binary name and field declarations.

## Executed scoped Java proof

```sh
bash m3/array-storage-20261114/linkedlistmultimap/verify_model.sh
```

The exact checked-in standalone model Git blob is
`417bad41aa1679d4d4ba8e4552819ef1452daed0`.
It was independently compiled locally using
`javac --release 21 -Xlint:all,-serial -Werror` and executed:

- Default JVM: `PASS 13236380` checks (20 seeds x 20,000 operations)
- Interpreter `-Xint`: `PASS 248184` checks
  (3 seeds x 2,500 operations)
- Additional nullable-key/value and lazy allocation invariants cover untouched entry cache,
  first explicit public entry view, detach and clear.

This is an algorithm-model proof, **not** a full Guava classpath compile.
Both real Guava JRE and Android production files are changed by the PR
with native JUnit3 regression tests and a focused Maven workflow.

## Further acceptance required (HOLD)

Original Guava JRE/Android provider and build matrices; NullAway and
JSpecify flow analysis; reentrant hashing and external entry callbacks;
public ABI and serialized stream compatibility against exact vendor
build; memory/JMH under read-only and entry-heavy workloads; canonical
Synexia Maven/OpenRewrite qualification; installed M3JDK21/JNI/JTReg.
No false claim that a hosted CI run has passed or master was merged.

## Category and donor references

- In-place parent-owned array indexed linked list: GeeksforGeeks
  https://www.geeksforgeeks.org/dsa/doubly-linked-list/
- Hash-and-index map design tradeoffs: LeetCode 706
  https://leetcode.com/problems/design-hashmap/
- Existing code donor: original Guava LinkedListMultimap/CompactHashMap,
  Apache-2.0; original headers remain.
- HPPC primitive array collections: https://github.com/carrotsearch/hppc
  (Apache-2.0, comparison only, no code copied)
- Agrona primitive maps: https://github.com/aeron-io/agrona
  (Apache-2.0, comparison only, no code copied).

Competitive programming problem descriptions provide algorithm ideas
only, never copied source or synthetic claims of performance.

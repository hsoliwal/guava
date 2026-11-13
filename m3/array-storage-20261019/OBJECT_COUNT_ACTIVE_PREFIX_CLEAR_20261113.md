<!-- SPDX-License-Identifier: Apache-2.0 -->
# Direct Guava ObjectCountHashMap active-prefix clear (2026-11-13)

**Target:** `hsoliwal/guava` public scratch collection fork, not a M3JDK adapter. This optimization changes the real existing Guava array-backed count map; no new storage backend, entry objects, JNI, Maven target or public API. Reusable production transformation recipes and subsequent M3JDK admission remain separate canonical Synexia responsibilities.

## Exact source and frozen semantics

- Branch base: `hsoliwal/guava@4ff0f361b83ad95b93b623998f53ade8613273f2`, file `guava/src/com/m3/collections/guava/collect/ObjectCountHashMap.java`, original Git blob `25b08b0332ad2c1bf225dd9b8b2af7404222ab8f`.
- Original full UTF-8 SHA-256 `d56bd54224ebe51ed7a66875bb0c3063e8b3f95090bc3eaaeadfaa13473c38a1`; proposed postimage full UTF-8 SHA-256 `e06dfe849c59eb35d45f675af607f21394a61f0333141adcc19ecbe96cd09da1`.
- One method-local atom: `clear()` previously calls `Arrays.fill(entries, UNSET)` across **entire retained capacity**. The source independently proves that `entries[size..capacity)` is already `UNSET` after initialization, expansion, insertion, and `moveLastEntry` deletion. Clear only the active `[0,size)` prefix when capacity exceeds size; when full, retain the exact original `Arrays.fill(long[], long)` path.
- Preserve `modCount++`, flooding-index release, key/value cleanup, bucket table reset, explicit `size = 0`, key identity, entry projection, primitive count, negative/zero counts, mutation and iteration semantics. `entries` is package-private test-visible; externally corrupting its inactive suffix is not an admitted map operation. All valid state outputs are byte-identical after clear.

## Executed Java21 atom qualification

The standalone exact `clear()` storage-atom oracle exercises capacities 1..262,144, tables of 2 and 1,024 slots, live sizes 0..2,048, repeated reuse, and full array equality (including the `UNSET` suffix). **5,327,882 checks passed per JVM**, with Java21 `javac -Xlint:all -Werror`. Five JVMs each ran nine warmed alternating baseline/candidate samples (64 clear operations/sample) after the adaptive dense guard:

- Sparse flooding table (262,144 entry slots, 16 live, 2 buckets): candidate/baseline ratios **0.0058, 0.0076, 0.0056, 0.0056, 0.0075**. The same storage becomes reusable without scanning every unused long.
- Sparse normal table (262,144 entries, 262,144 buckets, 16 live): ratios **0.3708, 0.3605, 0.3537, 0.3527, 0.3366**.
- Dense (8,192 entries all live): ratios **1.0034, 1.0060, 1.0023, 1.0002, 0.9877**. These are effectively neutral but show small regressions; a strict cross-platform non-regression verdict is still **PENDING**.

The atom harness models precisely the existing parallel `Object[]` / `int[]` / `long[]` / `int[]` clear sequence; this is not the whole Guava test suite. An actual fork-owned JUnit3 regression test is added. Run the Guava JRE/Android tests and the full source/API checker before merging. Do not promote to M3JDK on standalone evidence alone.

## Refusal and release boundary

No default branch modification; no merges. The local runtime does not contain the full Guava Maven/test dependency closure, so full JRE/Android/unit and GWT compatibility gates are **NOT_RUN**; record source diff, Git hash readback, exact JUnit test and CI dispositions separately. No package relocation or license transfer: original Guava Apache-2.0 notices remain intact.

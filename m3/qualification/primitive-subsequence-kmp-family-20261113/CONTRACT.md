<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava primitive subsequence search: direct seven-class fork optimization

**Source fork:** `hsoliwal/guava@06387d46d087bf79032b51afbd814ad467fffda0`. This batch modifies the seven existing production classes `Bytes`, `Shorts`, `Chars`, `Longs`, `Booleans`, `Floats`, and `Doubles` in `guava/src/com/m3/collections/guava/primitives/`. There are no M3JDK/adapter/other-fork edits. `Ints.indexOf(int[], int[])` is owned by independent, already open [Guava PR #34](https://github.com/hsoliwal/guava/pull/34); do not duplicate or modify it here.

## Problem category and immutable external semantics

The current public `indexOf(primitive[] array, primitive[] target)` uses nested scalar loops, with worst-case quadratic rechecking on periodic prefixes. Keep the exact existing method signature, array-then-target null-check order, empty target -> 0, first occurrence, no occurrence -> -1, and primitive `==` semantics. For floats/doubles this includes NaN never equaling itself and -0.0 matching +0.0. Preserve all other methods, imports, GWT/Android licensing and class contracts. No retained arrays or new collection types.

## One deterministic replaceable atom per class

For short needles (<32), oversized patterns (>65,536), or short haystacks (<4,096), run the **original nested loops unchanged**: no extra array allocation. For larger eligible operands:
1. If the pattern is longer than the haystack, return -1.
2. Check offset zero with primitive equality; return 0 immediately on full match without allocating scratch.
3. Scan the immutable primitive pattern for a repeated four-element prefix. If absent, run the original scalar search on shifts 1 onward (shift zero was already checked); no scratch allocated.
4. Otherwise build one `int[]` KMP failure table sized exactly to the pattern (<=256 KiB), then search once with the same primitive equality and return the earliest match. Both the preprocessing and scan are linear.

This preflight avoids the measured overhead of blindly running KMP for random patterns and first-position matches, while accelerating repetitive adversarial patterns. No per-entry objects, secondary retained storage, JNI or signature change. The specialized `Float`/`Double` handling uses exactly `==` (not `Float.compare`/`Double.compare`) to preserve NaN/signed-zero behavior.

## Executed bounded Java 21 evidence before fork write

The standalone exact-method family witness compiled under `javac -Xlint:all -Werror`. Original vs candidate:
- **166,726 deterministic checks PASS** in normal JIT execution across all seven types, including randomized inputs, alignment, overlapping prefixes, empty/no-match/last-match, float/double NaN, negative zero, and self-aliasing.
- A separate bounded subset of **6,930 checks PASS** in both JIT and `-Xint`, with byte-identical transcripts (SHA-256 `b045a20ca8b9fc261d8d6c0a6dc2ed459122194d94d3258ddd76edbf30d5388a`).
- Full interpreted replay was stopped at the execution limit, not counted as PASS. The first two source variants were rejected after measured random/offset-zero regressions; this branch uses the adaptive prefilter.
- Bounded period-miss benchmark (Java 21, 65,536 elements and 512-element repeated-prefix needle) measured candidate/baseline ratios around 0.005–0.012 in five samples, while random and offset-zero samples were variable. These are **microbenchmarks of the source algorithm only**, not Guava/JMH/release evidence.

## Required receiving gates

Before merge: exact Git blob/SHA-256 for each of the seven full source files; full reverse-diff proving one changed `indexOf(array,target)` per class; actual `guava-tests` native regression and existing primitive suites, JRE/Android/GWT-compatible build, source/ABI compatibility and representative performance. The full Guava dependency closure and Maven/Bazel suites are **NOT_RUN** in this environment. Deliver on a reviewable draft PR only; no default-branch changes, merges or M3JDK receiving without independent verification. Keep an explicit gate ledger and rejected alternatives.

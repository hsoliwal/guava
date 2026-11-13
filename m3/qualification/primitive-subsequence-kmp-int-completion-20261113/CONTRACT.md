<!-- SPDX-License-Identifier: Apache-2.0 -->
# Completing Guava's 16-class primitive subsequence optimization — Ints in JRE + Android

**Stack parent:** `hsoliwal/guava` [PR #35](https://github.com/hsoliwal/guava/pull/35), immutable head `f59c4a569cc0c43256fab3fd5ed18326d4e4211c`. **Receiver:** `perf/primitive-kmp-ints-jre-android-20261113`. No master writes, no merges, no unrelated donor infrastructure.

## Existing source inventory, not assumed equivalent

| Existing owner | Exact parent Git blob |
| --- | --- |
| `guava/src/com/m3/collections/guava/primitives/Ints.java` | `e379ad3971cdaadd868d3cd72044879571675c34` |
| `android/guava/src/com/m3/collections/guava/primitives/Ints.java` | `4b470728ffb5bf49ad92e9fbdc7b2c096f44cae2` |

The JRE and Android files are **not byte-equivalent** and must never be copied over each other. Change only their existing `public static int indexOf(int[] array, int[] target)` method bodies. Preserve every other byte, method/field/interface signature, null-check precedence, serialization, J2KT/GWT annotations, JNI symbols, package and surrounding implementation.

PR #35 already changes `Bytes/Shorts/Chars/Longs/Booleans/Floats/Doubles` for both compilation roots (**14 classes**), with bounded adaptive KMP. Open [PR #34](https://github.com/hsoliwal/guava/pull/34) independently proposes a JRE-only `Ints` KMP, but unconditional KMP can regress random patterns; do not modify or merge #34 silently. This stacked change adds **only the two previously unmodified Ints owners** to the common PR #35 algorithmic family, making a candidate 16-class integrated wave.

## Exact deterministic algorithm leaf and preservation

Use the already-reviewed PR #35 primitive-adaptive matcher for `int[]`:
- Verify `array` nonnull before `target` nonnull, then return 0 on an empty target.
- Retain the original unallocated nested search when `m < 32`, `m > 65536` or `n < 4096`.
- Refuse `m > n` before indexing the input, after the small-input test.
- Scan target at index 0 before allocating; if exact full first match, return 0.
- Detect an exact repeat of the first **four** primitive needle integers at a later offset. If none occurs, use the original scalar loop beginning at offset 1.
- For repeating patterns, allocate no more than 65,536 `int` failure links (256 KiB) and perform standard KMP to return the earliest matching offset.
- Primitive `==` equality, no boxing/comparators/custom code, zero modification of input arrays, no new public API/fields.
- O(n+m) for admitted repeating-prefix needles; original O(n*m) worst case remains on scalar fallback. Do not claim unconditional O(n+m) or universal speedup.

Follow M3 change stages: documentation first, exact Git blob/UTF-8 SHA-256 before+after each owner, file-only diff and readback; `javac --release 21 -proc:none -Xlint:all -Werror` on exact baseline/current methods; randomized and adversarial first-hit/overlap/periodic/empty/aliases/signed extremes/null behavior; replay across mixed, `-Xint`, `-Xcomp`; representative periodic + random microbenchmark; native Guava JRE and Android tests authored in each root.

**HOLD:** full Guava module/Bazel/Maven/JUnit, Android build, J2KT/GWT, built M3JDK21 receiver/jtreg/ABI, JMH and licence/NOTICE, unless actual executed logs prove otherwise. This is a two-source, one-algorithm stacked PR, not a fork-wide release assertion.

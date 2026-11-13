<!-- SPDX-License-Identifier: Apache-2.0 -->
# M3 collections primitive-search wave — seven Guava fork owners

**Receiver:** `hsoliwal/guava/master@06387d46d087bf79032b51afbd814ad467fffda0`. **Status:** proposed exact-source optimization, review-only, no merge. **Independent previous owner:** open PR #34 changes `Ints.indexOf(int[],int[])`; do not touch `Ints.java` in this wave.

## Serial, content-pinned source inventory

All seven source owners are in `guava/src/com/m3/collections/guava/primitives/`:

| Owner | Original Git blob SHA-1 | Primitive semantics |
| --- | --- | --- |
| `Bytes.java` | `6f774cc6246bd9435652e589414917cc02b4ed52` | byte == |
| `Shorts.java` | `2f2fb069f7b6c7f39c7f480650b593f58cd4d8bf` | short == |
| `Chars.java` | `a8122eda9a474b6610b1e4e06d4ff7ef6f6c472c` | char == |
| `Longs.java` | `fb526a19590110dfdf7193d7e8d2f48a34d7617a` | long == |
| `Booleans.java` | `318fde63bb37033da0b98af65ddc3289691192c7` | boolean == |
| `Floats.java` | `da8633d6183defcc3bebf988ce185c32b93c1423` | float ==; NaN never matches; +0/-0 match |
| `Doubles.java` | `ee13442c8e8fc8cb5c51f2d5ca56a18bc1c32688` | double ==; NaN never matches; +0/-0 match |

No existing class is moved, deleted, renamed, repackaged, merged into another semantic owner or given a new public method. **Modify exactly the existing `public static int indexOf(primitive[] array, primitive[] target)` body in each file.** Do not touch unrelated `indexOf`, `lastIndexOf`, converters, public API, JNI bindings, serialization, or other collection owners. One family test source and a reproducible benchmark cover all seven, retaining separate original test owners.

## Algorithm by problem category: first occurrence of a primitive sequence

The present source performs a naïve nested scan, worst-case O(n*m) equality comparisons on a periodic-prefix miss (for example 200,000 copies of one value and a pattern of 127 copies of that value followed by a different value). Each owner gets the same bounded **Knuth–Morris–Pratt failure-link algorithm**, with *primitive-specialized comparisons* and a deterministic `int[]` prefix table. No copied competitive-programming solution, reflection, boxing, external library or object allocation per character.

Fast-path gate: `array.length >= 4096 && target.length >= 32 && target.length <= 65536 && target.length <= array.length`. Max prefix storage 65,536 integers = 256 KiB. Preserve old original nested loop literally for short inputs, short needles, and long needles beyond this budget. Added early impossible-length return `-1` follows the original result for `target.length > array.length` after the original ordered null and empty checks. Both paths return the **first** match index, including overlap and self-alias.

The failure-link and haystack comparisons use the type's **primitive `==`**, not `Object.equals`/bitwise equality. Float/double NaN must not match any input, including another NaN; `-0.0` and `+0.0` must match. Those cases are mandatory differential tests. Since a pattern position containing NaN cannot match any text value, KMP must produce no false match. No caller callback or comparison side effects exist for primitives.

## Proof and admission order

1. Exact preimage Git blobs and source contract; this documentation is committed **before** any Java patch.
2. One file at a time: unique search-method match, preserve full postimage and old blob metadata, reread exact changed Git blob after each update.
3. Strict Java 21 **algorithm-level** compile and deterministic baseline-versus-candidate differential tests for every primitive family (sizes below/at/above gates, periodic/overlap/alias, first-match, negatives/extremes, NaN and signed zeros).
4. Replay in mixed, `-Xint`, and `-Xcomp`. Capture raw logs and SHA-256. Benchmark pathological periodic-prefix and typical random needles separately; report slowdowns too.
5. Real fork Maven/JUnit, module compiler, CI and JMH. These are **HOLD** if unavailable, and no result from a local method-only proof may be promoted as a full-fork test.
6. Only after real source/class tests and corresponding receiving API/ABI, re-evaluate convergence into `hsoliwal/M3jdk21`. No silent source import or automatic merge.

**Behavior invariants:** null validation order (`array` before `target`), empty needle → 0, equal-length self-match → 0, absence → -1, unsigned `char` semantics via native `char ==`, exact first matching index, no input mutation, existing overloads locked, no extra public API. The prior `Ints` PR is an independent work item and is not modified.

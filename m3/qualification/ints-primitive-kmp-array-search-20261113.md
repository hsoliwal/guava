<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava primitive-array subsequence search: bounded KMP leaf

**Source owner:** `hsoliwal/guava/guava/src/com/m3/collections/guava/primitives/Ints.java`.
**Pinned receiver:** `master@06387d46d087bf79032b51afbd814ad467fffda0`.
**Preimage Git blob:** `e379ad3971cdaadd868d3cd72044879571675c34`.

## Frozen public contracts

Preserve `public static int indexOf(int[] array, int[] target)`, including the
order of `checkNotNull(array, "array")` then `checkNotNull(target, "target")`,
the empty-needle result `0`, first-match semantics, primitive signed-int
equality, `-1` for absence, and behavior of overlapping prefixes,
self-aliasing, negative values, and `Integer.MIN_VALUE/MAX_VALUE`.
Do not change the single-value `indexOf(int[],int)`, any public API, the
package, serialized contracts, JNI bindings, primitive array ownership or
any other collection class.

## Independent algorithm atom

The incumbent nested-loop `indexOf(array,target)` is worst-case O(n*m)
for a haystack of n ints and needle of m ints. For large inputs, use
Knuth-Morris-Pratt prefix failure links in a bounded `int[] prefix` of
length m, then stream the haystack once, returning the first match.
Time O(n+m); extra memory O(m). No donor or coding-contest submission is
copied. The standard KMP algorithm is independently implemented against
Guava's primitive array API.

**Admission boundaries:** first validate nulls/empty array conditions
exactly as before. Only enable KMP for `array.length >= 4096`,
`target.length >= 32`, and `target.length <= 65536`. If target is longer
than haystack, return `-1`. All smaller, short, and deliberately unbounded
needles keep the exact original nested-loop source as fallback. The 65,536
ceiling limits extra failure-link storage to 256 KiB; no side-effecting
equality or comparator callback exists in this primitive path.

## Proof gate

Documentation first; exact source pre/post Git blob and SHA-256.
Java 21 strict `javac --release 21 -proc:none -Xlint:all -Werror` against
an isolated exact method body, plus original-vs-KMP differential:
empty and alias cases, worst-case repeated runs, overlapping prefixes,
extreme int values, seeded randomized arrays/needles and adversarial
mismatches. 3-mode mixed/interpreted/compiled replay and pre/post hashes.
Compare matched index, never only boolean containment. Benchmark worst-case
and ordinary workloads separately; label method-only timings as
exploratory and not full-fork/JMH qualification.

Add native Guava JUnit test source alongside original `IntsTest.java`
without editing the existing test. Full Guava reactor, actual relocated
`Ints.java` compile with its dependencies, receiver M3JDK21 ABI/JNI,
licence and release gates remain HOLD until executed. No broad class
rewrite or silent merge.

<!-- SPDX-License-Identifier: Apache-2.0 -->
# Primitive-array KMP donor implementation — Java21 verification and HOLD

**Receiver:** `hsoliwal/guava/master@06387d46d087bf79032b51afbd814ad467fffda0`.
**Existing product source:** `guava/src/com/m3/collections/guava/primitives/Ints.java`.
Before Git blob `e379ad3971cdaadd868d3cd72044879571675c34`;
candidate Git blob `50224c45e1ed48007000a51472c3ff37bbe0bde7`.

The exact `indexOf(int[], int[])` public signature and null/empty/first-match
semantics are unchanged. For n>=4096 and 32<=m<=65536, the method builds
a bounded primitive KMP failure-link `int[]` and scans the input once
(O(n+m), <=256 KiB temporary prefix); otherwise the **unchanged original**
nested loop remains available. No new APIs, JNI functions, fields,
delegate libraries, or public algorithm objects.

## Executed isolated Java 21 proof

An independent **method-level** Java21 source containing the exact new
algorithm atom and a literal incumbent nested-loop oracle was compiled
using `javac --release 21 -proc:none -Xlint:all -Werror`. No diagnostics,
compiler output SHA-256
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

- **21,998 deterministic original-vs-KMP checks PASS**: seeded lengths
  0–16,384, target lengths 0–1,024, overlapping prefixes, adversarial
  periodic misses, first-hit ordering, self alias and >65,536 fallback.
- A bounded **197-check** replay passed identically under mixed, `-Xint`,
  `-Xcomp`; each output SHA-256
  `7566b6c502e69688f5c74bc709cad4cfe28c1407ee87e7bc2d1527f9cdf0870a`.
- Full-run output SHA-256
  `9ea713cc9cad5efee008bd2c0be67f05ff7614e2a17ca8bb1636fe1855e08215`.

This local proof substitutes **only Guava's null-validation dependency** with
the JDK equivalent; it does not compile the full relocated Guava `Ints`
class nor execute the actual Guava Maven suite. The full candidate Java
file was separately fetched and read back by the GitHub connector.

## Exploratory Java21 -Xbatch timings, not JMH

The method-only source was run in the same benchmark harness with two
warmup and seven timed samples for 200,000-int haystacks.

| 200,000-int absent search | Original median | KMP median | Observation |
| --- | ---: | ---: | --- |
| 64-int periodic prefix | 3,149,526 ns | 382,036 ns | ~8.2x observed |
| 128-int periodic prefix | 5,888,513 ns | 457,490 ns | ~12.9x observed |
| 1,024-int periodic prefix | 44,554,669 ns | 411,080 ns | ~108x observed |
| 128-int random needle | 31,457 ns | 31,708 ns | approximately unchanged |
| 1,024-int random needle | 31,747 ns | 33,229 ns | ~4.7% slower observed |

These are **exploratory method-only measurements**, not full-fork latency
claims; JIT/GC interference and repeat sampling remain to be qualified.
A benchmark that executes the *actual fork class* is committed in
`m3/qualification/ints-primitive-kmp-array-search-20261113/IntsKmpBenchmark.java`
but is **authored, not executed under the actual Guava Maven dependency graph**.

## Source tests and immutable proof artifact

Five new **Guava JUnit3-style** `IntsPrimitiveKmpTest` methods are committed
under the existing `guava-tests` package and cover seeded boundaries,
periodic prefix, extreme primitive values, bounded fallback and null cases.
They are **authored, not claimed executed in Maven/JUnit**.

Portable standalone proof ZIP:
`guava_ints_primitive_kmp_method_proof_20261113.zip` SHA-256
`a7f6b37d6cfae80f22eb2eecff33d04671e18daaa1e0ba41c17b1c88669f4dd0`.
The 18-member ZIP's content CRC and every embedded file SHA-256 were
verified; includes the method-only Java source, class files, original
oracle, strict compiler logs, all three replay logs, exploratory benchmark
and receipt. The ZIP is a conversation artifact and not a source import
or JDK release.

**HOLD:** complete Guava `Ints.java` build, original IntsTest and new
IntsPrimitiveKmpTest Maven/JUnit, Guava module CI, M3JDK21 receiving
class/source parity and JNI/jtreg, licence/NOTICE and full performance
qualification. No automatic merge or all-collections-complete claim.

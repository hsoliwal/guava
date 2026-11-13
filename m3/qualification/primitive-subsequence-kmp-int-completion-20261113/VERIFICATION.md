<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava JRE + Android Ints adaptive KMP — executed Java 21 source-method proof

**Execution date:** 2026-11-13. **Stacked receiver:** [Guava PR #35](https://github.com/hsoliwal/guava/pull/35), exact base `f59c4a569cc0c43256fab3fd5ed18326d4e4211c`. **Status:** REAL_SOURCE_METHOD_BYTE_PARITY_PASS / FULL_GUAVA_MODULE_HOLD.

## Exact source identity and preserved semantic skeleton

| Existing owner | Parent Git blob | Candidate Git blob | Full postimage SHA-256 |
| --- | --- | --- | --- |
| JRE `guava/src/com/m3/collections/guava/primitives/Ints.java` | `e379ad3971cdaadd868d3cd72044879571675c34` | `ef56b9d309acf20f2e7c51968c2d4473d73438d9` | `92d35dd8e804fb5bacf08253ba9f67cf9d84fb9b373a86bab24e78d40286200d` |
| Android `android/guava/src/com/m3/collections/guava/primitives/Ints.java` | `4b470728ffb5bf49ad92e9fbdc7b2c096f44cae2` | `2ff993e9d5240265e9a63047321064512cac7053` | `56f620016ca3867ad3dfc658b8904fce4bc91cae35c053d8c2011858d90aeb88` |

GitHub fetched each source before/after and independently confirmed **replacing only the revised `public static int indexOf(int[] array, int[] target)` method** with the parent method reproduces the original full file byte-for-byte. All other source bytes and every public source declaration are unchanged. Do not assert JRE and Android whole-file identity: the original files are different.

Both exact candidate method bodies have SHA-256 `d2b8866a7298111245831e3fd354348df5d662d424ad061538d8cbe010ed2e0a` (2,303 UTF-8 bytes). It was derived from the already-reviewed PR #35 primitive algorithm and independently matched the local Java 21 proof source. This preserves ordered null checks, first occurrence, empty, impossible-length and self-alias behavior, no input writes, and unchanged public API.

## Actually executed Java 21 verification

Compiled the **exact 2,303-byte method** and exact 429-byte original method in two test-only Java wrappers under `OpenJDK 21.0.12.1` with `javac --release 21 -proc:none -Xlint:all -Werror`: **exit 0, zero compiler diagnostics**. Only the Guava `checkNotNull` dependency was replaced in these proof wrappers by `java.util.Objects.requireNonNull`; no production Guava class or native JUnit engine was falsely claimed compiled.

- **40,593 checks PASSED** in a full seeded original-versus-candidate mixed-mode run; SHA-256 of output `f64800bb1eadc93725122ef8cee454655032b86bf5f5ac9fdddbe04a58f1dee3`.
- Bounded **6,117 checks PASS** each under mixed, `-Xint` and `-Xcomp`, all byte-identical logs; SHA-256 `39265aad26c60670fa4200c0c790cbb55e9ce00cc510dfcb453ecac56ac61f61`.
- Actual compiler diagnostics log was empty, SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
- Inputs covered seeded patterns at 0/1/2/31/32/33/128/512/1024 lengths, 4,095/4,096/16,384/32,768 haystack boundaries, repeated prefix first/second hits, nonperiodic fallback, aliasing, null order, signed extremes, 65,536-entry prefix budget and the oversized legacy path, plus input/pattern immutability.

## Measured exploratory performance — no universal speedup claim

`java -XX:-TieredCompilation -Xbatch`, identical source methods, 65,536-int haystack and 512-int pattern, three independent processes per method/workload, with warmup and seven timed batches:

| Workload | Original median | Adaptive median | Observed ratio |
| --- | ---: | ---: | ---: |
| Periodic-prefix miss | 7,612,125 ns | 181,608 ns | 41.9x lower observed latency |
| Random miss | 10,135 ns | 10,315 ns | 0.98x (similar/slightly slower) |
| Index-zero match | 9,073 ns | 9,764 ns | 0.93x (slightly slower) |

These are **method-only** measurements and not JMH/whole-fork throughput. Both original and candidate compiled classes, all actual logs, the source identity table, reproducible test harness and checksum manifest are in the conversation proof `guava_ints_adaptive_jre_android_proof_20261113.zip`, SHA-256 `1f2cb4f63116fcd696cb67e7f85a7c223606a46eca558b1ed2c96cc507c350ea`. ZIP CRC and all **26 SHA-256 manifest entries** passed. No binary was committed to this repository.

## Product and merge HOLD

Two mirrored native Guava JUnit3 six-method regression test files are authored for the JRE and Android source trees; their Git blobs are identical `5f0b7a843bf7606ef5c019906feba9ab864e218d` and both were read back from GitHub. The real Guava Maven/Bazel/JUnit, GWT/J2KT, Android build, JMH, M3JDK21 receiver, JNI, licence and published ABI are **NOT EXECUTED / HOLD**. The stacked parent PR #35 itself also remains draft and unmerged; the existing independent JRE-only `Ints` PR #34 is not modified or silently closed.

## Exact fork JUnit test-body execution — added independent stage

The exact JRE and Android `M3IntSubsequenceKmpCompletionTest.java` Git blobs were read back as **identical** (`5f0b7a843bf7606ef5c019906feba9ab864e218d`). An independent source transport was reconstructed in an approved scratch directory and `git hash-object` verified **the exact original 4,711-byte native test source**. Its six native JUnit3 method bodies were compiled with the exact candidate `Ints` method using `javac --release 21 -proc:none -Xlint:all -Werror` — exit zero, empty diagnostics. Because the full Guava/JUnit dependencies are unavailable locally, a **test-only `junit.framework.TestCase` assertion shim**, not the real JUnit engine, supplied the assertions. A deterministic local reflection runner invoked each of the **six exact test bodies** in mixed, `-Xint`, and `-Xcomp`: **6/6 PASS in each mode**, three byte-identical logs SHA-256 `7b1906f7cd453518ddeb25b0683ebddc5bc751310be4c76f089341b1c5b26060`.

The updated, verified **39-member** proof ZIP `guava_ints_adaptive_jre_android_final_20261113.zip` has SHA-256 `37a25ac49eddfed4290347f7e6454cbfd8e9d4b6a886ae14975e94a2ac85f577`; all 38 included source/class/log hashes and ZIP CRC passed. It preserves both the original 40,593-check/three-mode method proof and the new exact six-test-body proof, with all local shims explicitly labeled. This archive is a conversation artifact, not an imported binary.

**Still HOLD:** actual JRE/Android `mvn test`/Bazel/JUnit engine and complete Guava production dependency closure, GWT/J2KT, M3JDK21 integration and runtime JMH. A test-only assertion shim does not grant a native JUnit pass or release qualification.

**Disposition:** `DRAFT / TWO_SOURCE_INTS_OPTIMIZATION / 16_CLASS_PRIMITIVE_FAMILY_STACK / NO_PRODUCT_PROMOTION`.

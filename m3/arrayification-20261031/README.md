<!-- SPDX-License-Identifier: Apache-2.0 -->
# M3 array-operation source candidate (2026-10-31)

This is a source-only candidate, not a released library or a claim that every collection has array-backed storage. It is a **materialization of the pre/postimages in [canonical Synexia draft PR #14952](https://github.com/hsoliwal/com.synexia/pull/14952)**, crate `synexia-openrewrite-recipes/crates/m3-bulk-arrayification-20261031/`. The reuse/recipe engine is owned by Synexia; no fork-local mutation engine is created.

## Modified owners

- `guava/src/com/m3/collections/guava/collect/TopKSelector.java`: Avoid partition self-swap (JRE). Original blob: `0df9010c2148df2166e77f3066e0f1c62fb53768`.
- `android/guava/src/com/m3/collections/guava/collect/TopKSelector.java`: Avoid partition self-swap (Android). Original blob: `5058b3072e7fb888bfeccdcd20a9f2978cec7a1c`.

Everything outside the exact selected method atoms remains unchanged; all provider API package declarations, simple names, public fields, methods and descriptors remain unchanged. These edits use existing Java arrays only and do **not** establish JNI array allocation or arrayification of other classes. No copied challenge solutions. Existing donor licenses/NOTICE and prior history are untouched.

## Admission

- **Completed:** isolated Java 21 arithmetic exercise checked 100,000 ring-index equivalences, 256 append equivalences and 262,144 swaps (refer to canonical PR). It is not a provider binary/source compilation.
- **Pending:** JRE+Android product tests and allocation/timing + comparator transcript.
- **Pending:** exact recipe replay, fixed point, API binary descriptors, native JNI backing-array integration and collection superset tests.
- **Held:** speed, allocation, packaging, rollout and merge acceptance. Measurements are not asserted.

The historical local `.m3-relocation/rewrite.yml` is superseded; do not apply it to this already-relocated source. Continue serial per-owner atomization/patternization and contract-preserving recipe improvements. In JDK-received classes add the `M3` prefix only to new first-party owner types; keep received donor class simple names.

## Second-pass differential oracles and CI (2026-11-01)

- New source-sealed test: `android/guava-tests/test/com/m3/collections/guava/collect/M3TopKSelectorArrayPassTest.java`
- New source-sealed test: `guava-tests/test/com/m3/collections/guava/collect/M3TopKSelectorArrayPassTest.java`

The canonical source and expected SHA-256 postimages live in [Synexia draft PR #14952](https://github.com/hsoliwal/com.synexia/pull/14952), now expanded to eight target files (4 production, 4 additive test targets). The focused pull-request workflow is `.github/workflows/m3-arrayification-topk-20261101.yml`, and its executable scoped invocation is:

```bash
mvn -B -ntp -f pom.xml -P m3-relocated-verification -pl guava-tests -am -Dtest=M3TopKSelectorArrayPassTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Android uses -f android/pom.xml; relocated failureaccess dependency closure may still block both builds. A passing focused oracle would prove this slice only. **No full-fork/JNI/benchmark gate is waived, and no merge is authorized.**

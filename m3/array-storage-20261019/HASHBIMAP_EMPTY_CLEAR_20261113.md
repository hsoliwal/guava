<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava HashBiMap: skip two empty bucket-array scans

Source: `hsoliwal/guava@06387d46d087bf79032b51afbd814ad467fffda0`, `guava/src/com/m3/collections/guava/collect/HashBiMap.java`, original complete Git blob `2b06ea1c5bed2ca8a740576c13252fcc452a081b`. This is a third production class in the direct-fork compact bucket-clear family, not M3JDK.

## Frozen semantics

The bimap owns two primitive `int[]` bucket heads, inverse K/V indexes and `int[]` insertion-order links. On every valid last removal, `deleteFromTableKToV` and `deleteFromTableVToK` reset their last bucket head to ABSENT, and the insertion-order endpoints return to ENDPOINT. On `size==0` with `floodedTables==null`, all bucket heads are already ABSENT, so a repeated `clear()` can preserve `modCount++` but skip re-clearing both full bucket arrays. Gate on at least 1024 buckets so small maps retain the original path. Preserve the original full clear on any nonempty or flooded map, including both flood-resistant maps and all array/value/entry lifecycle operations. Do not add a new retained collection/index or change any public API/inverse serialization/modCount/JNI surface.

No user-exposed mutation of private bucket arrays is part of the supported contract. Confirm through real `HashBiMap` JUnit tests: large empty repeated clears, put/get/forcePut and inverse behavior, iterator epoch, and flooded fallback including empty flooded state.

The standalone Java21 exact-array model must compile/lint, compare both bucket head lanes and the unchanged mutation epoch, then benchmark only the qualified empty case. Full Guava JRE/Android/Bazel test suite, JMH, ABI and serialization remain pending; no universal throughput claims from a synthetic model. Default branch unchanged until independent qualification.

## Actual reviewed-source and replay receipts

- Exact original Git blob `2b06ea1c5bed2ca8a740576c13252fcc452a081b`, UTF-8 SHA-256 `218d47276fa12f7bb01a315b8a85ce80bbcdc796f201c172e04e88a98e119db4`.
- Exact candidate Git blob `dbfbb45c5dd498e378c57540dbcbf7344a28f61d`, UTF-8 SHA-256 `4877879dbda68564b2972c4ec8f8854747aa12635cd51e63338d4c7964a47f41`.
- Full-file readback verifies the only production edit is the one guarded early return at the start of `clear()`. All other bytes unchanged. The three test methods in `HashBiMapEmptyClearTest.java` are committed to the fork.
- Local Java 21 standalone dual-index array model: strict `javac -Xlint:all -Werror` pass, **18,000** state comparisons in JIT and **18,000** in `-Xint`; full key/value head arrays, index/link arrays, size and modCount all match. Five warm empty-clear samples at 8,192/65,536/262,144 buckets improved substantially, but are **synthetic only**, not the actual whole bimap. Full Guava JRE/Android/serialization/JMH remains NOT_RUN.

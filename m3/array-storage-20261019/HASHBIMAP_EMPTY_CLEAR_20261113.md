<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava HashBiMap: skip two empty bucket-array scans

Source: `hsoliwal/guava@06387d46d087bf79032b51afbd814ad467fffda0`, `guava/src/com/m3/collections/guava/collect/HashBiMap.java`, original complete Git blob `2b06ea1c5bed2ca8a740576c13252fcc452a081b`. This is a third production class in the direct-fork compact bucket-clear family, not M3JDK.

## Frozen semantics

The bimap owns two primitive `int[]` bucket heads, inverse K/V indexes and `int[]` insertion-order links. On every valid last removal, `deleteFromTableKToV` and `deleteFromTableVToK` reset their last bucket head to ABSENT, and the insertion-order endpoints return to ENDPOINT. On `size==0` with `floodedTables==null`, all bucket heads are already ABSENT, so a repeated `clear()` can preserve `modCount++` but skip re-clearing both full bucket arrays. Gate on at least 1024 buckets so small maps retain the original path. Preserve the original full clear on any nonempty or flooded map, including both flood-resistant maps and all array/value/entry lifecycle operations. Do not add a new retained collection/index or change any public API/inverse serialization/modCount/JNI surface.

No user-exposed mutation of private bucket arrays is part of the supported contract. Confirm through real `HashBiMap` JUnit tests: large empty repeated clears, put/get/forcePut and inverse behavior, iterator epoch, and flooded fallback including empty flooded state.

The standalone Java21 exact-array model must compile/lint, compare both bucket head lanes and the unchanged mutation epoch, then benchmark only the qualified empty case. Full Guava JRE/Android/Bazel test suite, JMH, ABI and serialization remain pending; no universal throughput claims from a synthetic model. Default branch unchanged until independent qualification.

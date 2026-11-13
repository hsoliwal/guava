<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava CompactHashMap: skip vacant bucket-array scans on repeated clear

Direct source target `hsoliwal/guava@06387d46d087bf79032b51afbd814ad467fffda0`, `guava/src/com/m3/collections/guava/collect/CompactHashMap.java`, preimage Git blob `7b2bf7deddf92630a908007cdbb3d2f8a5957598`. This is an exact source fork optimization, NOT an M3JDK adapter.

## Array and lifecycle contract
The existing `CompactHashMap.clear()` increments its modification count and calls `CompactHashing.tableClear()` over the entire retained bucket array even when the internal size is already zero. `CompactHashing.remove()` resets the last chain/bucket head to `UNSET`; a valid empty open-addressed map has zero occupied bucket slots. For exact runtime class `CompactHashMap` with `size==0`, no delegate, preallocated table and mask >=512 (>=1024 buckets), preserve `incrementModCount` and skip redundant bucket fill. Preserve the existing complete `clear()` for all nonempty, delegate-backed, subclass and small-table paths.

One existing `byte[]`/`short[]`/`int[]` table remains authoritative; no node/retained metadata, additional index, public/protected contract or JNI changes. The existing lazy-unallocated path stays unchanged. All subclass `clear()` and flooding fallback behavior stay original. Do not assume corrupted private bucket arrays are a supported external state.

## Executed local model
OpenJDK21 `javac -Xlint:all -Werror` passed. The exact bucket-array clear model compared original versus guarded implementation for 14 table capacities (1..1,048,576), 250 repeated clears each, including byte/short/int tables, matching `modCount` and complete table contents: **385,525,750 byte/slot checks passed**. Eight timed repeats each at mask sizes 512/65,536/262,144 found large-capacity scanned-array removal costs; at 65,536 capacity candidate/baseline ratios ~0.0047..0.015, at 262,144 ~0.00016..0.00018. Small 512-slot ratios were variable. These are model measurements, not full Guava JRE/Android/JMH, and the full fork suites remain **NOT_RUN**.

## Acceptance
Only one source method and new actual Guava JUnit3 regression test may change on the review branch. Verify empty-map bucket invariants after allocation and final deletion, future put/get/remove, delegate reset and subclass behavior, retained serialization and iterator fail-fast. Full Java/Android/CI build and API verification required prior to merge; exact Git source SHA/UTF-8 SHA256 source records must be updated after code. No default-branch write or integration into M3JDK.

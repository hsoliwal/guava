<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava CompactHashSet: inherited compact-bucket array empty clear

**Direct fork owner:** `hsoliwal/guava@06387d46d087bf79032b51afbd814ad467fffda0`, `guava/src/com/m3/collections/guava/collect/CompactHashSet.java`, exact baseline Git blob `a8d993d0524ec38ea71ded0c868c0ed72a68ac42`.

The existing compact set uses the same `CompactHashing.remove()` and `tableClear()` primitive bucket-array family as CompactHashMap. Last legal removal unlinks the bucket head to `UNSET`, so a valid already-empty, allocated table contains no bucket entries. Repeated `clear()` currently increments `modCount` and zero-fills the whole table. For **exact runtime class** `CompactHashSet`, size 0, table allocated, no delegate and hash table mask >=512, preserve the modification epoch but skip the redundant array scan. All nonempty, small, lazy-unallocated, delegated and subclass cases retain the original clear path. `CompactLinkedHashSet.clear` retains its own link-reset and inherited slow-array behavior.

No new storage, iterator, dependency or public/protected signature. Full tests must compare future insertion/removal, zero element and null element, delegated fallback, and CompactLinkedHashSet iteration behavior. Strict standalone Java21 array-header/bucket metadata model shared with the map owner passed **385,525,750** state checks, but it does **not** substitute for compiling the real source. A fork-owned JUnit3 set test is required and must run before merge. All full Guava tests, performance profiling and CI remain NOT_RUN in this session. This is an additional family member in the existing Guava draft PR, not a second map abstraction.

## Exact received branch source receipt

- Preimage Git blob `a8d993d0524ec38ea71ded0c868c0ed72a68ac42`, SHA-256 `51ddaf40961bbcbe2a117210861e9085b6afdb65ea90d0c5082a1cd50f337899`.
- Candidate Git blob `77995aab7f0f1c47c5e0859139c60d070610adbd`, SHA-256 `cc8b864cdc7af6c6f1a3f90b84360ccad822ebb8f0a660ea175d62a9ef138b70`.
- New fork-owned JUnit3 `CompactHashSetEmptyClearTest.java`, 3 methods, Git blob `000a88e438aa65beae2d2c196f3a6db0994744d8`. Source committed; full Guava JRE/Android test run **NOT_RUN**.

# Primitive collision indexes for immutable maps

This packet consumes predecessor 3aea962167e102158aaf6ef39ce3c2c17e8a9f15 and changes exactly the existing RegularImmutableMap and RegularImmutableBiMap production owners. It complements the original JRE collect census at ../array-storage-20261019/storage-coverage.tsv and the count successor at ../immutable-count-arrays-20261019/coverage-delta.tsv.

## Representation and behavioral atoms

| Owner | Old topology | New topology | Preserved payload and behavior |
| --- | --- | --- | --- |
| RegularImmutableMap | ImmutableMapEntry[] bucket heads and NonTerminalImmutableMapEntry object links | int[] bucket heads and int[] nextInKeyBucket indices | The original Entry[] payload, key/value identities, ordering, views, null guards and JDK flood fallback |
| RegularImmutableBiMap | Separate key/value ImmutableMapEntry[] tables and one NonTerminalImmutableBiMapEntry per colliding mapping | Two int[] head tables plus int[] key/value next-slot columns | Entry[] payload, cached hash, forward/inverse order, inverse identity and serialized form |
| Shared admission and lookup | Pointer traversal in checkNoConflictInKeyBucket/get | Primitive index traversal in the same existing helper owner | Existing equality directions, duplicate messages and MAX_HASH_BUCKET_LENGTH guard |
| Duplicate compaction | Object links remain valid after entry-array compaction | Rebuild indexes after every compaction | First iteration position with final value/key object, repeated builder use and later buildOrThrow failures |

createIndexArray initializes a primitive column to the common absent sentinel. Map and bimap both use it. The existing shared checkNoConflictInKeyBucket preserves duplicate rejection or returns the matching payload index for buildKeepingLast. Bimap value conflict checking follows its own value column. No general-purpose parallel map, collection facade or relocated namespace is added.

This is deliberately reported as topology conversion. Ordinary immutable Entry payload objects remain because two unchanged original tests require identity of the result's Entry[] with the builder's Entry[]. No compatibility shadow array is introduced. Normal map/bimap construction reuses terminal ImmutableMapEntry payloads even when buckets collide; it no longer allocates or retains NonTerminal collision nodes. Legacy NonTerminal classes remain byte-for-byte unchanged for old standalone entry deserialization. JDK-backed flooding owners remain explicit storage exceptions.

Primitive topology is an ownership change, not a measured memory or speed improvement. Its footprint can vary with collision distribution, padding and JVM reference width. No throughput, latency, universal worst-case or memory-percentage claim is made.

## Frozen validation scope

The unmodified original baseline passed 101,107 cases: the earlier 76,003-case closure plus 12,549 ImmutableMap cases, 12,551 ImmutableBiMap cases and four original hash-flooding construction/query cases. The finite pom compiles actual JRE Guava and its unchanged testlib using source/target8 on JDK21, with the repository's declared test dependencies. It does not claim the native JDK27/ErrorProne reactor.

M3ImmutableIndexArrayContractTest adds six checks. It verifies primitive fields plus exact payload and individual entry identity; duplicate compaction under unchanged/changing capacities; 180 builder histories of48 insertions with repeated duplicate-policy and comparator builds, saved result snapshots and key identity; 700 bimap constructions including dual collision directions, conflicts and inverse lookup; original/candidate serialization of maps, views, inverses and standalone entries in both directions; and original source-entry getter behavior plus detached snapshots for regular and flooded copies. The unchanged singleton bimap returns fresh inverse objects, so the differential test compares that original owner behavior instead of imposing regular-bimap identity on the singleton.

All original test sources in the compiled closure are hash-pinned in frozen-original-test-oracles.json. The earlier16 contract cases are frozen in predecessor-contract-oracles.json. Actual production writes require the separate canonical com.synexia SDK results, sealing tests, receiver refusal cases and a zero-change receiver fixed point. Final exact target, source hash and runtime results accompany the publishing receipt.

```sh
mvn -f m3/immutable-index-arrays-20261019/pom.xml \
  -Dm3.baseline.classes=/path/to/original-baseline/classes test
```

To compile and run the original oracle first, supply -Dm3.source.root=/path/to/original-checkout and a separate -Dm3.proof.output. Original tests remain unchanged; the original checkout lacks the subsequently added contract files. The candidate uses that separate original class output for deterministic comparisons.

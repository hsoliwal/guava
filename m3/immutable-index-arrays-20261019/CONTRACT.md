# Immutable map index arrays: contract before implementation

Repository hsoliwal/guava, predecessor 3aea962167e102158aaf6ef39ce3c2c17e8a9f15. Pinned original JRE and Android donor provenance: 120424a121f1cbda05d2fe0f041779b2344d8ccb. Preserve com.m3.collections.guava identities, original author notices and Apache-2.0 license.

## Physical scope

Convert the existing RegularImmutableMap and RegularImmutableBiMap hash topology: object bucket-head arrays and NonTerminalImmutableMapEntry/NonTerminalImmutableBiMapEntry links become int[] bucket heads and int[] next-slot columns over the existing insertion-ordered Entry[] payload. Reuse the existing array-index bucket pattern from HashBiMap/ObjectCountHashMap, with one shared key-conflict and lookup implementation in RegularImmutableMap. No new public or parallel collection is introduced.

This is a partial storage conversion. Original ImmutableMapTest.testBuilderExactlySizedReusesArray and ImmutableBiMapTest.testBuilderExactlySizedReusesArray directly require the result to retain the identical Entry[] allocated by the builder. Keep that identity and the ordinary immutable entry payload. Never claim these payloads fully flattened. Normal runtime topology must retain no NonTerminal entry nodes. Existing legacy node classes remain readable for standalone entry serialization; the owners no longer instantiate them. Primitive topology can change footprint in either direction depending on collisions and JVM reference size; no footprint or throughput improvement claim is made.

## Mandatory behavior

Preserve all original tests without edits. Keep builder reuse, snapshot independence, orderEntriesByValue, duplicate rejection and buildKeepingLast semantics (first position, final value, including repeated builds and exceptions after keeping last). Rebuild all primitive bucket indexes after duplicate slot compaction, even when table capacity stays equal. Preserve null guards, key/value equality directions, collision length threshold, cached bimap hash, immutable entry/value/key views and spliterator behavior. Keep existing JdkBackedImmutableMap/BiMap flooding dispatch. Retain regular bimap inverse identity and InverseSerializedForm, plus all existing map/key/value/entry proxies.

## Proof gates

Run the unchanged ImmutableMapTest, ImmutableBiMapTest and their original flooding tests together with prior mutable, multimap and immutable multiset closures. Add discriminators for primitive collision fields, ordinary immutable entry payload reuse, duplicate compaction with unchanged and changed capacities, deterministic original-versus-candidate builder snapshots/exceptions, inverse lookups/flooding boundaries, and original/candidate serialization in both directions. Freeze original and predecessor contract source hashes. Apply source changes only from actual canonical com.synexia OpenRewrite SDK results after input sealing and receiver refusal tests, then require a zero-change receiver fixed point.

# Immutable count array storage: contract before implementation

Repository: hsoliwal/guava. Original donor commit: 120424a121f1cbda05d2fe0f041779b2344d8ccb. This successor requires the qualified guava-arrays packet; predecessor commit 6dcb62c653c3c0ac11011a79dff97d395c1a751a is sealed into the recipe dataset. Existing com.m3.collections.guava names and Apache-2.0 notices remain.

## Physical ownership

RegularImmutableMultiset currently retains one ImmutableEntry/NonTerminalEntry object per distinct element, with object collision links. Reuse the pinned Android RegularImmutableMultiset ObjectCountHashMap representation: Object[] element payload, int[] count payload and hash table, long[] packed hash/next slot metadata. Entries returned to callers are immutable values materialized on access, never retained structural nodes. Keep the original cached hash code, saturated size, element order, null rejection, immutable views and serialized proxy.

ObjectCountHashMap already owns the primitive columns. Adapt the observed CompactHashMap collision transition (maximum chain length 9) so its normal path stays arrays and its overflow path uses a JDK HashMap from key to dense slot index. Counts and order remain in primitive arrays in both modes. This makes existing immutable-multiset copy, builder and collector construction retain their original collision-resistance contract after the earlier mutable donor adoption. JDK nodes and boxed index values remain only in the explicit flooding fallback; no claim that this path is fully flattened.

## Mandatory behavior

Preserve linked multiset encounter order, moved-last slot repair, mutable count entries, iterator modification epochs and own-iterator removal; zero/negative/overflow count rules; absent and null queries; integer size saturation; serialization compatibility with the unchanged original classes. Preserve the existing JdkBackedImmutableMultiset result fallback after collision overflow, rather than silently dropping its query-complexity behavior. Builder reuse and immutable result independence must hold across removal and reinsertion. Do not change original test sources or mask failing tests.

## Scope and exclusions

Two production owners: ObjectCountHashMap and RegularImmutableMultiset. No public collection API or namespace changes. ImmutableMap and ImmutableBiMap full flattening is deferred because frozen original tests explicitly require identity of the retained Entry[] and builder Entry[] (ImmutableMapTest.testBuilderExactlySizedReusesArray and ImmutableBiMapTest.testBuilderExactlySizedReusesArray). This packet does not retain redundant entry arrays to claim those owners flattened.

## Qualification

Run the unchanged original mutable multiset, immutable multiset, immutable sorted multiset, and ImmutableMultisetFloodingTest suites, with their unchanged helper closure. Add physical owner checks, collision transition/slot compaction/live-entry tests, deterministic mutation comparisons against the unchanged original owner, and original/candidate serialization. The production candidate is admitted only through actual com.synexia OpenRewrite SDK results, receiver refusal cases and a zero-change receiver fixed point. No throughput, latency or footprint percentage claim is made.

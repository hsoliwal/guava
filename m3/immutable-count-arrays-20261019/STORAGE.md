# Immutable count arrays and collision closure

This successor consumes guava-arrays at commit 6dcb62c653c3c0ac11011a79dff97d395c1a751a. Its two production owners are the existing ObjectCountHashMap and RegularImmutableMultiset. No public type is added or relocated. The complete earlier JRE collect census remains at ../array-storage-20261019/storage-coverage.tsv; coverage-delta.tsv records exactly what this successor qualifies.

## Owner and behavior atoms

| Owner | Retained representation | Behavioral atom and pattern role |
| --- | --- | --- |
| RegularImmutableMultiset | One ObjectCountHashMap containing Object[] elements, int[] counts/table and long[] bucket metadata; scalar saturated size/cached hash | create reads each source entry once; elementSet projects indexed keys; getEntry materializes an immutable caller value; the serialized proxy remains the JRE proxy |
| ObjectCountHashMap normal mode | Existing dense payload and primitive bucket/slot columns | put performs counted bucket admission; moveLastEntry repairs dense slot ownership; linked owner continues to repair its long[] order links |
| ObjectCountHashMap overflow mode | Same primitive count/order payload, with JDK HashMap<K,Integer> key-to-slot index | createFloodingIndex performs one collision transition; putInFloodingIndex appends or replaces one primitive count; remove and moveLastEntry repair the moved slot's index; clear retires the fallback |
| JdkBackedImmutableMultiset | Existing JDK delegate and immutable entry list | Explicit unchanged immutable result fallback; create receives snapshots of already-read primitive columns |

The collision boundary is adapted from the pinned CompactHashMap implementation (nine existing entries in a bucket before inserting another distinct key). JDK nodes and boxed slot indexes are an explicit overflow exception to normal physical flattening. Count values remain primitive even in the mutable fallback. This successor supersedes the earlier primitive count donor's disclosed absence of a collision fallback. It makes no universal worst-case claim for arbitrary invalid or non-comparable key implementations; the unchanged original adversarial Comparable-key operation-count contract is the qualification gate.

## Contract and donor decisions

Reuse the existing Android immutable-count representation and existing JRE flood strategy as a closure. Keep the JRE cached hash code and saturated size. Keep JRE ImmutableMultiset.SerializedForm and its entry/element view behavior; do not import Android's different SerializedForm. Returned immutable entry values are not retained per-element storage. Counts and keys are copied into the result; builder reuse cannot mutate earlier results. The JDK result fallback consumes a transient indexed entry projection so that source Entry getters are not read a second time.

Original full immutable map/bimap flattening is outside this packet because unchanged tests directly assert the old retained Entry[] identity with the builder. This is a concrete representation-oracle conflict, not a claim that array conversion is impossible. There is no new parallel map implementation and no redundant compatibility entry array.

## Frozen proof

The expanded unchanged original baseline passed 76,003 cases: 50,273 earlier collection cases, 4,755 ImmutableMultiset cases, 20,973 ImmutableSortedMultiset cases and both ImmutableMultisetFloodingTest construction/query cases. The latter compares actual hashCode/equals/compareTo operation growth at 1,024 and 32,768 colliding Comparable keys. Original tests are unchanged and hash-pinned with their compiled source closure. The earlier ten added array contracts are also frozen as predecessor oracles.

M3ImmutableCountArrayContractTest adds six checks: physical retained storage; transition/iterator/live-entry behavior; 5,000 flooded ordered-slot mutations against a JDK map; 3,000 immutable builder operations against isolated original classes including overflow and saved snapshots; immutable JDK fallback plus single reads of source entries; and original/candidate serialization in both directions for ordinary and colliding keys including views.

The pom.xml compiles actual JRE source and unchanged testlib with the source owner's Java 8 source/target settings on Java 21. It declares the original Mockito 4.11.0 dependency needed by ImmutableSortedMultisetTest. It is a finite collection proof, not the native JDK 27/ErrorProne reactor. Actual source admission still requires the separate com.synexia SDK recipe, sealed inputs, receiver refusal cases and a zero-change receiver fixed point. Final SDK and runtime evidence is recorded with the exact result manifest by the publishing task.

Example runtime command after the recipe receiver has applied the packet (provide the original baseline's separately compiled classes):

```sh
mvn -f m3/immutable-count-arrays-20261019/pom.xml \
  -Dm3.baseline.classes=/path/to/original-baseline/classes test
```

The same pom.xml can first compile and run the original baseline with -Dm3.source.root=/path/to/original-checkout and a separate -Dm3.proof.output. Original baseline source lacks the two added test files; the candidate runs both frozen predecessor and new contract tests.

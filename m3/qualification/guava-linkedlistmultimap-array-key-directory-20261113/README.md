<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava LinkedListMultimap array-owned key-directory receiving evidence

This PR changes **the actual Guava JRE and Android fork implementations**,
not M3JDK21, an adapter, a recipe crate or an unrelated source catalogue.
The two existing `LinkedListMultimap` classes retain their externally visible
API and node-based global/sibling value chains while their **distinct-key
HashMap directory** is replaced by a reusable, GWT-compatible, package-private
`ArrayKeyDirectory` owner.

The new key directory retains aligned `Object[] keys`, `Object[] values`,
`int[] hashes`, `int[] freeNext` and `int[] index` arrays. It uses open
addressing with tombstones, recycled physical slots and a primitive rehash.
No per-distinct-key `HashMap.Entry` object exists on the ordinary path.
A bounded hash-flooding fallback is enforced even when tombstones are reusable, using the original JDK hash-map semantics,
rather than imposing unbounded linear probing in adversarial inputs.

**This is a structural key-directory arrayification, not yet a replacement
for the retained `Node` objects in the global and per-key value chains.**
Those are a distinct, still-pending contract-preserving arrayification phase.
In particular, public mutable `Map.Entry` escape behavior and iterator
modification epochs are untouched in this slice.

## Exact preimages

- Fork base `hsoliwal/guava@8b7e019c672e7600f7aceecbb9e290bf00728bd8`.
- JRE `guava/src/com/m3/collections/guava/collect/LinkedListMultimap.java`
  blob `4ac7e84b4b36d835ee7f8fad8a0fc2a020cf4fc1`.
- Android `android/guava/src/com/m3/collections/guava/collect/LinkedListMultimap.java`
  blob `124cdee1a436298c32901ac198f13871ef25af8c`.

## Source and verification

- Add the same internal array owner in JRE and Android source trees
  (Git blob `b502bfc3a0aa39033a2b6295b104441400a562c2`).
- JRE receiver Git blob `c3a43bcb00dd1cbddf1923057a9f1d8cc3e8b00c`.
- Android receiver Git blob `54a81d72e6a2c4ee6cbbd757e16d7502d3c2d5f8`.
- Two identical JUnit3 regression sources cover structural slots,
  collisions and fallback, null keys and values, global order,
  entry mutability, serialization, a seeded multimap view/reference
  model and negative expected capacities.
- Test-only JSpecify `@Nullable` syntax stub under `proof/` supports
  isolated strict compilation; it is **not** compiled into Guava libraries.
- An independent locally implemented Java 8-compatible directory model,
  compared against `java.util.HashMap`, passed **2,576,677 assertions**
  across 24 seeds x 50,000 mixed operations. The model uses the same
  key-hash, tombstone, slot recycling, growth, and fallback algorithms.
  The exact checked-in JRE/Android Java sources are scheduled for
  strict `--release 8 -Xlint:all,-options -Werror` compilation and
  model execution by the focused workflow; **do not claim hosted CI PASS**
  until it actually executes.

## Narrow measurement

A local SerialGC retained-heap preflight with 100,000 distinct integer
keys and a single shared value reference measured ~2,984,952 bytes for a
new `HashMap` versus ~1,386,128 bytes for the independent array owner
model, repeated twice. This is a **scoped directory-only** measurement,
not full `LinkedListMultimap` retained heap, throughput/JMH or Android
memory evidence.

## HOLD

The complete Guava JRE/Android Maven reactor, provider suites and GWT/J2KT
toolchains have not been executed in the local offline environment. The
new source is a reviewable fork optimization candidate. Only after actual
fork-native compiler/behavior/ABI/serialization/heap checks and canonical
Synexia Maven/OpenRewrite execution may independently qualified outputs
be received into integrated `hsoliwal/M3jdk21` with JDK formatting, naming
and installed-image/JNI/JTReg gates. No M3JDK21 product edit is claimed.

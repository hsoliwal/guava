<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava array storage convergence contract

Baseline: `hsoliwal/guava@120424a121f1cbda05d2fe0f041779b2344d8ccb`.
Canonical transformation owner: `hsoliwal/com.synexia`, existing
`synexia-openrewrite-recipes/recipe-crates/m3-collections-contract-refinement-20261017`.
Dataset: `guava-arrays`. Candidate inputs are not product source until actual
OpenRewrite SDK Results pass qualification and are received serially.

## Source and license

Reuse the same pinned repository's Android collection implementations, retaining
their complete Apache-2.0 headers and authorship. Preserve the already relocated
`com.m3.collections.guava` namespace. No challenge-site implementation is copied.
The existing JRE implementations remain the immutable behavioral and serialization
oracle; their tests are not changed to accommodate the candidates.

## Physical ownership and admitted transformations

1. Adapt the existing Android `Platform` factory selections to JRE. Ordinary map
   and set entries use existing compact object arrays and integer hash/link masks.
   Keep the JRE concurrent-set factory and platform emulation boundaries.
2. Adapt Android `HashBiMap`'s key/value arrays, bidirectional integer bucket links,
   and integer insertion-order links. Preserve the JRE public superclass,
   public method declarations, inverse serialized proxy, serial version, and
   clear-before-rebuild `replaceAll` behavior. Public entries remain API objects
   materialized during access, rather than retained nodes for every mapping.
3. Adapt the existing Android object/count array family for JRE mutable
   multisets. Preserve the JRE subclass-owned serialized envelope, superclass
   serial version, public entry views and occurrence semantics. A direct Android
   class replacement is not admitted because its serialization ownership differs.

## Contract locks

Retain public names and callable signatures, collection and inverse relations,
null admission, duplicate multiplicity rules, live backed views, insertion order
where promised, iterator removal, entry mutation, overflow and capacity guards,
serialization in both original/candidate directions, and retained donor licenses.
Entry behavior after unsupported external mutation is not promoted into a new
contract. Do not change public `Maps.newHashMap`/`newLinkedHashMap` concrete return
types or replace the original classes with parallel M3 facade collections.

## Qualification

Use diff review, source checks, Java compilation, and unchanged relevant Guava JRE
tests in that order. Add independent physical-storage discriminators and seeded
behavioral/serialization comparisons. Replay the canonical recipe to a fixed
point and reject unknown source hashes. Runtime/compiler results are separate
from recipe syntax and replay qualification.

## Explicit remaining storage boundaries

The existing compact maps and sets retain their collision-flooding JDK fallback.
The reused Android object/count owner uses primitive index chains and has no
tree-bin fallback. It preserves functional count contracts but does not preserve
the former JDK HashMap's adversarial-collision tree-bin complexity. No speed,
latency, memory-size or security improvement is inferred from functional tests.
`LinkedListMultimap.Node`, `LinkedHashMultimap.ValueEntry`, `TreeMultiset.AvlNode`,
reference-aware/concurrent maps and other unconverted families remain visible in
the storage ledger. Factory conversion lowers their key indexes where applicable;
it does not erase their remaining value/order/tree nodes. Native JNI, GWT,
Android-wide builds, the complete JRE reactor and measured throughput require
their own evidence. No all-fork completion or performance claim is made here.

## Donor defects distinguished from the JRE contract

The seeded original-versus-array comparison found two insertion-order relocation
defects in the pinned Android `HashBiMap` algorithms. Both are repaired in this
JRE adaptation: a predecessor that occupied the moved last slot is remapped after
`putInverse` deletes an existing key, and `replaceKeyInEntry` normalizes moved
predecessor/successor indexes before excluding the replaced entry. The second
repair prevents a self-link after merging adjacent mappings. Focused regressions
accompany the unchanged 12,000-operation collision/null/view tape.

The Android source remains the immutable donor input in this packet. The donor
defects are not hidden by modifying either the donor or the original JRE tests.

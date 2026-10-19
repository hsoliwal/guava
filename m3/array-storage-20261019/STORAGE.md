<!-- SPDX-License-Identifier: Apache-2.0 -->
# Storage owners and recipe boundaries

This packet changes existing collection storage under `com.m3.collections.guava`.
It introduces no parallel public collection classes. Its physical change is to
retain array slots and primitive links/counts where the JRE owner previously
retained one structural object per association or distinct element.

## Reused semantic atoms

| Existing owner | Reused atoms and pattern role | Retained state after the change |
| --- | --- | --- |
| `HashBiMap` | `findEntry`, `bucket`, bucket insertion/deletion, `setSucceeds`, `moveEntryToIndex`, and replacement/removal atoms from the pinned Android implementation | Two object columns and primitive bidirectional hash/insertion-order columns |
| `HashBiMap.View` | One iterator skeleton with `forEntry` projections for keys, values and both entry directions | Iterator cursor/index state; API entries are created when accessed |
| `HashBiMap.EntryMutationListener` | Acknowledges an entry's own replacement to its originating iterator | One reference in an exposed entry; no per-mapping listener retained by the map |
| `ObjectCountHashMap` | Packed hash/next masks, `indexOf`, `insertEntry`, `moveLastEntry`, capacity and table-resize atoms | Object keys, primitive counts, integer buckets and packed long entry metadata |
| `ObjectCountLinkedHashMap` | Packed predecessor/successor masks, `setSucceeds`, movement and iterator-index repair | One primitive long order column per capacity slot |
| `AbstractMapBasedMultiset` | Indexed iteration plus occurrence-count projection; count edits preserve the original nonstructural meaning | One array-count owner and a long total size |
| `Platform` | Existing internal factory boundary chooses existing compact implementations | Compact array owners in ordinary hash map/set paths |
| JRE serialization methods | Original outer/subclass serial owners and inverse proxy adapt the storage implementation | The original serialized data envelope and serial identifiers |

`replaceAll` copies the active columns temporarily before clearing and rebuilding,
preserving the JRE callback-visible empty/partial map behavior. These temporary
arrays are not permanent per-entry nodes. Entry objects remain valid API objects;
behavior after unsupported external modification is not given a new guarantee.

## Complete JRE collect source census

`storage-coverage.tsv` contains every one of the 216 baseline Java source files in
`guava/src/com/m3/collections/guava/collect`, plus the two relocated Android count
owners admitted here. Each of the 218 rows records exact baseline/candidate hashes,
its storage classification and the remaining contract. `storage-coverage.json`
records the scope, counts and limitations. There is no omitted-source success
bucket: interfaces, skeletal iterators and views are explicitly recorded as having
no independent per-element structural owner. Singleton, empty and implicit range
representations have their own scalar/implicit classification.

The census is a source-owner inventory. It is not an allocation measurement, and
an array of entry-object references does not qualify as removal of those retained
entry objects. A `contract-blocked` row means this packet does not yet contain a
qualified replacement for that dependency family. It does not mean the owner can
never be converted.

| Remaining family | Exact intrinsic owner or component | Required conversion closure |
| --- | --- | --- |
| Linked multimaps | `LinkedListMultimap.Node` and `KeyList`; `LinkedHashMultimap.ValueEntry`, `ValueSet`, global iteration chain | Global and per-key order, duplicate multiplicity, mutable list entries or immutable snapshots, iterator mutations and serialized order |
| Sorted multiset | `TreeMultiset.AvlNode`, shared root and range views | Primitive child/order links, heights, long subtree totals, rotations and comparator/range view aliasing |
| Sorted maps, sets and tables | `TreeMultimap`, `TreeBasedTable`, `TreeRangeMap`, `TreeRangeSet`; `ImmutableSortedMultiset` build-time `TreeMultiset` | Comparator equivalence, navigable views, range splitting/coalescing, shared ownership and serialization |
| Immutable hash maps/bimaps | `RegularImmutableMap`, `RegularImmutableBiMap`, `ImmutableMapEntry`; `ImmutableMap`/`ImmutableBiMap` builders | Android alternating-array owner must be qualified together with JRE duplicate handling, builder entry reuse, serial forms and flooding fallback |
| Immutable hash multiset | `RegularImmutableMultiset` and its nonterminal count entries | Array-count donor closure with immutable entry views, saturation, serialization and flooding behavior |
| Tables with existing cell arrays | `ArrayTable` and `DenseImmutableTable` key-to-index maps | Values already occupy arrays; retained immutable-map key indexes still depend on the immutable-map closure |
| Sparse immutable tables | `SparseImmutableTable` nested immutable maps and construction maps | Row/column/cell encounter order, sparse views, immutable-map closure and builder accumulation |
| Mutable hash tables | `HashBasedTable`, `StandardTable`, `StandardRowSortedTable` | Nested map factories, caller-supplied delegate aliases, row/column views and serialized map envelopes |
| Reference/concurrent collections | `MapMakerInternalMap`, `MapMaker`, `Interners`, `ConcurrentHashMultiset` | Weak/reference queues, reclamation, identity equivalence, concurrency publication, CAS counts and weakly consistent traversal |
| Auxiliary rank indexes | `ExplicitOrdering` rank map and `Ordering.ArbitraryOrdering` weak identity map | Immutable-map or reference-map closure, stable ranks and reclamation |
| Other delegated mutable maps | `EnumHashBiMap` inverse map; `MutableClassToInstanceMap` default/supplied map | Inverse and checked-entry contracts, caller aliases and serialization |
| Polymorphic factories and collectors | `MultimapBuilder`, `Multimaps`, `CollectCollectors`, `TableCollectors` | Explicit linked/tree strategies, supplied maps/factories, parallel combination and merge callback order |
| Collision defenses | All four `JdkBackedImmutable*` owners; compact hash map/set delegate paths | Existing fallback remains until an equivalent collision defense is proved |
| Public concrete JDK factories | `Maps`, `Sets`, `Lists`, `Queues` | Public concrete return types and explicit JDK tree/linked/concurrent requests prevent blind substitution |

Arrays already used by regular lists/sets, sorted immutable data, enum counts,
heap/selection buffers and range lists are recorded as such. JDK `ArrayDeque`,
`EnumMap` and `EnumSet` delegate boundaries are recorded separately from Guava's
intrinsic array fields. The normal compact representation and its collision
fallback are never collapsed into an unconditional all-array claim.

Android donor source hashes appear in `donor-sources.json`. Android/GWT variants
and the repository's graph, cache and concurrent packages are outside this JRE
`collect` census and remain outside this packet's completion claim.

## Reproducible finite proof

`pom.xml` is an isolated verification module. Run Maven with JDK 21 and the source
owner's Java 8 source/class-file settings. It compiles the actual JRE sources and
unchanged `guava-testlib` source closure. It does not replace the repository's
native JDK 27 reactor or its Error Prone gate.

Build the repository's existing relocated `failureaccess` and `truth` verification
dependencies first. Run this module once against the pinned baseline using
`-Dm3.source.root=/absolute/baseline` and a separate `-Dm3.proof.output` directory.
Then run the received module against the received sources with
`-Dm3.baseline.classes=/absolute/baseline-proof/classes` and a fresh proof output.
The new differential tests fail if the independent baseline classes are absent.

The ten frozen JRE suite owners run 50,273 cases. Their source and the complete
unchanged generator/testlib closure are hashed in `frozen-test-oracles.json`.
The additional ten checks distinguish actual retained array/count fields, retain
the compact collision fallback, execute 12,000 bimap and 20,000 multiset mutation
steps against the independent original classes, check replacement callbacks and
iterator/entry edits, preserve original/candidate serialization in both directions
(including shared inverse identities and multiset self-reference), and cover the
two donor insertion-order defects with focused regressions.

The original JRE suites remain byte-for-byte unchanged. The first candidate passed
all of them while failing the seeded bimap comparison; this failure was retained
and repaired. Recipe parsing/replay success is distinct from Java/runtime success.
Every accepted product source is received from actual qualified SDK Results.

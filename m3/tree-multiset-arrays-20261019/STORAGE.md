# TreeMultiset: partial AVL topology conversion

This packet changes the existing JRE TreeMultiset owner. It extends the exact collect source census in ../array-storage-20261019/storage-coverage.tsv and the immutable successor deltas. It introduces no alternate public collection and preserves the existing com.m3.collections.guava identity.

## Physical representation and its boundary

| State | Retained representation |
| --- | --- |
| Element payload | Object[] references to distinct user elements |
| AVL topology | int[] left/right child slots; byte[] heights |
| Counts and aggregates | int[] occurrence counts and subtree distinct totals; long[] subtree occurrence totals |
| Sorted traversal | int[] predecessor/successor slots; slot zero is the constant header |
| Slot lifetime | int[] free/retired links, long[] generations, byte[] state; shared scalar operation/callback depths |
| Exported count compatibility | ExportedCount[] reference lane; a lazy shared object containing one int per observed live generation |
| Exceptional cursor chain | Iterator-owned Object[] payloads, ExportedCount[] shared states, long[] generations and int[] direction/index columns |
| Active cursor custody | Temporary CursorSnapshot<?>[] descriptor array and scalar count; array is nulled at outer operation exit |
| Views | Existing GeneralRange descriptors and inherited descending/element views share the same SlotStore |

The private AvlNode class, its per-element left/right/pred/succ object graph, the separate Reference object and header node are removed. This is a **partial topology conversion**, not node-free or pure primitive retained storage: live entry behavior requires the explicitly retained ExportedCount objects described below. No throughput, latency or measured footprint improvement is claimed.

Ordinary insertion, count queries and forEachEntry without exporting entries or nested exceptional cursor capture allocate no ExportedCount objects. The first entry wrapper for a slot generation lazily creates one state object, and later wrappers share it. An exceptional cursor-chain capture also obtains the shared state for each captured generation before release, including any still-live continuation. Each state contains only one int and no element, owner or topology reference. An ExportedCount[] reference lane spans the slot capacity regardless of how many entries were observed. Existing entry wrappers additionally capture the original element, slot, generation and shared state.

Before any entry export, a 64-element owner has zero compatibility objects. After the first entry export it has one; a second wrapper for the same generation shares that object. Full entry traversal can leave one compatibility object per live distinct element, even if callers then drop every wrapper. Those current-generation states remain until retirement or clear. When the original generation becomes unreachable, the arena transfers its final count into the state and nulls its reference. Only previously returned wrappers or captured cursors can then retain that state, so it becomes reclaimable with those API objects. The arena retains no historical-generation registry.

The contract test checks both shapes and release: 64 populated entries, one shared first-entry state, 64 states after full export, 63 after removal, and zero owner state/payload references after clear. Another 128 rejected clear/add cycles leave an empty owner with zero compatibility-state references, zero element references, used-slot high water 2 and payload capacity 8 while all held exceptional entries still report their original detached count 3. These are exact reference/field observations, not JVM heap-size or GC-timing measurements.

The rejected primitive-history predecessor has separate frozen evidence: after 128 such cycles with no retained probe entries, an empty owner still retained 128 generation/count records. It is not admitted by this packet. The shared-count successor removes that unbounded historical owner retention.

## Semantic atoms and callback lifetime

The comparator traversal, AVL rotations and aggregate changes come from the pinned original JRE TreeMultiset owner. Direction selects the existing ascending/descending entry traversal algorithm over integer links. Root checks compare the original expected root identity represented by a stable slot ID. Active operation frames defer slot reuse, preventing an ABA root match during comparator or visitor reentry. Grown columns publish only after every allocation succeeds; comparator-bearing recursion finishes before a caller writes through a reloaded array field.

An entry preserves its original element identity. It reads the live generation's count when available. Once detached, it reads the transferred count; a zero detached count delegates to the original owning view's comparator-equivalent count lookup. The exceptional positive detached count from clear during add(existing), followed by a failed root check, remains positive after slot reuse. Repeated retirement always zeros the count, even when the slot was already queued. No exported state changes its final count after the arena releases that generation.

Structural callback reentry or a root-identity conflict triggers a primitive reachability pass over both authoritative roots: the AVL root and the header. It follows all four original child/order links. This preserves successful same-root reattachment and any original partial writes still reachable after a failed outer root check; it frees only actual orphans. If the original callback leaves root and linked-order membership inconsistent, an explicit unusual-topology flag keeps reconciliation active for later structural operations until that shape is removed. An ordinary later clear therefore releases root-only resurrected generations while preserving their held positive count.

The reconciliation path scans used slot capacity and uses one transient int[] work stack of that size. Ordinary well-formed mutation keeps the original logarithmic AVL work plus constant work per retired slot; full graph scans occur after structural callback interference, failed root checks, or while an exceptional inconsistent topology remains. Arrays retain allocated capacity for reuse, as disclosed. The partial-write semantics and their exceptional scan cost are preserved rather than silently repaired into a different collection state.

A cursor stores its element and generation before its own operation releases a captured generation. Exceptional detachment can require the full remaining direction chain: object-array element references and shared count states, primitive generation/direction columns and a primitive hash index. These arrays belong to that iterator and cease to be retained when it drops the capture or becomes unreachable. A detached prefix reads the captured old generations; once traversal reaches a still-live generation, it reads current arena counts and direction links. A visitor can therefore update a live successor count or append another live successor without turning traversal into a stale snapshot.

A callback can create a cursor before any retirement is pending, then insert a new successor, retire both old and new nodes, and return. An early immutable chain cannot preserve that original behavior. The arena temporarily registers descriptors for cursors created within an already active operation, updates their current positions as they advance, and refreshes each remaining chain from the final leased generations immediately before release. This also preserves positive detached count writes made by the outer mutation after the creating callback returns. The outer finally clears each registration, nulls every temporary array entry and then nulls the arena's array and resets its count, on success and failure. No arena field retains a cursor or its captured chain between operations.

Let C be the number of descriptors created within the active operation, including cursors the callback discards, and let L be the sum of chain lengths captured for them. This exceptional machinery adds O(C + L) retained references/primitive cells while the operation is active. Each capture visits its direction chain and builds a primitive lookup table; final refresh can copy a chain again after later callback writes. There is no speed claim for this path. Nested internal iteration, such as bounded clear, also creates a descriptor; ordinary top-level iterator construction does not register one. After release only surviving iterators retain their own exceptional arrays. A contract creates and discards 32 cursors in one comparator callback, observes a 32-slot tracking array during the call, and then verifies a null owner tracking array, count zero, and zero empty-owner element and count-state references. The late-clear, root-conflict and explicitly throwing callback cases independently check tracking drain on exit.

Detached chains survive later bound-comparator callbacks that insert fresh elements and reuse old slots within the same public call. Original NPE type, comparison order and mutation effects remain exact, including a later clear that nulls a link after cursor creation. Ordinary traversal after unrelated external structural mutation remains the original unspecified boundary; no universal iterator-after-mutation equivalence is claimed and no new global modification epoch is introduced.

## Original oracle and discriminators

The frozen original baseline passed 205,610 cases: the prior 101,107-case closure plus 104,503 original TreeMultiset cases. The finite Maven proof compiles actual JRE source and the unchanged testlib with source/target 8 on JDK 21. The changed TreeMultiset source and its ten new contracts also compile with the repository's pinned Error Prone 2.50.0-m3-guava-namespace-20261018 checker and declared source/target 8 flags on JDK 25. This does not claim the entire native JDK 27 reactor or Android/GWT qualification.

The ten contract tests cover primitive AVL topology and capacity reuse; exact heights, totals, sorted links and free-list disjointness; retained element identity and comparator-equivalent reattachment; 10,000 deterministic operations over four comparators and shared views against independent original classes; comparator exception traces and five reentry modes; rejected-root positive detached counts; lazy/shared/released compatibility states; same-root resurrection, repeated retirement and later ordinary clear; range endpoint detachment/reuse in both directions and pending-reconciliation orphan capture; all-detached and mixed live chain traversal in both directions; late-clear, new-generation successor, later outer-write and throwing-callback nested cursor traces; temporary registration capacity and complete release; four visitor mutation modes; and serialization in both directions with independent range snapshots.

The comparator differential test compares exception types, callback traces, explicit owner validation messages and mutations. A frozen JDK 25 run demonstrated that HotSpot can alternate enhanced null-dereference text with null at a hot throw site; the same unmodified ten tests passed with -XX:-OmitStackTraceInFastThrow. The portable test normalizes only omitted/enhanced VM NPE diagnostics, preserving other explicit validation messages. Both runs remain evidence.

Original test source hashes are frozen in frozen-original-test-oracles.json. The 22 prior qualified contract cases are frozen in predecessor-contract-oracles.json. Product writes require canonical com.synexia SDK results, source sealing, receiver refusal proof, actual receiver runtime tests and a zero-change fixed point. Final counts and receipts accompany publication.

```sh
mvn -f m3/tree-multiset-arrays-20261019/pom.xml \
  -Dm3.baseline.classes=/path/to/original-baseline/classes test
```

Build the original oracle from its pinned original checkout with -Dm3.source.root and a separate -Dm3.proof.output. The original checkout does not contain the subsequently added contracts.

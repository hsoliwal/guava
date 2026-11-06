# HashBiMap hash-flooding port (2026-11-06)

The fork array HashBiMap now has the hash-flooding fallback from upstream 7cd44b350. When an insertion meets a key bucket or value bucket that already holds MAX_HASH_BUCKET_LENGTH (9) entries, the entry indexes move to j.u.HashMap tables (`FloodedTables`, entry index by key and by value). Every lookup, link, unlink, slot move and clear then goes through those tables. `isUsingHashFloodingResistance()` and `convertToHashFloodingResistantImplementation()` match the upstream test API.

Upstream HashBiMapTest (merged with fork tests) and HashBiMapFloodingTest moved back from m3/retained-variants/upstream-sync-20261106/ to guava-tests. The upstream Node-based HashBiMap stays retained there.

Proof: `HbmFloodDiff.java` (package com.m3.collections.guava.collect) runs one random op stream on a plain map, a map forced into FloodedTables and a map whose keys and values all hash to 7. After every step it compares return values or exception classes, entry order and inverse order. Result: `HBM_FLOOD_DIFF PASS checks=900000 seeds=300 steps=3000`, with javac --release 21 over guava main (614 files) at 0 errors.

Not run: guava-tests. Fork-master tests do not compile: the relocation renamed `com.google.common.truth`.

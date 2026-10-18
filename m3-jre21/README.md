<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava M3 Java 21 variant

This module builds the complete `com.google.guava:guava:33.4.8-m3-java21`
provider with qualified native storage for `ArrayListMultimap` and `HashBiMap`.
HashBiMap keeps payloads, hashes, insertion order, bucket links, offsets, mask,
generations, free slots and lifetime state in parallel arrays. It retains the
original concrete class, public/protected API, mutation behavior and stream
protocol. The existing nine ArrayListMultimap/M3 core Java sources are unchanged.

Map.Entry projections are created only by entry APIs. A private bounded
128-slot primitive-array M3LFUCache adaptation manages those API views using
slot, long generation and creator-iterator lease coordinates. Eviction drops
references; escaped entries are never retargeted or shared across incompatible
creator iterators. Single-pass traversal need not produce cache hits.

Build with JDK 21 and Maven:

```sh
mvn -f m3-jre21/pom.xml package
```

The artifact is
`m3-jre21/target-hash-bimap-stream-qualified-v4/guava-33.4.8-m3-java21-hash-bimap-stream-qualified-v4.jar`.
The build compiles native sources against the provided original Guava oracle,
runs all 4,533 unchanged HashBiMap cases, 6,064 unchanged ArrayListMultimap
cases and four native storage tests, then streams the original archive and
native replacements into a complete modular JAR. Existing Assembly/Plexus
and in-process Ant move avoid an unpacked original runtime tree. Production
compilation retains Java 21, `-Xlint:all` and `-Werror`.

The actual final artifact also passed the same 10,601 cases as sole Guava
provider, public/protected ABI, UIDs, callback/reentrant traces, independent-JVM
serialization in both directions, classpath and named-module smokes, 12,000
LFU accesses and 10,000 callback churn operations. It retains the original
`com.google.common@33.4.8-jre` identity/version and all module requirements,
exports, opens and services; only the private M3 package is added to ModulePackages.
The seventeen explicit manifest attributes identify Java 21 and the variant.

Final artifact SHA-256: `0d1f77f56a15a9c6c91e3faca6b598e8bff5b17ecb76c323feff9c427351539a`.
Final report SHA-256: `1a3c4cb47de723d59fd64c756b9a1ea14158847455a4521b7dd992c077ce0a4f`.
See `qualification/HASH_BIMAP_FULL_QUALIFICATION_REPORT_V7.json` and
`qualification/NATIVE_GUAVA_MANIFEST_COUNTEREVIDENCE_V1.json`. The latter
corrects the earlier artifact's overstated manifest qualification; historical
reports and source/behavior proofs remain preserved.

The original `guava/src` sources and official tests remain unchanged oracles.
Source/proof ownership and exact recipe inputs are recorded in
`SOURCE_PROVENANCE.tsv` and the canonical crate: https://github.com/hsoliwal/com.synexia/tree/03de1b62d58a98972e42eb09fae7635ff007c1e0/synexia-openrewrite-recipes/recipe-crates/guava-native-hash-bimap-v7-20261018.
This increment builds on merged Guava PR #4 at
`0d2b43dd16385c12f79eba52fb9fea6bc9835804`, on
`m3/guava-33.4.8-jre-baseline`; unrelated master work is preserved.

Configured autoreview returned NO_VERDICT under its mandatory no-tools mode;
it was not retried, switched or bypassed. Hosted CI and draft review remain
pending. This increment does not claim universal nested-pressure/OOM behavior,
performance improvement, conversion of all retained Guava owners, Android/GWT,
java.base promotion, or completion of the 107-family collection superset.

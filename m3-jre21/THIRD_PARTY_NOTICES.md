# Source attribution

Guava is Copyright The Guava Authors, licensed under Apache License 2.0.
The original source is google/guava, reused through the user fork
hsoliwal/guava at f06690fa3e874f65515e8fd338a74d636e2c792f.
The official guava-33.4.8-jre.jar SHA-256 is
f3d7f57f67fd622f4d468dfdd692b3a5e3909246c28017ac3263405f0fe617ed.
Its original META-INF/LICENSE remains byte-identical in the packaged artifact.
This module's LICENSE contains that Apache License 2.0 text.

M3 storage is Copyright 2026 Hitesh Soliwal and contributors to the Synexia
Project, licensed under Apache License 2.0. The qualified receiver source base
is hsoliwal/M3jdk21@2f2beb2eff7a88f1f7c0012fd17a78a6f0a00b49.
The expected-key reservation mechanism is derived and qualified first in the
existing canonical PackedHashMap owner from
hsoliwal/com.synexia@140c263a90f9803b9c46103c63355a286e0515f0, then adapted to
the existing M3LinkedHashMap receiver. The list adds the explicit trim-epoch
mode required by Guava while retaining its existing no-argument behavior.
Canonical source ownership remains in Synexia; these are pinned, qualified
vendored storage sources, not new parallel collection implementations.

Source SPDX notices are preserved. SOURCE_PROVENANCE.tsv records exact source
identities, and qualification contains the reuse decisions and recipe hashes.
The unpack/compiler/test/JAR build plugins remain separate Apache-licensed
build dependencies; their implementation is not copied into this module.


## Native HashBiMap and API entry cache (2026-10-18)

The original JRE HashBiMap flow is retained from Guava 33.4.8, Git blob
`f5254b367e72e97067739c40c91bdfd94edd4847`. The array/index donor is Guava's
Android HashBiMap, blob `ad9138c8eca437bda700df63a4afa80e5abe474f` at baseline
`f06690fa3e874f65515e8fd338a74d636e2c792f`. Original Guava Apache-2.0 author
and license notices remain unchanged.

The private API-view cache adapts M3LFUCache from hsoliwal/M3jdk21 commit
`6ae20943e2295e6120c9666a04dc1f2be29f4873`, blob
`315fad8a68c1af45d7e23bdded687bea3d6537e8`, Apache-2.0, Copyright 2026
Hitesh Soliwal and contributors. The adaptation adds long generation and
creator-iterator lease coordinates while keeping cache bookkeeping primitive.
M3-HASH-BIMAP-NOTICE.txt and M3-LFU-API-ENTRY-NOTICE.txt preserve the exact
notices included in the qualified final artifact.

Canonical recipe/proof custody: https://github.com/hsoliwal/com.synexia/tree/03de1b62d58a98972e42eb09fae7635ff007c1e0/synexia-openrewrite-recipes/recipe-crates/guava-native-hash-bimap-v7-20261018.
Assembly 3.8.0, Plexus Archiver 4.10.4 and AntRun 3.2.0/Ant 1.10.15 are
Apache-2.0 build dependencies; their implementations are not copied here.
The canonical XML and Java recipe engines are unchanged.


## Native HashMultimap array and API-projection cache increment

Retained Guava 33.4.8 HashMultimap, Apache-2.0, source blob 719c0b40f66e87b92060cf074d067912e452e6f6. Original Guava author and license notices are preserved. The private value-set membership pattern adapts M3LinkedHashSet blob e1ac806d1ebe8aac1b9efe7bcfbaa3f675e7d0d6; the unchanged M3LinkedHashMap blob bc998ec2e65e1997de0e95b138818d04308a5105 supplies the existing array/index tables.

The primitive M3LFUCache adaptation derives from hsoliwal/M3jdk21@6ae20943e2295e6120c9666a04dc1f2be29f4873, blob 315fad8a68c1af45d7e23bdded687bea3d6537e8, Apache-2.0, Copyright 2026 Hitesh Soliwal and Contributors. Its use here is limited to immutable pair API projections; live entry/count projections remain open. M3-HASH-MULTIMAP-NOTICE.txt is copied byte-for-byte from the qualified artifact.

Canonical custody: https://github.com/hsoliwal/com.synexia/tree/c7a2de8816d2097c73125bc4b8e95227b3a83605/synexia-openrewrite-recipes/recipe-crates/guava-native-hash-multimap-v5-20261018. Existing Apache-2.0 build dependencies remain external. No challenge-platform submitted solution was copied. JMH/jopt-simple/Commons Math remain external benchmark-only dependencies with their original licenses; no dependency implementation is included in the provider.

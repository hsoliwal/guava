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

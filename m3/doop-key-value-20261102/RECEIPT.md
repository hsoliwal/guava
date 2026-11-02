<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# DOOP key/value source receipt

Both actual ArrayTable source flavors provide direct indexed values traversal while preserving cached/live collection views, order, nulls and mutation failures.

Canonical recipe and qualification: [Synexia commit cfa05713fe53](https://github.com/hsoliwal/com.synexia/commit/cfa05713fe5395694af36df74373dcfe1e7e9031).

Receiving parent: `7a616e45c9fb941aedfee08bb9b1dc9996022a86`. Canonical datasets: `doop-key-value-guava-jre-20261102`, `doop-key-value-guava-android-20261102`. All Java files below are actual verified SDK Result outputs.

## Executed verification

285/285 tests per JRE and Android source flavor; five original classes per flavor. Measured traversal allocation fell from 98,368 to 24 bytes per 4,096 values. Independent view probes and Java 8 source compatibility passed.

## Exact output blobs

| Path | Git blob |
|---|---|
| `guava-tests/test/com/m3/collections/guava/collect/M3ArrayTableValuesProjectionTest.java` | `1f22e5d4c4c34358acc359df67a3794ab48518e9` |
| `guava/src/com/m3/collections/guava/collect/ArrayTable.java` | `15bacc3c6d7630a94efe6753255e6f7d55e8e839` |
| `android/guava-tests/test/com/m3/collections/guava/collect/M3ArrayTableValuesProjectionTest.java` | `1f22e5d4c4c34358acc359df67a3794ab48518e9` |
| `android/guava/src/com/m3/collections/guava/collect/ArrayTable.java` | `20817c790d1d64dc8239a8018c22a279ace681d9` |

## Acceptance scope

The configured native JDK 27/testJDK toolchain gate remains unresolved. Android source tests ran on Java 21; no device/GWT or whole-library storage qualification is claimed.

The existing broader branch acceptance gates remain in force. This receipt does not claim whole-repository or fleet-wide conversion, universal throughput gains, or default-branch integration.

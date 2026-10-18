<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava working fork package-first M3 receiving
## Current full-family namespace admission (2026-10-18)

The complete fork now receives `com.google.common` as `com.m3.collections.guava`
and `com.google.thirdparty` as `com.m3.collections.guava.thirdparty` through the
canonical Synexia OpenRewrite owner. The task packet is
`m3/package-import-rename-20261018`. JRE and Android source trees are both covered.
Use its `m3-relocated-verification` Maven profile after building the distinct
verification dependency receivers; published original cache artifacts are preserved.

The original differential evaluation is recovered from immutable Git object
`dfd9dca629d44faf71846b1f85acd38556f4854d` by the current namespace workflow.
The renamed standalone proof refuses a same-class oracle comparison. Recipe
manifests and templates below retain their historical seals; the previous
`com.m3.sdk.guava` staging recipe and original-source path commands are historical
inputs and are superseded by the current workflow and task packet.

---



Pinned original hsoliwal/guava@9247d70e1638aa88103f727d2c47c66c4f93fd16
(upstream google/guava, Apache-2.0). Original
`com.google.common.collect.EvictingQueue` is copied in full to
`com.m3.collections.guava.collect.EvictingQueue` with only package line and
imports of original Guava `ForwardingQueue` and `Iterables` siblings changed.
The original Guava source stays untouched. Class identity and serialized
class name change so this is not a drop-in binary replacement.

A seeded standalone differential compares complete original and relocated
queue public behavior, including capacity zero, add/offer/addAll/poll,
duplicates, iteration removals and independent serialization. It is a fork
working-area stage, not a competing M3JDK21 product runtime backend.
Original Guava Apache-2.0 copyright remains verbatim.
Full-family relocation uses the reusable declarative OpenRewrite ChangePackage
recipe already catalogued by Synexia PR #12290; no source copied from
LeetCode/HackerRank/GFG.

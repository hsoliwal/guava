<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and contributors -->
# M3 Guava source-workspace relocation
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



**Original donor fork:** `hsoliwal/guava`, pinned commit `9247d70e1638aa88103f727d2c47c66c4f93fd16`.
**Canonical recipe owner:** [hsoliwal/com.synexia#12293](https://github.com/hsoliwal/com.synexia/pull/12293) at `0f96532236f9d2b113b635f8c14884f64a75f6fc`.
**M3JDK21 product receiver:** [#1293](https://github.com/hsoliwal/M3jdk21/pull/1293).

This branch prepares a **real source package relocation recipe** for guava (and guava-tests, guava-testlib integration closure).
Do not rename public upstream packages in fork `master`. Keep the fork's original
public release and provider compatibility libraries intact. Apply the recipe
only in a reviewed, pinned scratch/worktree after the relevant tests are frozen.

**From a detached scratch worktree of this branch** with Maven and OpenRewrite dependencies:
```sh
mvn -B -ntp org.openrewrite.maven:rewrite-maven-plugin:5.23.1:dryRun \
  -Drewrite.configLocation=.m3-relocation/rewrite.yml \
  -Drewrite.activeRecipes=com.synexia.m3.RelocateGuavaFork20261018

# When the dry-run patch, notices, and dependent modules have been admitted:
mvn -B -ntp org.openrewrite.maven:rewrite-maven-plugin:5.23.1:run \
  -Drewrite.configLocation=.m3-relocation/rewrite.yml \
  -Drewrite.activeRecipes=com.synexia.m3.RelocateGuavaFork20261018
```

Source before `com.google.common` -> after `com.m3.sdk.guava`. Both package
declarations and Java type references must be rewritten through OpenRewrite's
AST visitors. The current PR commits the recipe **but not yet its whole-fork
generated postimages**. No source-relocation execution or donor tests are claimed
until exact generated files have been checked back into the fork branch.

**Licensing:** original donor source remains under **Apache-2.0** including
all retained copyright headers/NOTICE; this added recipe description is original
first-party Apache-2.0. A package rename does not relicense upstream files.
Existing `com.m3.collections` backends are the target M3 runtime primitives,
not a parallel collection engine. Group semantic atoms by source file and prove
call/return/null/equality/iterator/view/serialization/concurrency contracts,
then apply array-offset-index-mask lowering via separate canonical recipes.
LeetCode, HackerRank, and GeeksforGeeks are reference-only algorithm categories.

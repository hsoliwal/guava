<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Android nested-type import recovery

The earlier namespace output omitted 92 imports from 69 Android test and benchmark
sources. This source-sealed recovery uses the exact original Guava commit
`dfd9dca629d44faf71846b1f85acd38556f4854d`, with the already established M3 package
spelling. Every non-import byte is identical to receiving baseline
`373312292f38f15fb8242cfb11910721a793eac1`.

All test bodies, assertions, data, benchmark bodies, annotations, method/class
declarations and original license headers remain intact. No test exclusions,
compiler diagnostic suppression or production storage algorithm change is included.
The native Android compiler and runtime remain the qualification authority.

The existing Synexia `CollectionsContractRefinement20261017` Maven/OpenRewrite
recipe owns the exact preimages and recovered postimages. Its shared scope owner
admits the two physically observed benchmark roots through a separately qualified
successor. Actual SDK results, refusal cases and zero-change receiver replay must
qualify this packet before it is received or published.

This is a compiler/namespace recovery supporting the all-fork collection work.
It does not count these 69 test/benchmark files as array-storage transformations.

<!-- SPDX-License-Identifier: Apache-2.0 -->
# Guava working fork package-first M3 receiving

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

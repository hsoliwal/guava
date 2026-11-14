#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# JDK21 independent model; does not substitute for the native Guava suites.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac --release 21 -Xlint:all,-serial -Werror -d "$tmp" \
  "$here/NodeArrayModel.java" "$here/ContainsValueArrayProof.java" \
  "$here/PagedEntryArrayProof.java"
java -cp "$tmp" NodeArrayModel 20 20000 > "$tmp/jit.out"
java -Xint -cp "$tmp" NodeArrayModel 3 2500 > "$tmp/xint.out"
grep -Fxq 'PASS 13236380' "$tmp/jit.out"
grep -Fxq 'PASS 248184' "$tmp/xint.out"
java -cp "$tmp" ContainsValueArrayProof > "$tmp/value-jit.out"
java -Xint -cp "$tmp" ContainsValueArrayProof 2000 > "$tmp/value-xint.out"
grep -Fxq 'PASS assertions=60000' "$tmp/value-jit.out"
grep -Fxq 'PASS assertions=6000' "$tmp/value-xint.out"
java -cp "$tmp" PagedEntryArrayProof > "$tmp/paged-entry.out"
grep -Fxq 'PASS assertions=102885' "$tmp/paged-entry.out"
echo 'ENTRY_PAGE_CACHE_MODEL_PASS assertions=102885'
echo 'PARENT_OWNED_SLOT_ARRAY_MODEL_PASS jit=13236380 interpreted=248184'
echo 'NATIVE_GUAVA_JRE_ANDROID_MAVEN_ABI_JMH_HOLD'

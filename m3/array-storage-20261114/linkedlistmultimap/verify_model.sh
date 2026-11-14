#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# JDK21 independent model; does not substitute for the native Guava suites.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac --release 21 -Xlint:all,-serial -Werror -d "$tmp" "$here/NodeArrayModel.java"
java -cp "$tmp" NodeArrayModel 20 20000 > "$tmp/jit.out"
java -Xint -cp "$tmp" NodeArrayModel 3 2500 > "$tmp/xint.out"
grep -Fxq 'PASS 12836380' "$tmp/jit.out"
grep -Fxq 'PASS 240684' "$tmp/xint.out"
echo 'PARENT_OWNED_SLOT_ARRAY_MODEL_PASS jit=12836380 interpreted=240684'
echo 'NATIVE_GUAVA_JRE_ANDROID_MAVEN_ABI_JMH_HOLD'

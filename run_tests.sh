#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

OUT=build/test_classes
rm -rf "$OUT"
mkdir -p "$OUT"

javac -source 8 -target 8 -Xlint:-options -d "$OUT" \
  src/io/github/expirt/storageredirect/Glob.java \
  src/io/github/expirt/storageredirect/PathEngine.java \
  test/io/github/expirt/storageredirect/PathEngineTest.java

java -cp "$OUT" io.github.expirt.storageredirect.PathEngineTest

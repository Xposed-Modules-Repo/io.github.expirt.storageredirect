#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

OUT=build/test_classes
rm -rf "$OUT"
mkdir -p "$OUT"

AJ="${ANDROID_JAR:-}"
if [ -z "$AJ" ]; then
  if [ -f "/storage/emulated/0/projects/toolkit/android-sdk/platforms/android-37/android.jar" ]; then
    AJ="/storage/emulated/0/projects/toolkit/android-sdk/platforms/android-37/android.jar"
  elif [ -f "/data/user/0/com.agy/files/usr/share/android-sdk/android.jar" ]; then
    AJ="/data/user/0/com.agy/files/usr/share/android-sdk/android.jar"
  else
    AJ="/usr/lib/android-sdk/platforms/android-23/android.jar"
  fi
fi

javac -source 8 -target 8 -Xlint:-options -bootclasspath "$AJ" -cp "stubs" -d "$OUT" \
  src/io/github/expirt/storageredirect/Glob.java \
  src/io/github/expirt/storageredirect/PathEngine.java \
  src/io/github/expirt/storageredirect/RedirectConfig.java \
  src/io/github/expirt/storageredirect/Backups.java \
  test/io/github/expirt/storageredirect/PathEngineTest.java \
  test/io/github/expirt/storageredirect/SecurityTest.java

TEST_TMP="${HOME:-.}/tmp"
mkdir -p "$TEST_TMP"

java -Djava.io.tmpdir="$TEST_TMP" -cp "$OUT:$AJ:stubs" io.github.expirt.storageredirect.PathEngineTest
echo ""
java -Djava.io.tmpdir="$TEST_TMP" -cp "$OUT:$AJ:stubs" io.github.expirt.storageredirect.SecurityTest

#!/usr/bin/env bash
# Builds StorageRedirect.apk without Gradle (Debian/Ubuntu or Android/Termux):
#   sudo apt install openjdk-21-jdk-headless aapt apksigner dalvik-exchange \
#        libandroid-23-java zipalign
set -euo pipefail
cd "$(dirname "$0")"

AJ="${ANDROID_JAR:-}"
if [ -z "$AJ" ]; then
  if [ -f "/data/user/0/com.agy/files/usr/share/android-sdk/android.jar" ]; then
    AJ="/data/user/0/com.agy/files/usr/share/android-sdk/android.jar"
  else
    AJ="/usr/lib/android-sdk/platforms/android-23/android.jar"
  fi
fi

DX="${DX_JAR:-/usr/share/java/com.android.dx.jar}"
KS="${KEYSTORE:-/storage/emulated/0/sign/x.p12}"
KS_PASS="${KEYSTORE_PASS:-123456}"
KS_ALIAS="${KEY_ALIAS:-a}"

# Extract version from AndroidManifest.xml
VER=$(grep 'android:versionName=' AndroidManifest.xml | head -1 | sed -E 's/.*android:versionName="([^"]+)".*/\1/')
APP_NAME="StorageRedirect"
REL_DIR="release"
OUT=".build_tmp"

mkdir -p "$REL_DIR"
rm -rf "$OUT" build
mkdir -p "$OUT/stubcls" "$OUT/classes"

# Xposed API stubs: compile-time only, NOT packaged (the framework provides the real API).
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$AJ" -d "$OUT/stubcls" \
  $(find stubs -name '*.java')
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$AJ" -cp "$OUT/stubcls" \
  -d "$OUT/classes" $(find src -name '*.java')

if command -v d8 >/dev/null 2>&1; then
  d8 --min-api 21 --lib "$AJ" --classpath "$OUT/stubcls" --output "$OUT" $(find "$OUT/classes" -name '*.class')
else
  java -cp "$DX" com.android.dx.command.Main --dex --min-sdk-version=21 \
    --output="$OUT/classes.dex" "$OUT/classes"
fi

aapt package -f -M AndroidManifest.xml -S res -I "$AJ" -A assets -0 arsc -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
  echo "ERROR: Keystore not found at $KS" >&2
  exit 1
fi

FINAL_APK="$REL_DIR/${APP_NAME}-v${VER}.apk"
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" --key-pass "pass:$KS_PASS" \
  --min-sdk-version 21 --out "$FINAL_APK" "$OUT/aligned.apk"
apksigner verify "$FINAL_APK"

# Keep both release/ and releases/ directories synchronized
mkdir -p "$REL_DIR" "releases"
cp -f "$FINAL_APK" "releases/" 2>/dev/null || true
if [ -f "$FINAL_APK.idsig" ]; then
  cp -f "$FINAL_APK.idsig" "releases/" 2>/dev/null || true
fi

# Clean up all build junk immediately
rm -rf "$OUT" build

echo "Built and signed: $FINAL_APK"

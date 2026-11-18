#!/bin/bash
# Builds Etesian Glance (its own repo, Etesian-design's sibling) and copies the unsigned release APK here; Soong signs
# it with the platform key (Android.bp.in).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${GLANCE_SRC:-$HOME/Git/etesian-glance}"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$SRC"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease

APK="app/build/outputs/apk/release/app-release-unsigned.apk"
cp "$APK" "$HERE/EtesianGlance.apk"
echo "Copied $SRC/$APK -> $HERE/EtesianGlance.apk ($(git -C "$SRC" rev-parse --short HEAD))"
# Soong fails on a missing apk even for unused modules, so the module only exists next to the APK.
cp "$HERE/Android.bp.in" "$HERE/Android.bp"

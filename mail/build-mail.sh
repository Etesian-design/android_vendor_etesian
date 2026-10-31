#!/bin/bash
# Builds N9 Mail (patched Thunderbird for Android, foss release) and copies the unsigned APK here.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${THUNDERBIRD_SRC:-$HOME/android/email/thunderbird-android}"
PATCH="$HERE/patches/0001-N9-mail.patch"

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$PATH"
# Only fatal lint checks run on release builds anyway; skip them to save time.
export CI_CHECK_RELEASE_BUILDS=false

cd "$SRC"
echo "sdk.dir=$ANDROID_HOME" > local.properties

if git apply --reverse --check "$PATCH" 2>/dev/null; then
    echo "N9 patch already applied"
elif git apply --check "$PATCH"; then
    echo "Applying N9 patch"
    git apply "$PATCH"
else
    echo "N9 patch does not apply to $SRC (reset it to THUNDERBIRD_24_0?)" >&2
    exit 1
fi

./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx6g :app-thunderbird:assembleFossRelease

APK="app-thunderbird/build/outputs/apk/foss/release/app-thunderbird-foss-release-unsigned.apk"
cp "$APK" "$HERE/EtesianMail.apk"
echo "Copied $APK -> $HERE/EtesianMail.apk"
# Soong fails on a missing apk even for unused modules, so the module only exists next to the APK.
cp "$HERE/Android.bp.in" "$HERE/Android.bp"

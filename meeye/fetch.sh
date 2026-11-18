#!/bin/bash
# Meeye, the Harmattan-style icon pack by YeyeBBC (https://github.com/yeyebbc/meeye-releases), preinstalled as
# the launcher's default icon pack. Its icons are CC BY-NC 4.0 (the release's LICENSE file says BY-NC-ND 4.0):
# it ships unmodified, as the author signed it, with credit, and never for sale.
#
# Downloads the pinned release and checks it; etesian.mk adds it to the ROM only when it is here.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
VERSION=E-10.0
URL="https://github.com/yeyebbc/meeye-releases/releases/download/$VERSION/Meeye-$VERSION-c400-release.apk"
SHA256=5eb1fe9191c16b8f12ac76c9d2d29b42f44689efc507addbd81ae8f0af5b2e00
APK="$HERE/Meeye.apk"
if ! echo "$SHA256  $APK" | sha256sum -c --status 2>/dev/null; then
    curl -fsSL -o "$APK.part" "$URL"
    echo "$SHA256  $APK.part" | sha256sum -c --status || { echo "Meeye: checksum mismatch" >&2; rm -f "$APK.part"; exit 1; }
    mv "$APK.part" "$APK"
fi
cp "$HERE/Android.bp.in" "$HERE/Android.bp"
echo "Meeye $VERSION ready"

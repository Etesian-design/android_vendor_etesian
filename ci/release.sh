#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-only
# Builds and signs an Etesian release for one phone on the DeskMini (ci/runner/README.md). The release workflow runs it once per
# phone with the next version; the keys' passphrase comes on stdin, never as an argument or a variable Docker can show.
#   printf %s "$ETESIAN_KEYS_PASS" | ci/release.sh <device> <version>
# Writes release/etesian-<version>-<device>.zip and its .sha256 in this checkout, next to the other phones' files.
#
# ci/<device>/device.sh says what differs per phone: LUNCH (how the product is chosen), and optionally PREPARE (shell run in the
# tree after repo sync, before Etesian's own patches) and MANIFEST (a local manifest put in place before the sync).
# ${CI_DIR}/<device>/src is a LineageOS tree (repo) kept between runs, ${CI_DIR}/<device>/ccache its compiler cache; each run only
# fetches what changed upstream. ${CI_DIR}/keys/etesian-keys.tar.gpg holds the release keys (gpg, symmetric); they are unpacked
# into memory for the signing step only.
# BUILD_ONLY=1: sync, patch and build, but no signing (no keys or passphrase needed); a check before a release.
set -euo pipefail
DEVICE="${1:?usage: release.sh <device> <version>, the passphrase on stdin}"
VERSION="${2:?usage: release.sh <device> <version>, the passphrase on stdin}"
CI="${CI_DIR:?CI_DIR: ~/ci of the host, as the runner sees it}"
HERE="$(cd "$(dirname "$0")" && pwd)"; ETESIAN="$(cd "$HERE/.." && pwd)"
[ -f "$HERE/$DEVICE/device.sh" ] || { echo "no ci/$DEVICE/device.sh" >&2; exit 1; }
LUNCH=""; PREPARE=""; MANIFEST=""
# shellcheck source=/dev/null
. "$HERE/$DEVICE/device.sh"
[ -n "$LUNCH" ] || { echo "ci/$DEVICE/device.sh sets no LUNCH" >&2; exit 1; }
W="$CI/$DEVICE"; KEYS="$CI/keys/etesian-keys.tar.gpg"; OUT="$ETESIAN/release"
[ -d "$W/src/.repo" ] || { echo "no LineageOS tree at $W/src (repo init + sync it once, ci/runner/README.md)" >&2; exit 1; }
if [ -z "${BUILD_ONLY:-}" ]; then
    [ -f "$KEYS" ] || { echo "no release keys at $KEYS" >&2; exit 1; }
    IFS= read -r PASS || true
    [ -n "$PASS" ] || { echo "no passphrase on stdin" >&2; exit 1; }
fi
# Only this phone's files: the release collects every phone's.
mkdir -p "$OUT" "$W/ccache"; rm -f "$OUT"/etesian-*-"$DEVICE".zip "$OUT"/etesian-*-"$DEVICE".zip.sha256
# Meeye, the launcher's default icon pack, is never committed (third-party): fetched here, pinned and checksummed, so the
# release has it as a local build does (etesian.mk leaves it out when it is missing, and the copy below would delete it).
"$ETESIAN/meeye/fetch.sh"

docker build -q -t etesian-rom-builder "$HERE/builder" >/dev/null
# One ROM build at a time: two at once run the DeskMini out of memory and the kernel kills the Java steps of both
# (2026-11-23, a test build beside the Pro1-X release). The runner never runs two jobs, but a build by hand can.
[ -z "$(docker ps -q --filter ancestor=etesian-rom-builder --filter ancestor=etesian-builder)" ] ||
    { echo "another ROM build is running on this machine" >&2; exit 1; }
# The build needs most of the machine's memory: etesian-vm is stopped for it and started again after, whatever the outcome.
if [ "$(docker inspect -f '{{.State.Running}}' etesian-vm 2>/dev/null)" = true ]; then
    docker stop -t 120 etesian-vm >/dev/null && trap 'docker start etesian-vm >/dev/null' EXIT
fi
run() {
    docker run --rm -i --tmpfs /keys:size=64m,mode=700,uid=1000,gid=1000 \
        -e USE_CCACHE=1 -e CCACHE_EXEC=/usr/bin/ccache -e CCACHE_DIR=/ccache -e VERSION="$VERSION" -e DEVICE="$DEVICE" \
        -v "$W/ccache:/ccache" -v "$W/src:/src" -v "$ETESIAN:/etesian:ro" -v "$KEYS:/keys.tar.gpg:ro" -v "$OUT:/release" \
        etesian-rom-builder bash -eo pipefail -c "$1"
}

# Upstream, then ours. The last run's patches are undone first so repo sync can move every project.
run '
cd /src
ccache -M 60G >/dev/null
repo forall -c "git am --abort 2>/dev/null; git reset -q --hard && git clean -fdq"
# Etesian'"'"'s apps in their own repos (Glance), fetched and built from source with the ROM.
cp /etesian/manifest/etesian.xml .repo/local_manifests/etesian.xml
'"${MANIFEST:+cp /etesian/ci/$DEVICE/$MANIFEST .repo/local_manifests/$MANIFEST}"'
# -d: back on the manifest revision, so patches applied as commits apply again
repo sync -d -c -j8 --no-tags --no-clone-bundle --optimized-fetch --force-sync
'"$PREPARE"'
# LineageOS includes vendor/extra/product.mk in every product when it exists: Etesian comes in there.
mkdir -p vendor/extra && echo "\$(call inherit-product, vendor/etesian/etesian.mk)" > vendor/extra/product.mk
# Never the N9 reference files or old snapshots. Etesian Mail is built outside the ROM (mail/build-mail.sh): the copy
# already in the tree stays.
# -O: folders get the time of the copy, so Soong lists a new folder (a kept old time hid it from its file globs).
rsync -a -O --delete --exclude .git --exclude node_modules --exclude /n9-files --exclude /backups --exclude /notes --exclude /local --exclude /release \
    --filter "P /mail/EtesianMail.apk" --filter "P /mail/Android.bp" /etesian/ vendor/etesian/
bash vendor/etesian/patches/apply.sh
source build/envsetup.sh >/dev/null
'"$LUNCH"' >/dev/null
# LineageOS copies TARGET_BOOTANIMATION without tracking it as an input: drop the copy so a changed boot animation is picked up.
rm -f out/soong/.intermediates/vendor/lineage/bootanimation/gen-bootanimation.zip/gen/bootanimation.zip \
      out/soong/.intermediates/vendor/lineage/bootanimation/bootanimation.zip/android_common/bootanimation.zip
# A module built on its own (m <app>) stays installed in out/ and would ship, even one the product overrides
# (2026-11-20: a stray WallpaperPicker2 took com.android.wallpaper from ThemePicker). Only what the product lists goes in.
m installclean >/dev/null
m -j$(nproc) target-files-package otatools
'

[ -z "${BUILD_ONLY:-}" ] || { echo "built $DEVICE (BUILD_ONLY: not signed)"; exit 0; }

# Signing: the keys exist only in the container's memory (/keys, a tmpfs) and go with it.
printf '%s\n' "$PASS" | run '
IFS= read -r PASS
printf %s "$PASS" | gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 0 -d /keys.tar.gpg | tar -x -C /keys
unset PASS
cd /src
export PATH=/src/out/host/linux-x86/bin:/src/prebuilts/jdk/jdk21/linux-x86/bin:$PATH   # the signing tools run java (the JDK in the tree)
TF=$(ls -t out/target/product/$DEVICE/obj/PACKAGING/target_files_intermediates/*-target_files*.zip | head -1)
# Every APEX is signed with our key named after it, or else after the key the build names for it (APEXes can share one, as
# com.android.hardware). One with neither (added upstream later) keeps its own key and is listed in the log. Presigned ones are
# left as they are.
apex=()
while read -r line; do
    a=$(echo "$line" | sed -n "s/^name=\"\([^\"]*\)\".*/\1/p")
    p=$(echo "$line" | sed -n "s/.* private_key=\"\([^\"]*\)\.pem\".*/\1/p")
    k=/keys/${a%.*}
    [ -f "$k.pem" ] || k=/keys/$(basename "$p")
    if [ -n "$p" ] && [ -f "$k.pem" ]; then
        apex+=(--extra_apks "$a=$k" --extra_apex_payload_key "$a=$k.pem")
    else
        echo "no key of ours for $a: it keeps its own"
    fi
done < <(unzip -p "$TF" META/apexkeys.txt | grep -v "container_certificate=\"PRESIGNED\"")
sign_target_files_apks -o -d /keys "${apex[@]}" "$TF" out/etesian-signed-target_files.zip
ota_from_target_files -k /keys/releasekey --block --backup=true out/etesian-signed-target_files.zip \
    "/release/etesian-$VERSION-$DEVICE.zip"
cd /release && sha256sum "etesian-$VERSION-$DEVICE.zip" > "etesian-$VERSION-$DEVICE.zip.sha256"
'
ls -l "$OUT"

#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Builds and signs an Etesian Pro1-X release on the DeskMini (ci/runner/README.md). The release workflow runs it with the next version;
# the keys' passphrase comes on stdin, never as an argument or a variable Docker can show.
#   printf %s "$ETESIAN_KEYS_PASS" | ci/pro1x/release.sh <version>
# Writes release/etesian-<version>-pro1x.zip and its .sha256 in this checkout.
#
# ${CI_DIR}/pro1x/src is a LineageOS tree (repo) kept between runs, ${CI_DIR}/pro1x/ccache its compiler cache; each run only
# fetches what changed upstream. ${CI_DIR}/keys/etesian-keys.tar.gpg holds the release keys (gpg, symmetric); they are
# unpacked into memory for the signing step only.
set -euo pipefail
VERSION="${1:?usage: release.sh <version>, the passphrase on stdin}"
CI="${CI_DIR:?CI_DIR: ~/ci of the host, as the runner sees it}"
HERE="$(cd "$(dirname "$0")" && pwd)"; ETESIAN="$(cd "$HERE/../.." && pwd)"
W="$CI/pro1x"; KEYS="$CI/keys/etesian-keys.tar.gpg"; OUT="$ETESIAN/release"
# BUILD_ONLY=1: sync, patch and build, but no signing (no keys or passphrase needed); a check before a release.
if [ -z "${BUILD_ONLY:-}" ]; then
    [ -f "$KEYS" ] || { echo "no release keys at $KEYS" >&2; exit 1; }
    IFS= read -r PASS || true
    [ -n "$PASS" ] || { echo "no passphrase on stdin" >&2; exit 1; }
fi
mkdir -p "$OUT" "$W/ccache"; rm -f "$OUT"/*

docker build -q -t etesian-rom-builder "$HERE/../builder" >/dev/null
# The build needs most of the machine's memory: etesian-vm is stopped for it and started again after, whatever the outcome.
if [ "$(docker inspect -f '{{.State.Running}}' etesian-vm 2>/dev/null)" = true ]; then
    docker stop -t 120 etesian-vm >/dev/null && trap 'docker start etesian-vm >/dev/null' EXIT
fi
run() {
    docker run --rm -i --tmpfs /keys:size=64m,mode=700,uid=1000,gid=1000 \
        -e USE_CCACHE=1 -e CCACHE_EXEC=/usr/bin/ccache -e CCACHE_DIR=/ccache -e VERSION="$VERSION" \
        -v "$W/ccache:/ccache" -v "$W/src:/src" -v "$ETESIAN:/etesian:ro" -v "$KEYS:/keys.tar.gpg:ro" -v "$OUT:/release" \
        etesian-rom-builder bash -eo pipefail -c "$1"
}

# Upstream, then ours. The last run's patches are undone first so repo sync can move every project.
run '
cd /src
ccache -M 60G >/dev/null
repo forall -c "git reset -q --hard && git clean -fdq"
repo sync -c -j8 --no-tags --no-clone-bundle --optimized-fetch --force-sync
# Never the N9 reference files or old snapshots. Etesian Mail is built outside the ROM (mail/build-mail.sh): the copy
# already in the tree stays.
rsync -a --delete --exclude .git --exclude node_modules --exclude /n9-files --exclude /backups --exclude /notes --exclude /local --exclude /release \
    --filter "P /mail/EtesianMail.apk" --filter "P /mail/Android.bp" /etesian/ vendor/etesian/
bash vendor/etesian/patches/apply.sh
source build/envsetup.sh >/dev/null
breakfast pro1x >/dev/null
# LineageOS copies TARGET_BOOTANIMATION without tracking it as an input: drop the copy so a changed boot animation is picked up.
rm -f out/soong/.intermediates/vendor/lineage/bootanimation/gen-bootanimation.zip/gen/bootanimation.zip \
      out/soong/.intermediates/vendor/lineage/bootanimation/bootanimation.zip/android_common/bootanimation.zip
m -j$(nproc) target-files-package otatools
'

[ -z "${BUILD_ONLY:-}" ] || { echo "built (BUILD_ONLY: not signed)"; exit 0; }

# Signing: the keys exist only in the container's memory (/keys, a tmpfs) and go with it.
printf '%s\n' "$PASS" | run '
IFS= read -r PASS
printf %s "$PASS" | gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 0 -d /keys.tar.gpg | tar -x -C /keys
unset PASS
cd /src
export PATH=/src/out/host/linux-x86/bin:/src/prebuilts/jdk/jdk21/linux-x86/bin:$PATH   # the signing tools run java (the JDK in the tree)
TF=$(ls -t out/target/product/pro1x/obj/PACKAGING/target_files_intermediates/*-target_files*.zip | head -1)
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
    "/release/etesian-$VERSION-pro1x.zip"
cd /release && sha256sum "etesian-$VERSION-pro1x.zip" > "etesian-$VERSION-pro1x.zip.sha256"
'
ls -l "$OUT"

# SPDX-License-Identifier: AGPL-3.0-only
# The OnePlus 7 Pro 5G (guacamoleg), for ci/release.sh. Its device tree is github.com/memphisx/android_device_oneplus_guacamoleg
# (device.xml); that tree's manifest/guacamoleg.xml adds the OnePlus 7 Pro trees and blobs it builds on, and its patches/ the
# changes it needs in them (kernel, sm8150-common).
# The 5G model's firmware (vendor/oneplus/guacamoleg) comes from a OnePlus OTA and is never redistributed: it is made once in the
# DeskMini's tree with the device tree's extract-radio.sh and stays there, as the release keys stay on the host.
LUNCH="lunch lineage_guacamoleg-cp2a-userdebug"
MANIFEST=device.xml
PREPARE='
# The device tree'"'"'s own manifest, synced again when it changed (a first run, or a new dependency).
if ! cmp -s device/oneplus/guacamoleg/manifest/guacamoleg.xml .repo/local_manifests/guacamoleg.xml; then
    cp device/oneplus/guacamoleg/manifest/guacamoleg.xml .repo/local_manifests/guacamoleg.xml
    repo sync -d -c -j8 --no-tags --no-clone-bundle --optimized-fetch --force-sync
fi
[ -d vendor/oneplus/guacamoleg/radio ] || {
    echo "no 5G firmware in vendor/oneplus/guacamoleg: run device/oneplus/guacamoleg/extract-radio.sh once (ci/guacamoleg/device.sh)" >&2; exit 1; }
device/oneplus/guacamoleg/patches/apply.sh /src
# Upstream clash (2026-11-21): sepolicy_vndr 526280b44 labels the LSM Listen wakeup node that hardware/oplus already labels the
# same way, and checkpolicy refuses the duplicate. The hardware/oplus copy goes until upstream drops one of them.
sed -i "/q6core-audio:sound\/Listen /d" hardware/oplus/sepolicy/qti/vendor/genfs_contexts
'

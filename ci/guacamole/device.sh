# SPDX-License-Identifier: AGPL-3.0-only
# The OnePlus 7 Pro (guacamole), for ci/release.sh: LineageOS's own device, as LineageOS builds it. It is the 5G model
# (ci/guacamoleg) without its modem, so it builds in that phone's tree, which already holds its trees and blobs
# (device/oneplus/guacamoleg's manifest brings them); none of the 5G model's patches or firmware are applied here.
# Not tested on a phone yet: its releases say so.
TREE=guacamoleg
LUNCH="lunch lineage_guacamole-cp2a-userdebug"
MANIFEST=device.xml
PREPARE='
if ! cmp -s device/oneplus/guacamoleg/manifest/guacamoleg.xml .repo/local_manifests/guacamoleg.xml; then
    cp device/oneplus/guacamoleg/manifest/guacamoleg.xml .repo/local_manifests/guacamoleg.xml
    repo sync -d -c -j8 --no-tags --no-clone-bundle --optimized-fetch --force-sync
fi
# The same upstream clash as the 5G model (ci/guacamoleg/device.sh): sepolicy_vndr and hardware/oplus label the LSM Listen
# wakeup node twice.
sed -i "/q6core-audio:sound\/Listen /d" hardware/oplus/sepolicy/qti/vendor/genfs_contexts
'

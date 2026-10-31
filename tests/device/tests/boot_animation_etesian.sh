# Etesian rename 2026-10-28: the boot animation is ours (ETESIAN wordmark, Fira Sans Medium), never the N9 one.
Z=~/android/lineage/vendor/etesian/bootanimation/bootanimation.zip
check "etesian.mk uses the Etesian boot animation" 'grep -q "^TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation.zip" ~/android/lineage/vendor/etesian/etesian.mk'
dev=$(sh_ md5sum /product/media/bootanimation.zip | cut -c1-32)
check "phone has the Etesian boot animation" '[ "$dev" = "$(md5sum < $Z | cut -c1-32)" ]'
# As on the N9: part0 (slow pulse) loops until boot completes, then part1 (the ripple) plays once with
# the startup sound. Regression 2026-10-28: the first Etesian build shipped without the sound.
check "part0 loops while booting, part1 plays once when booted" 'unzip -p $Z desc.txt | grep -qx "p 0 0 part0" && unzip -p $Z desc.txt | grep -qx "c 1 0 part1"'
check "startup sound is in part1" 'unzip -l $Z | grep -q " part1/audio.wav$"'
check "and not in part0 (it plays after boot, not during)" '! unzip -l $Z | grep -q " part0/audio.wav$"'

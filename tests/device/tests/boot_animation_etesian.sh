# Etesian rename 2026-10-28: the boot animation is ours (ETESIAN wordmark, Fira Sans Medium), never the N9 one.
# Drawn at the screen's size (2026-11-18: the 1080x2160 one sat in a box with black bars on the 1440x3120 phone).
SW=$(raw_sh wm size | grep -o '[0-9]*x[0-9]*' | head -1)
Z=~/android/lineage/vendor/etesian/bootanimation/bootanimation-$SW.zip; [ $SW = 1080x2160 ] && Z=${Z%-*}.zip
check "etesian.mk uses the Etesian boot animation, per screen size" 'grep -q "TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation.zip" ~/android/lineage/vendor/etesian/etesian.mk && grep -q "TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation-1440x3120.zip" ~/android/lineage/vendor/etesian/etesian.mk'
check "the phone's ($SW) animation is drawn at its size" '[ -f $Z ] && unzip -p $Z desc.txt | head -1 | grep -qx "${SW/x/ } 24" && unzip -p $Z part1/00050.jpg | python3 -c "import sys,io;from PIL import Image;print(Image.open(io.BytesIO(sys.stdin.buffer.read())).size)" | grep -q "(${SW/x/, })"'
dev=$(sh_ md5sum /product/media/bootanimation.zip | cut -c1-32)
check "phone has the Etesian boot animation" '[ "$dev" = "$(md5sum < $Z | cut -c1-32)" ]'
# As on the N9: part0 (slow pulse) loops until boot completes, then part1 (the ripple) plays once with
# the startup sound. Regression 2026-10-28: the first Etesian build shipped without the sound.
check "part0 loops while booting, part1 plays once when booted" 'unzip -p $Z desc.txt | grep -qx "p 0 0 part0" && unzip -p $Z desc.txt | grep -qx "c 1 0 part1"'
check "startup sound is in part1" 'unzip -l $Z | grep -q " part1/audio.wav$"'
check "and not in part0 (it plays after boot, not during)" '! unzip -l $Z | grep -q " part0/audio.wav$"'

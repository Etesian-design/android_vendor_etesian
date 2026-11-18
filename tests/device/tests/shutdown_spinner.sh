# The shutdown/reboot screen's busy indicator runs round the N9 squircle (2026-11-18, user). Reboots the phone.
F=/home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/globalactions
check "ShutdownUi uses the squircle spinner" 'grep -q "new EtesianSquircleSpinner(" $F/ShutdownUi.java && grep -q "Math.pow(Math.abs(c), 2 / N)" $F/EtesianSquircleSpinner.java'
check "kept in the frameworks_base patch" 'grep -q "EtesianSquircleSpinner.java" ~/android/lineage/vendor/etesian/patches/frameworks_base/*.patch'
unlock
raw_sh svc power reboot >/dev/null 2>&1 &
sleep 0.9
# Frames back to back while the phone still answers; the spinner moves between them.
for i in 1 2 3 4; do command adb exec-out screencap -p > "$SHOTS/shutdown_spinner$i.png" 2>/dev/null; done
cp "$SHOTS/shutdown_spinner1.png" "$SHOTS/shutdown_spinner.png"
wait
check "screen captured while shutting down" '[ -s "$SHOTS/shutdown_spinner.png" ]'
check "the spinner is drawn and moving (frames differ near the centre)" '/home/memphisx/android/.venv-tools/bin/python -c "
import sys
from PIL import Image, ImageChops
ims=[]
for i in range(1,5):
    try: ims.append(Image.open(\"$SHOTS/shutdown_spinner%d.png\" % i).convert(\"L\"))
    except Exception: pass
w,h=ims[0].size; box=(w//2-200,h//3,w//2+200,h*2//3)
sys.exit(0 if len(ims)>1 and any(ImageChops.difference(ims[0].crop(box),x.crop(box)).getbbox() for x in ims[1:]) else 1)"'
command adb wait-for-device; until [ "$(raw_sh getprop sys.boot_completed)" = 1 ]; do sleep 3; done; sleep 5
command adb root >/dev/null 2>&1; sleep 3; unlock

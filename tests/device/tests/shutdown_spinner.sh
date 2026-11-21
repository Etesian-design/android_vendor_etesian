# The shutdown/reboot screen's busy indicator runs round the N9 squircle (2026-11-18, user). Reboots the phone.
F=/home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/globalactions
check "ShutdownUi uses the squircle spinner" 'grep -q "new EtesianSquircleSpinner(" $F/ShutdownUi.java && grep -q "Math.pow(Math.abs(c), 2 / N)" $F/EtesianSquircleSpinner.java'
check "kept in the frameworks_base patch" 'grep -q "EtesianSquircleSpinner.java" ~/android/lineage/vendor/etesian/patches/frameworks_base/*.patch'
unlock
# Frames back to back, taken on the phone itself (a quick phone, the Pro1X, drops adb before a second frame would
# come over the cable) into /data, which outlasts the reboot; pulled once it is back.
raw_sh "rm -f /data/local/tmp/spin*"
# Raw frames (no PNG encoding: the phone is busy shutting down), one after another from the start.
raw_sh "nohup sh -c 'for i in 1 2 3 4 5 6 7 8 9 10; do screencap /data/local/tmp/spin\$i.raw; done' >/dev/null 2>&1 &"
raw_sh svc power reboot >/dev/null 2>&1 &
wait
command adb wait-for-device; until [ "$(raw_sh getprop sys.boot_completed)" = 1 ]; do sleep 3; done; sleep 5
for _ in 1 2 3 4 5 6 7 8 9 10; do command adb root >/dev/null 2>&1; sleep 3; command adb wait-for-device
  [ "$(raw_sh whoami)" = root ] && break; done
# Frames with something drawn (the first ones may still show the app), as PNGs; the spinner's own.
rm -f "$SHOTS"/shutdown_spinner*.png; n=0
for i in 1 2 3 4 5 6 7 8 9 10; do command adb pull /data/local/tmp/spin$i.raw "$SHOTS/spin$i.raw" >/dev/null 2>&1 || continue
  /home/memphisx/android/.venv-tools/bin/python -c "
import sys,struct
from PIL import Image
b=open(sys.argv[1],'rb').read(); w,h,f=struct.unpack('<III',b[:12]); off=len(b)-w*h*4
Image.frombytes('RGBA',(w,h),b[off:off+w*h*4]).convert('RGB').save(sys.argv[2])" "$SHOTS/spin$i.raw" "$SHOTS/shutdown_spinner$((n + 1)).png" && n=$((n + 1))
  rm -f "$SHOTS/spin$i.raw"; done
raw_sh "rm -f /data/local/tmp/spin*"
cp "$SHOTS/shutdown_spinner1.png" "$SHOTS/shutdown_spinner.png" 2>/dev/null
check "screen captured while shutting down" '[ -s "$SHOTS/shutdown_spinner.png" ]'
check "the spinner is drawn and moving (frames differ near the centre)" '/home/memphisx/android/.venv-tools/bin/python -c "
import sys
from PIL import Image, ImageChops
ims=[]
for i in range(1,11):
    try: ims.append(Image.open(\"$SHOTS/shutdown_spinner%d.png\" % i).convert(\"L\"))
    except Exception: pass
from PIL import ImageStat
ims=[x for x in ims if ImageStat.Stat(x).mean[0] < 20]  # only the shutdown screen: black but for the text and spinner
if len(ims) < 2: sys.exit(1)
w,h=ims[0].size; box=(w//2-200,h//3,w//2+200,h*2//3)
sys.exit(0 if any(ImageChops.difference(ims[0].crop(box),x.crop(box)).getbbox() for x in ims[1:]) else 1)"'
unlock

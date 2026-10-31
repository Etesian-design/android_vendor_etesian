# Bug 2026-10-25 #5: halfway through the unlock slide home was pure black, and the lock screen's
# status bar stayed put. Like an app swiped to the background, home must come out of the dark
# with the slide, and the whole lock screen (status bar too) must move as one.
unlock; home
for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done
lock; sh_ input keyevent WAKEUP; sleep 1.5
sh_ input motionevent DOWN 1000 1000; for x in 900 800 700 600 500; do sh_ input motionevent MOVE $x 1000; sleep 0.08; done; sleep 0.6
read bright status <<<"$(python3 - <<'PY'
import subprocess, io
from PIL import Image
im = Image.open(io.BytesIO(subprocess.run(['adb','exec-out','screencap','-p'], capture_output=True).stdout)).convert('L')
# Right half, where home is exposed: its icons must show (brightest pixel).
bright = max(im.crop((620, 100, 1080, 1800)).getdata())
# Where the lock screen's status icons were (top-right): they must have moved off with it.
status = max(im.crop((900, 0, 1080, 50)).getdata())
print(bright, status)
PY
)"
sh_ input motionevent UP 450 1000; sleep 2.5
check "home shows behind the half-slid lock screen (brightest $bright)" '[ $bright -gt 90 ]'
check "lock screen status bar slid away with it (top-right $status)" '[ $status -lt 90 ]'
# It was intermittent (the bar's controller reset its shift on every alpha update): repeat the slide.
stay=0; for _ in 1 2 3 4; do unlock; home; lock; sh_ input keyevent WAKEUP; sleep 1.5
  sh_ input motionevent DOWN 1000 1000; for x in 900 800 700 600 500; do sh_ input motionevent MOVE $x 1000; sleep 0.08; done; sleep 0.6
  s=$(python3 -W ignore -c "
import subprocess, io
from PIL import Image
im=Image.open(io.BytesIO(subprocess.run(['adb','exec-out','screencap','-p'],capture_output=True).stdout)).convert('L'); print(max(im.crop((900,0,1080,50)).getdata()))")
  sh_ input motionevent UP 450 1000; sleep 2.5; [ "$s" -lt 90 ] || stay=$((stay+1)); done
check "status bar slides every time ($stay/4 stayed)" '[ $stay -eq 0 ]'
check "KeyguardStatusBarViewController only undoes its own shift" 'grep -q mSystemEventTranslated ~/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/phone/KeyguardStatusBarViewController.java'
home

# Bug 2026-10-25 #12: an application being swiped to the background lost its rounded corners.
# Mid-swipe, the top-left corner of the moving (light) window must be cut round, i.e. dark.
unlock; home
sh_ am start -n com.android.contacts/.activities.PeopleActivity >/dev/null; sleep 2.5
sh_ input motionevent DOWN 2 1100; for x in 100 250 400; do sh_ input motionevent MOVE $x 1100; sleep 0.1; done; sleep 0.4
left=$(python3 - <<'PY'
import subprocess, io
from PIL import Image
im = Image.open(io.BytesIO(subprocess.run(['adb','exec-out','screencap','-p'], capture_output=True).stdout)).convert('L')
# The window's left edge: first column from the left where row 1100 turns light.
print(next((x for x in range(0, 1080) if im.getpixel((x, 1100)) > 150), -1))
im.save('/tmp/hd-drag.png')
PY
)
corner=$(python3 -c "from PIL import Image; print(Image.open('/tmp/hd-drag.png').convert('L').getpixel(($left+3, 3)))")
edge=$(python3 -c "from PIL import Image; print(Image.open('/tmp/hd-drag.png').convert('L').getpixel(($left+60, 300)))")
sh_ input motionevent UP 400 1100; sleep 2
check "dragged window has round corners (edge x=$left, corner $corner, body $edge)" '[ $left -gt 50 ] && [ $corner -lt 80 ] && [ $edge -gt 150 ]'
home

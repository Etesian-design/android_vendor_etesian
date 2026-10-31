# Contact avatars and icon tiles are N9 squircles (n = 2.8), never circles: the recipient chip
# (libchips, used by Messaging; Etar's copy), settingslib's user avatar, Settings' modes contact
# photos / icon tiles (bug 2026-10-25 follow-up). Content (album art, photos) stays square.
R=/home/memphisx/android/lineage
for f in frameworks/opt/chips/src/com/android/ex/chips/RecipientEditTextView.java \
         frameworks/opt/chips/src/com/android/ex/chips/CircularImageView.java \
         packages/apps/Etar/app/src/main/java/com/android/calendar/chips/RecipientEditTextView.java \
         packages/apps/Etar/app/src/main/java/com/android/calendar/chips/CircularImageView.java \
         packages/apps/Settings/src/com/android/settings/notification/modes/IconUtil.java; do
  check "no circles: ${f##*/} (source)" "[ -f $R/$f ] && ! grep -q 'drawCircle\|OvalShape' $R/$f"
done
f=$R/frameworks/base/packages/SettingsLib/src/com/android/settingslib/drawable/CircleFramedDrawable.java
check "user avatar is a squircle (source)" "[ -f $f ] && grep -q '2 / 2.8' $f && ! grep -q 'addArc(circleRect' $f"
f=$R/packages/apps/Settings/src/com/android/settings/notification/modes/ZenHelperBackend.java
check "modes contact photos not circular (source)" "[ -f $f ] && ! grep -q 'setCircular(true)' $f"
# On the phone: the chip avatar for a test number is a squircle. At the avatar's 45° diagonal
# a circle reaches 1.0 of the radius, an n=2.8 squircle ~1.10.
PY=${PY:-/home/memphisx/android/.venv-tools/bin/python}
M=com.android.messaging
unlock; sh_ am force-stop $M
sh_ am start -a android.intent.action.SENDTO -d smsto: >/dev/null; sleep 3
if top_activity | grep -q $M; then
  sh_ input text 5550100; sleep 1; sh_ input keyevent ENTER; sleep 2.5
  adb exec-out screencap -p > /tmp/sq-chip.png
  r=$($PY - <<'PYEOF'
from PIL import Image
im = Image.open('/tmp/sq-chip.png').convert('RGB')
# The avatar: the leftmost non-blue, non-background run in the To: band (rows 190..290).
def isav(p): return not (p[2] > 170 and p[0] < 60) and max(p) - min(p) > 60
xs = [x for x in range(90, 500) if isav(im.getpixel((x, 236)))]
if not xs: print('none'); raise SystemExit
x0 = xs[0]; x1 = x0
while x1 + 1 < 500 and isav(im.getpixel((x1 + 1, 236))): x1 += 1
ys = [y for y in range(180, 300) if isav(im.getpixel(((x0 + x1) // 2, y)))]
y0, y1 = ys[0], ys[-1]; r = (x1 - x0) / 2; cx = (x0 + x1) / 2; cy = (y0 + y1) / 2
# Walk from the centre to the top-left corner; the last avatar pixel's distance, as a fraction.
d = 0
for i in range(0, int(r * 1.42)):
    p = im.getpixel((int(cx - i * 0.7071), int(cy - i * 0.7071)))
    if isav(p): d = i
print(round(d / r, 2))
PYEOF
)
  check "chip avatar is a squircle (diagonal reach $r of radius; circle 1.0, squircle ~1.10)" "[ \"$r\" != none ] && $PY -c 'import sys; sys.exit(0 if 1.05 <= float(\"$r\") <= 1.2 else 1)'"
  for _ in 1 2 3; do sh_ input keyevent BACK; sleep 1; done
  sh_ am force-stop $M
fi
check "messaging did not crash" '! crashed com.android.messaging'
home

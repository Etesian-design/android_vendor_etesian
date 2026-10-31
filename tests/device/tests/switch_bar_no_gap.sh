# Bug 2026-10-25 #7: switching to an app, the bar vanished for a frame at the end of the zoom
# (WM moved it back under the app's raised transition layer). Records the switch from Open apps
# to Messages; once the bar shows it must show in every following frame.
# Needs FFMPEG (an ffmpeg binary) and python3 with Pillow (PY, default python3).
[ -x "${FFMPEG:-}" ] || { echo "  skip (set FFMPEG)"; return 0; }
PY=${PY:-python3}
unlock
sh_ am start -n com.android.messaging/.ui.conversationlist.ConversationListActivity >/dev/null; sleep 2
home; sleep 1
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
d=$(mktemp -d -p "$PWD" rec.XXXX)
sh_ "screenrecord --time-limit 3 --bit-rate 20000000 /data/local/tmp/r.mp4" & rp=$!
sleep 0.8; sh_ input tap 195 450; wait $rp
adb pull /data/local/tmp/r.mp4 $d/r.mp4 >/dev/null
"$FFMPEG" -loglevel error -i $d/r.mp4 -vf scale=540:-1 $d/%03d.png
gaps=$($PY - $d <<'PYEOF'
import sys, glob
from PIL import Image
seen = False; gaps = 0
for f in sorted(glob.glob(sys.argv[1] + '/*.png')):
    im = Image.open(f).convert('L'); h = im.height
    bar = im.getpixel((270, h - 15)); content = im.getpixel((270, h - 120))
    has = abs(bar - content) > 20 and bar > 0
    if has: seen = True
    elif seen and content > 0: gaps += 1
print(gaps if seen else -1)
PYEOF
)
rm -rf $d
check "bar showed during the switch" '[ "$gaps" -ge 0 ]'
check "bar never vanishes after it shows (gaps $gaps)" '[ "$gaps" -eq 0 ]'
home

# Bugs 2026-10-25 #18-#23: folders expand/collapse; long-press anywhere on the grid enters edit
# mode with the N9 bar (compact buttons, no hint); widgets need a long hold, scroll in edit mode,
# have the red ✕ top right and the same bar (Done / Add widget); settings in sub pages.
# The folder animation part needs FFMPEG (an ffmpeg binary) and python3 with Pillow (PY).
PY=${PY:-python3}
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
centre() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   '; }
apps_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 900 1200 200 1200 250; sleep 1; done; }
unlock

# -- app grid --
apps_pane
set -- $(centre 'text="Settings"'); lx=$(( ($1 + $3) / 2 )); ly=$(( ($2 + $4) / 2 ))
sh_ input swipe $lx $ly $lx $ly 1000; sleep 1.5
check "long-press on a label enters edit mode" 'dump | grep -q "text=\"New folder\""'
check "no edit-mode hint text" '! dump | grep -q "Drag to rearrange"'
set -- $(centre 'text="Done"'); w=$(( $3 - $1 ))
check "edit bar buttons are compact ($w px of 1080)" '[ "$w" -gt 0 ] && [ "$w" -lt 450 ]'
sh_ input keyevent BACK; sleep 1

# Folder expand / collapse: several in-between frames, not a jump.
if [ -x "${FFMPEG:-}" ]; then
  set -- $(centre 'text="Folder"')
  if [ -n "$1" ]; then
    fx=$(( ($1 + $3) / 2 )); fy=$(( $2 - 60 ))
    d=$(mktemp -d -p "$PWD" rec.XXXX)
    sh_ "screenrecord --time-limit 3 --bit-rate 20000000 /data/local/tmp/r.mp4" & rp=$!
    sleep 0.8; sh_ input tap $fx $fy; sleep 1.2; sh_ input tap $fx $fy; wait $rp
    adb pull /data/local/tmp/r.mp4 $d/r.mp4 >/dev/null
    "$FFMPEG" -loglevel error -i $d/r.mp4 -vf scale=270:-1 $d/%03d.png
    steps=$($PY - $d <<'PYEOF'
import sys, glob
from PIL import Image
fs = [Image.open(f).convert('L') for f in sorted(glob.glob(sys.argv[1] + '/*.png'))]
diff = lambda a, b: sum(abs(x - y) for x, y in zip(a.getdata(), b.getdata())) / (a.width * a.height)
# Frames that change from the one before: an animation spreads the change over several.
print(sum(1 for a, b in zip(fs, fs[1:]) if diff(a, b) > 0.4))
PYEOF
)
    rm -rf $d
    check "folder opens and closes animated ($steps moving frames)" '[ "$steps" -ge 6 ]'
  fi
fi

# -- widgets --
wp=$(pref_switch home Widgets 1)  # the Widgets view is off by default: on for the test, restored at the end
widgets_pane; sleep 1
for _ in 1 2 3 4 5 6; do sh_ input swipe 540 600 540 1800 150; done; sleep 1
check "no Add widget button on a page with widgets" '! dump | grep -q "text=\"Add widget\""'
set -- $(centre 'class="android.appwidget.AppWidgetHostView"'); wx=$(( ($1 + $3) / 2 )); wy=$(( ($2 + $4) / 2 ))
sh_ input swipe $wx $wy $wx $wy 1200; sleep 1.5
check "a long hold on a widget enters edit mode" 'dump | grep -q "text=\"Add widget\""'
set -- $(centre 'content-desc="Remove widget"'); bx=$1; by=$2
check "✕ sits at the widget's top right (x $bx, widget centre $wx)" '[ "${bx:-0}" -gt "$wx" ]'
sh_ input swipe 540 1700 540 800 300; sleep 1
set -- $(centre 'content-desc="Remove widget"')
check "edit mode scrolls instead of dragging (✕ y $by -> $2)" '[ -n "$2" ] && [ "$2" != "$by" ]'
check "edit bar has Done" 'dump | grep -q "text=\"Done\""'
sh_ input keyevent BACK; sleep 1
check "Back leaves widget edit mode" '! dump | grep -q "text=\"Done\""'

# -- settings --
sh_ am start --activity-clear-task -n com.bompotis.etesian.launcher/.SettingsActivity >/dev/null; sleep 2
for t in "Home screen" "Applications" "Events and notifications"; do
  check "settings lists $t" 'dump | grep -q "text=\"$t\""'
done
# Wallpapers and System are below the fold with all the pages listed.
check "settings lists Wallpapers" 'dump | grep -q "text=\"Wallpapers &amp; backgrounds\"" || { sh_ input swipe 540 1700 540 1100 300; sleep 1; dump | grep -q "text=\"Wallpapers &amp; backgrounds\""; }'
for _ in 1 2; do sh_ input swipe 540 600 540 1700 200; done; sleep 1
check "settings lists System" '( for _ in 1 2 3 4; do dump | grep -q "text=\"System\"" && exit 0; sh_ input swipe 540 1700 540 600 300; sleep 1; done; false )'
sh_ am start -f 0x10008000 -n com.bompotis.etesian.launcher/.SettingsActivity --es section wallpapers >/dev/null; sleep 2
check "wallpaper page offers the home wallpaper mode" 'dump | grep -q "text=\"Home screen\""'
sh_ am start -f 0x10008000 -n com.bompotis.etesian.launcher/.SettingsActivity --es section events >/dev/null; sleep 2
check "events page opens (weather setting)" 'dump | grep -q "text=\"Weather location\""'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home
pref_switch home Widgets "$wp" >/dev/null; home

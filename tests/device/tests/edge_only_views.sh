# 2026-11-22 (user): the home screen's gestures can be changed, the N9 way staying the default. An edge's view
# ("After swiping an app away") can be that edge's only: it leaves the row of views and comes in from that edge, from
# an application or on the home screen (e.g. Sailfish: the bottom always brings Applications). The bottom and one side
# only, so the row always keeps views to swipe between.
unlock
# The user's settings are put back exactly at the end, from a copy (choosing them back in Settings did not restore them).
PF=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
sh_ cp $PF /data/local/tmp/edge_prefs.xml
panes() { for _ in 1 2 3 4 5 6 7 8; do echo "$(home_pane)"; sh_ input swipe 1000 1200 100 1200 300; sleep 1.2; done | sort -u | tr '\n' ' '; }
sheet() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'content-desc="pane:[a-z]*"' | tr '\n' ' '; }
pref_list gestures "From the bottom" "Applications" >/dev/null
pref_switch gestures "Only from the bottom" 1 >/dev/null
# Back out of Etesian settings already lands home, so this Home is a Home on the home screen: it brings Applications,
# now the bottom's own view, as the sheet. Back sends it away before the row is read.
home; sleep 1
sheet | grep -q "pane:apps" && { sh_ input keyevent BACK; sleep 1; }
row=$(panes)
check "Applications left the row ($row)" '! echo "$row" | grep -q "apps"'
check "the row keeps its other views" 'echo "$row" | grep -q switcher'
# Home on the home screen brings Applications (N9); as the bottom's own view it comes in as the sheet, showing its icons.
# It came in blank (black) when the pager had hidden the view, and the next swipe then shut it.
sh_ input keyevent HOME; sleep 1.5
check "Home on the home screen brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && sh_ cat /sdcard/u.xml | grep -q "text=\"Calculator\""'
sh_ input keyevent BACK; sleep 1
check "Back sends it away ($(sheet))" '! sheet | grep -q "pane:apps"'
sh_ input swipe 540 2155 540 1400 300; sleep 1
# 2026-11-24 (user): a view that had been beside the shown one kept the row's sideways offset and came in black, the
# pane named but nothing drawn: its icons must be on screen.
icons() { sh_ cat /sdcard/u.xml | grep -q 'text="Calculator"'; }
check "a swipe up from the bottom on the home screen brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && icons'
shot edge_only_apps
sh_ input keyevent BACK; sleep 1
check "Back sends it away again" '! sheet | grep -q "pane:apps"'
# 2026-11-24 (user): the view moves with the finger, in and out, as an application window does; it neither jumps in
# nor drops away on its own, and over an application it is not the old minimise first and the view afterwards.
# Held mid-swipe, the view's edge is where the finger is (bounds and screenshots are read in 1080x2160, as the finger).
pref_list gestures "From the left side" "Open applications" >/dev/null
edge_of() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep "content-desc=\"pane:$1\"" | grep -o 'bounds="\[[0-9]*,[0-9]*\]' | grep -o '[0-9]\+' | sed -n "$2p"; }
near() { [ -n "$1" ] && [ $(( $1 > $2 ? $1 - $2 : $2 - $1 )) -lt 60 ]; }
home; sheet | grep -q "pane:apps" && { sh_ input keyevent BACK; sleep 1; }
sh_ input motionevent DOWN 540 2155; sh_ input motionevent MOVE 540 2000; sh_ input motionevent MOVE 540 1700; sh_ input motionevent MOVE 540 1500; sleep 0.8
t=$(edge_of apps 2)
check "held, Applications comes up with the finger (its top $t, finger 1500)" 'near "$t" 1500'
check "held, the row behind it goes small into the dark" '[ "$(edge_of switcher 1)" -gt 0 ]'
shot edge_view_in_held
sh_ input motionevent UP 540 1500; sleep 1.2
check "let go early (a third of the way, slowly), it goes back down" '! sheet | grep -q "pane:apps"'
sh_ input swipe 540 2155 540 900 250; sleep 1.2
check "carried past half way, it stays" 'sheet | grep -q "pane:apps" && icons'
sh_ input motionevent DOWN 5 1200; sh_ input motionevent MOVE 100 1200; sh_ input motionevent MOVE 300 1200; sh_ input motionevent MOVE 450 1200; sleep 0.8
l=$(edge_of apps 1)
check "held, a swipe from the side carries Applications away with the finger (its left $l, finger 450)" 'near "$l" 450'
shot edge_view_out_held
sh_ input motionevent MOVE 900 1200; sh_ input motionevent UP 900 1200; sleep 1.2
check "carried on, it leaves for Open applications" '! sheet | grep -q "pane:apps" && [ "$(home_pane)" = switcher ]'
sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
adb exec-out screencap -p > $SHOTS/edge_app_ref.png
sh_ input motionevent DOWN 540 2155; sh_ input motionevent MOVE 540 2000; sh_ input motionevent MOVE 540 1700; sh_ input motionevent MOVE 540 1300; sleep 1
adb exec-out screencap -p > $SHOTS/edge_over_app.png
shot edge_view_over_app_held
sh_ input motionevent MOVE 540 2100; sh_ input motionevent UP 540 2140; sleep 1.5
# Above the finger the application, in shadow; below it Applications (not Open applications, not the application).
read above below <<<"$(python3 - $SHOTS/edge_app_ref.png $SHOTS/edge_over_app.png 1300 <<'PY'
import sys
from PIL import Image, ImageChops, ImageStat
a, h, y = Image.open(sys.argv[1]).convert('L'), Image.open(sys.argv[2]).convert('L'), int(sys.argv[3])
w, H = a.size
up = (0, int(y * 0.3), w, int(y * 0.85)); down = (0, y + 40, w, min(H, y + 600))
lum = lambda im, box: ImageStat.Stat(im.crop(box)).mean[0] + 0.01
print(round(lum(h, up) / lum(a, up), 2), round(ImageStat.Stat(ImageChops.difference(a.crop(down), h.crop(down))).mean[0], 1))
PY
)"
check "held over an application, it goes into shadow above the finger (light x$above)" 'python3 -c "import sys; sys.exit(0 if $above < 0.95 else 1)"'
check "and Applications is below the finger, come up with it (changed $below)" 'python3 -c "import sys; sys.exit(0 if $below > 8 else 1)"'
check "let go back at the edge, the application carries on" 'top_activity | grep -q calculator'
sh_ input swipe 540 2155 540 900 250; sleep 2
check "carried on over an application, Applications is in front, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && icons'
# 2026-11-24 (user): with Applications in from the bottom, the other edges did nothing; the user was stuck in it until
# opening an app. Every other edge sends it away and does what it does in the row (both sides: Open applications).
pref_list gestures "From the left side" "Open applications" >/dev/null; pref_list gestures "From the right side" "Open applications" >/dev/null
for side in "5 1200 900 1200" "1075 1200 180 1200"; do
  home; sheet | grep -q "pane:apps" || { sh_ input swipe 540 2155 540 1400 300; sleep 1; }
  sh_ input swipe $side 300; sleep 1.2
  check "a swipe in from the side ($side) sends Applications away to Open applications ($(sheet))" '! sheet | grep -q "pane:apps" && [ "$(home_pane)" = switcher ]'
done
sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
sh_ input swipe 540 2155 540 900 300; sleep 2
check "leaving an application from the bottom brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && icons'
sh_ input keyevent HOME; sleep 1
# One side only: with the left side's view its own, the right side's switch is off limits.
pref_list gestures "From the left side" "Events" >/dev/null; pref_switch gestures "Only from the left side" 1 >/dev/null
pref_list gestures "From the right side" "Applications" >/dev/null
sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity --es section gestures >/dev/null 2>&1; sleep 1.5
for _ in 1 2 3; do sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q 'text="Only from the right side"' && break; sh_ input swipe 540 1700 540 700 300; sleep 0.8; done
check "the right side cannot have its own view too" 'sh_ cat /sdcard/u.xml | tr ">" "\n" | awk "/text=\"Only from the right side\"/{f=1} f&&/enabled=/{print; exit}" | grep -q "enabled=\"false\""'
# Back as it was, exactly.
sh_ am force-stop $LAUNCHER; sh_ cp /data/local/tmp/edge_prefs.xml $PF
u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u $PF; sh_ chmod 660 $PF; sh_ restorecon $PF >/dev/null 2>&1
check "the user's settings are back" '[ "$(sh_ md5sum $PF | cut -d" " -f1)" = "$(sh_ md5sum /data/local/tmp/edge_prefs.xml | cut -d" " -f1)" ]'
sh_ rm /data/local/tmp/edge_prefs.xml
home; sleep 1
# The row follows the settings put back: Applications is in it unless the user's own settings give it to an edge.
if sh_ cat $PF | grep -q 'name="edge_only_[a-z]*" value="true"'; then
  check "with the user's own edge views back, the row follows them" 'panes | grep -q switcher'
else check "with it off, Applications is back in the row" 'panes | grep -q apps'; fi
check "launcher did not crash" '! crashed $LAUNCHER'

# Bug 2026-10-31: the Belle Classic analog clock, switches, toggle caps and data meter did not follow the 701's own art: rounded
# rectangles instead of squircles, a black dial instead of warm charcoal, no second hand on the small clock, a round data meter.
# Places them on an emptied Widgets view, checks the dial colour and that the small clock's second hand moves, then puts the
# user's widgets back.
R=~/android/lineage/vendor/etesian/launcher/app/src/main/res
check "drawables come from tools/belle_art.py (no rounded rectangles)" 'bash ~/android/lineage/vendor/etesian/tests/source/belle_shapes.sh | grep -q ^PASS'
check "small analog clock has a second hand" 'grep -q hand_second $R/layout/bc_analog.xml'
unlock
wp=$(pref_switch home Widgets 1)
sh_ cp /data/data/$LAUNCHER/shared_prefs/widgets.xml /data/local/tmp/wbak.xml
prefs_put() { sh_ am force-stop $LAUNCHER; sh_ cp $1 /data/data/$LAUNCHER/shared_prefs/widgets.xml
  u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u /data/data/$LAUNCHER/shared_prefs/widgets.xml
  sh_ chmod 660 /data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ restorecon /data/data/$LAUNCHER/shared_prefs/widgets.xml >/dev/null 2>&1; }
sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
prefs_put /data/local/tmp/wempty.xml
widgets_pane
for c in BelleSquareClockWidget BelleTogglesWidget BelleSwitch1Widget BelleDataUsageWidget BelleFmSwitchWidget; do
  sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls $c >/dev/null; sleep 2
done
sleep 2
adb exec-out screencap -p > /tmp/claude-1000/b701a.png; sleep 3
adb exec-out screencap -p > /tmp/claude-1000/b701b.png
read warm moved <<<"$(python3 - <<'PY'
from PIL import Image, ImageChops
a=Image.open('/tmp/claude-1000/b701a.png').convert('RGB'); b=Image.open('/tmp/claude-1000/b701b.png').convert('RGB')
W,H=a.size
# the dial's warm charcoal (#32322D-ish: red and green a little above blue, all dark)
warm=sum(1 for y in range(0,H,4) for x in range(0,W,4) if (lambda p: 30<p[0]<75 and abs(p[0]-p[1])<4 and 2<=p[0]-p[2]<=9)(a.getpixel((x,y))))
# the second hand moves between the two shots (nothing else on the Widgets view does in 3 s)
bbox=ImageChops.difference(a,b).getbbox()
print(warm, 1 if bbox else 0)
PY
)"
check "the analog dial is warm charcoal ($warm samples)" '[ "${warm:-0}" -gt 150 ]'
check "the second hand moves" '[ "$moved" = 1 ]'
prefs_put /data/local/tmp/wbak.xml
home
pref_switch home Widgets "$wp" >/dev/null; home

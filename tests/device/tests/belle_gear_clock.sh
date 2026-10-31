# Bug 2026-10-31: the Belle gear clock had no metal rim with ticks and a blue second hand; belle-ref11 has a lighter metal rim with
# 12 ticks, toothed gears and a thin white second hand. Places it on an emptied Widgets view, checks there is no Belle-blue second
# hand and that the hand moves, then puts the user's widgets back.
R=~/android/lineage/vendor/etesian/launcher/app/src/main/res
check "the gear clock uses the white gear second hand" 'grep -q bc_hand_second_gear $R/layout/bc_gearclock.xml'
check "day and date both sit in the dark round bubbles (belle-ref11)" "[ \$(grep -c bc_date_bubble \$R/layout/bc_gearclock.xml) = 2 ]"
check "the gear clock sizes the day bubble as a circle (height set with the width)" "grep -q 'setViewLayoutHeight(R.id.bc_day, date' ~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt"
unlock
wp=$(pref_switch home Widgets 1)
sh_ cp /data/data/$LAUNCHER/shared_prefs/widgets.xml /data/local/tmp/wbak.xml
prefs_put() { sh_ am force-stop $LAUNCHER; sh_ cp $1 /data/data/$LAUNCHER/shared_prefs/widgets.xml
  u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u /data/data/$LAUNCHER/shared_prefs/widgets.xml
  sh_ chmod 660 /data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ restorecon /data/data/$LAUNCHER/shared_prefs/widgets.xml >/dev/null 2>&1; }
sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
prefs_put /data/local/tmp/wempty.xml
widgets_pane
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls BelleGearClockWidget >/dev/null; sleep 4
adb exec-out screencap -p > /tmp/claude-1000/bgear-a.png; sleep 3
adb exec-out screencap -p > /tmp/claude-1000/bgear-b.png
read blue moved <<<"$(python3 - <<'PY'
from PIL import Image, ImageChops
a=Image.open('/tmp/claude-1000/bgear-a.png').convert('RGB'); b=Image.open('/tmp/claude-1000/bgear-b.png').convert('RGB')
W,H=a.size
bbox=ImageChops.difference(a.crop((0,H//6,W,H)),b.crop((0,H//6,W,H))).getbbox()
# Belle blue (#4791DC) where the hand is: the old second hand
blue=0
if bbox:
  for y in range(bbox[1],bbox[3]):
    for x in range(bbox[0],bbox[2]):
      p=b.getpixel((x,y+H//6))
      if p[2]>180 and p[0]<110 and p[2]-p[0]>90: blue+=1
print(blue, 1 if bbox else 0)
PY
)"
check "the second hand moves" "[ $moved = 1 ]"
check "the second hand is not Belle blue ($blue blue pixels)" "[ $blue -lt 20 ]"
prefs_put /data/local/tmp/wbak.xml
pref_switch home Widgets "$wp" >/dev/null; home
sh_ rm -f /data/local/tmp/wbak.xml /data/local/tmp/wempty.xml

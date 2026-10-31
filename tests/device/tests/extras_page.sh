# Etesian settings → Android extras (mock 20): one switch per non-N9 extra, Settings.Secure
# etesian_extra_<name>, all off by default; the switches write the keys.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/Extras.kt
check "Extras.kt exists" '[ -f $SRC ]'
unlock; home
# A fresh page: an earlier test may have left the settings task scrolled down (that caused "note text" to fail).
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section extras >/dev/null; sleep 2.5
shot extras_page
check "title Android extras" 'dump | grep -q "text=\"Android extras\""'
check "title shown once (no second blue heading)" '[ $(dump | grep -o "text=\"Android extras\"" | wc -l) -eq 1 ]'
check "note text" 'dump | grep -q "Things the N9 did not have. All off = the pure N9."'
# Regression: the page used to open scrolled down (a row took focus), hiding the note.
top=0; for _ in 1 2 3; do sh_ input keyevent BACK; sleep 1; sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section extras >/dev/null; sleep 2.5
  dump | grep -q "Things the N9 did not have" && top=$((top+1)); done
check "opens at the top every time ($top/3)" '[ $top -eq 3 ]'
# Off by default = an unset key reads 0 (checked in code: the device keeps the user's own choices).
E=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "extras default off (unset key = 0)" 'grep -q "getInt(ctx.contentResolver, key(name), 0) == 1" $E/Extras.kt'
check "nothing but the extras page switches an extra on" '! grep -rn "putInt([^)]*etesian_extra_\|key(name), 1)" $E --include=*.kt | grep -v "Extras.kt\|SettingsActivity.kt"'
# The switch flips the user's own value and puts it back (all extras may be on).
old=$(sh_ settings get secure etesian_extra_privacy); [ "$old" = 1 ] && was=1 || was=0
tap_text "Privacy indicators"; sleep 1
check "switch writes etesian_extra_privacy=$((1-was))" '[ "$(sh_ settings get secure etesian_extra_privacy)" = $((1-was)) ]'
[ $was = 1 ] && shot extras_off   # the switch is off now: its track is visible
tap_text "Privacy indicators"; sleep 1
check "switch writes it back to $was" '[ "$(sh_ settings get secure etesian_extra_privacy)" = $was ]'
[ $was = 0 ] && shot extras_off
check "N9 switch: off track is dark, not stock grey" '/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image
im=Image.open(\"$SHOTS/extras_off.png\").convert(\"RGB\"); w,h=im.size
px=[im.getpixel((x,y)) for x in range(w-200,w-20) for y in range(300,h-300)]
import sys; sys.exit(0 if any(r<60 and g<60 and b<60 and r>25 for r,g,b in px) else 1)"'
[ "$old" = null ] && sh_ settings delete secure etesian_extra_privacy >/dev/null
check "ghost rows extra listed and off by default" 'dump | grep -q "Keep dismissed notifications" || { sh_ input swipe 540 1700 540 500 300; sleep 1; dump | grep -q "Keep dismissed notifications"; }'
check "launcher did not crash" '! crashed $LAUNCHER'
home

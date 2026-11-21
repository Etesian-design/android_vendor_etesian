# User 2026-11-19: single hand mode. The whole screen smaller, the same shape (zoomed out: the resolution stays),
# within reach of the thumb. Its size is the user's (a slider with a live preview in Etesian settings, default 70%:
# half suits only very large phones). It always sits in a corner, never the middle; a tap in the empty space moves
# it to the corner nearest the tap; a double tap there returns to full screen. On from an app shortcut, a toolbar or Breeze button, or a Breeze gesture.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
G=com.bompotis.etesian.glance
# Real screen coordinates throughout (the library maps taps and dumps onto 1080 x 2160): raw adb.
A() { command adb "$@"; }
state() { A shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | grep -o "EtesianSingleHand active=.*" | head -1; }
bounds() { state | grep -o 'bounds=\[[-0-9]*,[-0-9]*\]\[[-0-9]*,[-0-9]*\]' | tr -c '0-9-\n' ' ' | xargs; }
raw_tap() { A shell input tap "$1" "$2"; }
set -- $(A shell wm size | grep -o '[0-9]*x[0-9]*' | head -1 | tr x ' '); SW=$1; SH=$2
unlock; home
sh_ settings delete secure etesian_single_hand_size

check "offered as a toolbar/Breeze button, a macro step and a Breeze gesture" \
  'grep -q "Single hand mode" $L/ToolbarConfig.kt && grep -q "\"singlehand\" -> SingleHand.toggle" $L/ToolbarActions.kt && grep -q "ACTION_SINGLE_HAND" $L/SettingsActivity.kt && grep -q "ACTION_SINGLE_HAND -> " $L/../../../../../../platform/java/com/bompotis/etesian/launcher/platform/EdgeGestures.kt'
check "and as an app shortcut on Etesian settings' icon" 'sh_ dumpsys shortcut | grep -q "single_hand"'

# On, from the app shortcut's activity.
sh_ am start -n $LAUNCHER/.SingleHandActivity >/dev/null; sleep 1.5
check "on ($(state))" 'state | grep -q "active=true"'
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
check "70% of the size by default, the same shape ($((r - l))x$((b - t)) of ${SW}x$SH)" '[ $(( (r - l) * 100 - SW * 70 )) -ge -400 ] && [ $(( (r - l) * 100 - SW * 70 )) -le 400 ] && [ $(( (b - t) * 100 - SH * 70 )) -ge -400 ] && [ $(( (b - t) * 100 - SH * 70 )) -le 400 ]'
check "in a corner, never the middle ($l,$t $r,$b)" '{ [ $l -le 2 ] || [ $r -ge $((SW - 2)) ]; } && { [ $t -le 2 ] || [ $b -gt $((SH * 85 / 100)) ]; }'

# The size setting applies at once while on.
sh_ settings put secure etesian_single_hand_size 55; sleep 1
set -- $(bounds); l=$1; r=$3
check "the size setting applies at once (55%: $((r - l)) of $SW)" '[ $(( (r - l) * 100 - SW * 55 )) -ge -400 ] && [ $(( (r - l) * 100 - SW * 55 )) -le 400 ]'
sh_ settings put secure etesian_single_hand_size 30; sleep 1
set -- $(bounds); l=$1; r=$3
check "and is kept between 50% and 90% (asked for 30%: $((r - l)))" '[ $(( (r - l) * 100 - SW * 50 )) -ge -400 ] && [ $(( (r - l) * 100 - SW * 50 )) -le 400 ]'
sh_ settings delete secure etesian_single_hand_size; sleep 1
check "the empty space is SystemUI's (taps there are caught)" 'sh_ dumpsys window windows | grep -q "EtesianSingleHand"'
A exec-out screencap -p > "$SHOTS/single_hand.png"

# Touch inside the small screen goes to the app at the scaled place: open Glance, tap its first story there.
sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 3
sb=$(A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml | grep -o 'content-desc="story:[^"]*"[^>]*bounds="[^"]*"' | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
set -- $(bounds); l=$1; t=$2
if [ -n "$sb" ]; then set -- $sb; raw_tap $(( l + ($1 + $3) / 4 )) $(( t + ($2 + $4) / 4 )); sleep 2.5; fi
A exec-out screencap -p > "$SHOTS/single_hand_glance.png"
check "a tap in the small screen reaches the app at that spot ($(top_activity))" 'top_activity | grep -q "$G/.ReaderActivity"'
check "and the mode stays on in another app" 'state | grep -q "active=true"'

# A tap in the empty space: to the corner nearest the tap. Beside it, high up, then beside it, low down.
set -- $(bounds); l=$1; r=$3
if [ $l -le 2 ]; then x=$(( (r + SW) / 2 )); col=1; else x=$(( l / 2 )); col=0; fi
raw_tap $x $(( SH / 6 )); sleep 1
check "a tap high up beside it moves it to that top corner ($(state))" 'state | grep -q "column=$col row=0" && [ "$(bounds | cut -d" " -f2)" -le 2 ]'
set -- $(bounds); l=$1; r=$3
if [ $l -le 2 ]; then x=$(( (r + SW) / 2 )); col=1; else x=$(( l / 2 )); col=0; fi
raw_tap $x $(( SH * 5 / 6 )); sleep 1
check "a tap low down beside it moves it to that bottom corner, never the middle ($(state))" 'state | grep -q "column=$col row=1"'
A exec-out screencap -p > "$SHOTS/single_hand_moved.png"

# A double tap on the empty space: full screen again.
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
x=$(( SW / 2 )); if [ $t -gt 2 ]; then y=$(( t / 2 )); else y=$(( (b + SH) / 2 )); fi; before=$(state)
A shell "input tap $x $y; sleep 0.12; input tap $x $y"; sleep 1.2
check "the double tap did not first move it (was: $before)" '[ "$(state | grep -o "column=[0-9]")" = "$(echo "$before" | grep -o "column=[0-9]")" ]'
check "a double tap on the empty space returns to full screen ($(state))" 'state | grep -q "active=false"'
check "and its setting is off again" '[ "$(sh_ settings get secure etesian_single_hand)" = 0 ]'
check "nothing scaled is left over (full-size bounds)" '[ "$(bounds | cut -d" " -f3)" -ge $((SW - 2)) ]'

# Locking the phone ends it.
sh_ am start -n $LAUNCHER/.SingleHandActivity >/dev/null; sleep 1.2
sh_ input keyevent SLEEP; sleep 1.5; unlock; sleep 1
check "locking the phone ends it ($(state))" 'state | grep -q "active=false"'

sh_ settings put secure etesian_single_hand 0
# The setting: Etesian settings > Gestures, a preview of the phone with the small screen, and the Size slider.
sh_ am start -n $LAUNCHER/.SettingsActivity --activity-clear-task --es section gestures >/dev/null; sleep 2
for i in 1 2 3; do u=$(A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml); echo "$u" | grep -q "Preview: singlehand" && break; A shell input swipe $((SW/2)) $((SH*3/4)) $((SW/2)) $((SH/4)) 300; sleep 1; done
A exec-out screencap -p > "$SHOTS/single_hand_setting.png"
check "Gestures has Single hand mode with a live preview" 'echo "$u" | grep -q "text=\"Single hand mode\"" && echo "$u" | grep -q "Preview: singlehand"'
check "and its Size slider" 'echo "$u" | grep -q "text=\"Size\"" && echo "$u" | grep -q "SeekBar"'
home
sh_ settings put secure etesian_single_hand 0
check "SystemUI did not crash" '! crashed com.android.systemui'
check "launcher did not crash" '! crashed $LAUNCHER'
home

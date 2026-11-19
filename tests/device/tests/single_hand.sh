# User 2026-11-19: single hand mode. The whole screen at half its size, the same shape (zoomed out: the resolution
# stays), within reach of the thumb. Tapping the empty space moves it to that side; a double tap there returns to
# full screen. On from an app shortcut, a toolbar or Breeze button, or a Breeze gesture.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
G=com.bompotis.etesian.glance
# Real screen coordinates throughout (the library maps taps and dumps onto 1080 x 2160): raw adb.
A() { command adb "$@"; }
state() { A shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | grep -o "EtesianSingleHand active=.*" | head -1; }
bounds() { state | grep -o 'bounds=\[[-0-9]*,[-0-9]*\]\[[-0-9]*,[-0-9]*\]' | tr -c '0-9-\n' ' ' | xargs; }
raw_tap() { A shell input tap "$1" "$2"; }
set -- $(A shell wm size | grep -o '[0-9]*x[0-9]*' | head -1 | tr x ' '); SW=$1; SH=$2
unlock; home

check "offered as a toolbar/Breeze button, a macro step and a Breeze gesture" \
  'grep -q "Single hand mode" $L/ToolbarConfig.kt && grep -q "\"singlehand\" -> SingleHand.toggle" $L/ToolbarActions.kt && grep -q "ACTION_SINGLE_HAND" $L/SettingsActivity.kt && grep -q "ACTION_SINGLE_HAND -> " $L/../../../../../../platform/java/com/bompotis/etesian/launcher/platform/EdgeGestures.kt'
check "and as an app shortcut on Etesian settings' icon" 'sh_ dumpsys shortcut | grep -q "single_hand"'

# On, from the app shortcut's activity.
sh_ am start -n $LAUNCHER/.SingleHandActivity >/dev/null; sleep 1.5
check "on ($(state))" 'state | grep -q "active=true"'
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
check "half the size, the same shape ($((r - l))x$((b - t)) of ${SW}x$SH)" '[ $(( (r - l) * 2 - SW )) -ge -4 ] && [ $(( (r - l) * 2 - SW )) -le 4 ] && [ $(( (b - t) * 2 - SH )) -ge -4 ] && [ $(( (b - t) * 2 - SH )) -le 4 ]'
check "at the bottom, in the middle" '[ $b -gt $((SH * 85 / 100)) ] && [ $(( l + r - SW )) -ge -4 ] && [ $(( l + r - SW )) -le 4 ]'
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

# Tap left of it: it moves left. Tap above it: it moves up.
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
raw_tap $(( l / 2 )) $(( (t + b) / 2 )); sleep 1
check "a tap on its left moves it to the left ($(state))" 'state | grep -q "column=0"'
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
raw_tap $(( (l + r) / 2 )) $(( t / 2 )); sleep 1
check "a tap above it moves it up ($(state))" 'state | grep -q "row=0" && [ "$(bounds | cut -d" " -f2)" -le 2 ]'
A exec-out screencap -p > "$SHOTS/single_hand_moved.png"

# A double tap on the empty space: full screen again.
set -- $(bounds); l=$1; t=$2; r=$3; b=$4
x=$(( (r + SW) / 2 )); y=$(( (t + b) / 2 )); before=$(state)
A shell "input tap $x $y; input tap $x $y"; sleep 1.2
check "the double tap did not first move it (was: $before)" '[ "$(state | grep -o "column=[0-9]")" = "$(echo "$before" | grep -o "column=[0-9]")" ]'
check "a double tap on the empty space returns to full screen ($(state))" 'state | grep -q "active=false"'
check "and its setting is off again" '[ "$(sh_ settings get secure etesian_single_hand)" = 0 ]'
check "nothing scaled is left over (full-size bounds)" '[ "$(bounds | cut -d" " -f3)" -ge $((SW - 2)) ]'

# Locking the phone ends it.
sh_ am start -n $LAUNCHER/.SingleHandActivity >/dev/null; sleep 1.2
sh_ input keyevent SLEEP; sleep 1.5; unlock; sleep 1
check "locking the phone ends it ($(state))" 'state | grep -q "active=false"'

sh_ settings put secure etesian_single_hand 0
check "SystemUI did not crash" '! crashed com.android.systemui'
check "launcher did not crash" '! crashed $LAUNCHER'
home

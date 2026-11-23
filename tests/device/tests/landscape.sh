# 2026-11-22 (user): landscape for every app and for the launcher. Home followed portrait only, so leaving a landscape
# app turned the screen back and its card lay sideways in Open applications.
unlock; home
A logcat -c
acc0=$(sh_ settings get system accelerometer_rotation); rot0=$(sh_ settings get system user_rotation)
rotation() { sh_ dumpsys window displays | grep -o 'mCurrentRotation=[^ ]*\|mRotation=ROTATION_[0-9]*' | head -1 | grep -o '[0-9]*$'; }
sh_ settings put system accelerometer_rotation 0; sh_ settings put system user_rotation 1; sleep 2
check "home turns to landscape ($(rotation))" '[ "$(rotation)" = 1 ] || [ "$(rotation)" = 90 ]'
# Two applications open, so Open applications has cards to measure.
for a in com.android.calculator2/.Calculator com.android.settings/.Settings; do sh_ am start -n $a >/dev/null 2>&1; sleep 2; done
# Leaving an app shows Open applications (N9); Home again on the home screen goes to Applications.
sh_ input keyevent HOME; sleep 1.5; sh_ input keyevent HOME; sleep 1.5
check "Home shows Applications ($(home_pane))" '[ "$(home_pane)" = apps ]'
cols=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep -o 'bounds="\[[0-9]*,[0-9]*\]' | grep -o ',[0-9]*' | sort | uniq -c | sort -rn | head -1 | awk '{print $1}')
check "Applications fits more icons in a row in landscape ($cols)" '[ "${cols:-0}" -ge 6 ]'
A exec-out screencap -p > "$SHOTS/landscape_apps.png"
sh_ input swipe 1900 700 300 700 300; sleep 1.5
check "a swipe to Open applications ($(home_pane))" '[ "$(home_pane)" = switcher ]'
# The cards fit: none taller than the screen (two columns of portrait-sized cards did not).
tall=$(raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; raw_sh cat /sdcard/u.xml | tr '>' '\n' | grep 'ImageView' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | paste - - - - | awk '$4-$2>1300' | wc -l)
check "Open applications shows both cards" 'raw_sh cat /sdcard/u.xml | grep -q "text=\"Settings\"" && raw_sh cat /sdcard/u.xml | grep -q "text=\"Calculator\""'
check "Open applications cards fit the landscape screen ($tall too tall)" '[ "$tall" = 0 ]'
A exec-out screencap -p > "$SHOTS/landscape_switcher.png"
# Landscape apps' snapshots stand upright: taken at the screen's own rotation, so not turned (Etesian log). Only
# Calculator's task: the others in the log may be old portrait snapshots, which a landscape home rightly turns.
ct=$(A shell am stack list | grep calculator2 | grep -o 'taskId=[0-9]*' | head -1 | cut -d= -f2)
check "Calculator's landscape snapshot is not turned on a landscape home (task $ct)" '[ -n "$ct" ] && A logcat -d -s Etesian | grep "snapshot $ct .* rotation" | tail -1 | grep -q "rotation 0"'
sh_ input keyevent HOME; sleep 1
sh_ am start -n com.android.settings/.Settings >/dev/null; sleep 2
sh_ input swipe 2 700 1800 700 300; sleep 2
check "a swipe in from the side leaves the app in landscape" 'top_activity | grep -q HomeActivity'
check "no bar over the home screen" '! sh_ dumpsys window windows | grep -A3 "EtesianBottomBar\|EtesianToolbar" | grep -q "isOnScreen=true\|mViewVisibility=0x0 "'
check "and home stays in landscape ($(rotation))" '[ "$(rotation)" = 1 ] || [ "$(rotation)" = 90 ]'
sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; A exec-out screencap -p > "$SHOTS/landscape_status.png"; sh_ input keyevent BACK; sleep 1
sh_ settings put system user_rotation "${rot0/null/0}"; sh_ settings put system accelerometer_rotation "${acc0/null/0}"; sleep 2
check "back in portrait" '[ "$(rotation)" = 0 ]'
check "launcher did not crash" '! crashed $LAUNCHER'
home

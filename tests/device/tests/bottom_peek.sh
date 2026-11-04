# Bug 2026-11-04 (user): swipe up from the bottom had a setting (minimise, back, close, nothing) and
# needed a hold to show the apps. N9, the only behaviour: the first row of applications comes up at
# the bottom and the application drops onto it and stays, until a tap on an icon opens it or a tap
# on the application goes back to it.
unlock
peek_up() { sh_ "input motionevent DOWN 540 2155; for y in \$(seq 2155 -50 1500); do input motionevent MOVE 540 \$y; done; input motionevent UP 540 1500"; sleep 1.5; }
peek_shown() { sh_ dumpsys window windows | grep -q 'Window{.* EtesianPeek}'; }
check "no setting for the bottom swipe" '! grep -rq "BOTTOM_ACTION\|Swipe up from the bottom" ~/android/lineage/vendor/etesian/launcher/app/src/main/java'
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 3
peek_up
check "the row of applications comes up" 'peek_shown'
sleep 3
check "and stays after the finger lifts" 'peek_shown'
sh_ input tap 540 700; sleep 2
check "a tap on the application goes back to it ($(top_activity))" 'top_activity | grep -q com.android.settings && ! peek_shown'
peek_up
# The first icon of the row (columns from the launcher's grid, default 4).
sh_ input tap 135 2100; sleep 3
check "a tap on an icon opens that application ($(top_activity))" '! top_activity | grep -q com.android.settings && ! peek_shown'
check "launcher did not crash" '! crashed $LAUNCHER'
home

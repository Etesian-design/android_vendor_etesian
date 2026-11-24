# 2026-11-24 (user): Android extras → Swipe down for the status menu: for people used to pulling their status down, OUR
# status menu comes down with a swipe from the top instead of a tap on the status bar, and the swipe closes nothing.
# Never Android's own shade.
unlock; home
old=$(sh_ settings get secure etesian_extra_status_swipe); oldm=$(sh_ settings get secure etesian_extra_status_menu)
shade() { sh_ dumpsys window | grep -q "mCurrentFocus=.*NotificationShade"; }
menu_up() { top_activity | grep -q StatusMenuActivity; }
sh_ settings put secure etesian_extra_status_swipe 1; sleep 1
sh_ am start -n com.android.settings/.Settings >/dev/null; sleep 2
# 2026-11-24 (user): a swipe is not a tap: the menu follows the finger down, its lower edge under it, until it lets go.
# Lowest drawn edge of the status menu's views (bounds are where they are drawn, so they follow the panel).
edge() { raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml | tr '>' '\n' | grep 'package="com.bompotis' |
  grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | paste - - - - | awk '$4-$2 < 2000 {if ($4>m) m=$4} END {print m+0}'; }
sh_ input motionevent DOWN 540 5; sh_ input motionevent MOVE 540 120; sh_ input motionevent MOVE 540 350; sleep 1.2
e1=$(edge); shot status_swipe_follow
sh_ input motionevent MOVE 540 650; sleep 0.8
e2=$(edge)
check "while held, the menu's lower edge is under the finger (350 → $e1)" '[ "$e1" -ge 300 ] && [ "$e1" -le 400 ]'
check "and comes down with it (650 → $e2)" '[ "$e2" -ge 600 ] && [ "$e2" -le 700 ]'
check "it is our status menu" 'menu_up'
sh_ input motionevent MOVE 540 120; sleep 0.8; sh_ input motionevent UP 540 120; sleep 1.5
check "pushed back up and let go, it goes away" '! menu_up'
sh_ input swipe 540 5 540 900 600; sleep 1.5
check "a full swipe down leaves it open" 'menu_up'
check "never Android's shade" '! shade'
shot status_swipe
sh_ input keyevent BACK; sleep 1.5
check "and closes nothing (Settings still open: $(top_activity))" 'top_activity | grep -q com.android.settings'
sh_ input tap 540 30; sleep 1.5
check "a tap on the status bar no longer opens it" '! menu_up'
home; sh_ input swipe 540 5 540 900 300; sleep 1.5
check "from the home screen too" 'menu_up'
sh_ input keyevent BACK; sleep 1
sh_ settings put secure etesian_extra_status_swipe 0; sleep 1
sh_ am start -n com.android.settings/.Settings >/dev/null; sleep 2
sh_ input tap 540 30; sleep 1.5
check "off: a tap opens the status menu again" 'menu_up'
sh_ input keyevent BACK; sleep 1.5
put_back etesian_extra_status_swipe "$old"; put_back etesian_extra_status_menu "$oldm"
check "launcher did not crash" '! crashed $LAUNCHER'
home

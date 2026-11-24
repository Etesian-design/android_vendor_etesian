# 2026-11-24 (user): Android extras → Swipe down for the status menu: for people used to pulling their status down, OUR
# status menu comes down with a swipe from the top instead of a tap on the status bar, and the swipe closes nothing.
# Never Android's own shade.
unlock; home
old=$(sh_ settings get secure etesian_extra_status_swipe); oldm=$(sh_ settings get secure etesian_extra_status_menu)
shade() { sh_ dumpsys window | grep -q "mCurrentFocus=.*NotificationShade"; }
menu_up() { top_activity | grep -q StatusMenuActivity; }
sh_ settings put secure etesian_extra_status_swipe 1; sleep 1
sh_ am start -n com.android.settings/.Settings >/dev/null; sleep 2
sh_ input swipe 540 5 540 900 300; sleep 1.5
check "a swipe down from the top opens Etesian's status menu" 'menu_up'
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

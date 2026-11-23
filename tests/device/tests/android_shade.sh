# 2026-11-23 (user): Android extras → Android's notification shade: the status bar works the Android way for people
# used to it: swiping down from the top opens Android's notifications (no status menu, nothing closes).
unlock; home
old=$(sh_ settings get secure etesian_extra_android_shade)
shade() { sh_ dumpsys window | grep -q "mCurrentFocus=.*NotificationShade"; }
sh_ settings put secure etesian_extra_android_shade 1; sleep 1
sh_ am start -n com.android.settings/.Settings >/dev/null; sleep 2
sh_ input swipe 540 5 540 1400 400; sleep 1.5
check "a swipe down from the top opens Android's shade" 'shade'
shot android_shade
sh_ input keyevent BACK; sleep 1
check "and closes nothing (Settings still open: $(top_activity))" 'top_activity | grep -q com.android.settings'
check "a tap on the status bar opens no status menu" '! top_activity | grep -q StatusMenuActivity'
sh_ settings put secure etesian_extra_android_shade 0; sleep 1
sh_ input swipe 540 5 540 1400 400; sleep 1.5
check "off: no Android shade" '! shade'
put_back etesian_extra_android_shade "$old"
check "launcher did not crash" '! crashed $LAUNCHER'
home

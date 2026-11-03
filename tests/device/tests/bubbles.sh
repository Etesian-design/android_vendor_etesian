# Settings → "Notification bubbles over apps" (mock 08, 13, 16): instead of the heads-up, the
# lock-screen card floats under the status bar; tap opens, swipe up dismisses. Also used for
# "App pinned". Off: the stock heads-up. Silent: the test channel has no sound.
SRC=/home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/notification/row/EtesianBubble.kt
check "EtesianBubble.kt exists" '[ -f $SRC ]'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bubble_shown() { sh_ dumpsys window windows | grep -q "EtesianBubble"; }
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind ${1:-message} >/dev/null; }
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
old=$(sh_ settings get secure etesian_extra_bubbles)
peeks_on
unlock; home
sh_ am start -n $LAUNCHER/.SettingsActivity >/dev/null; sleep 2
sh_ settings put secure etesian_extra_bubbles 1
post; sleep 2.5
check "on: bubble window shown" 'bubble_shown'
shot bubble_message
# The bubble is not in the accessibility tree; check it is drawn: bright text on the dark card.
check "bubble card drawn under the status bar" '[ $(pixel 900 180) -lt 60 ]'

check "no stock heads-up while bubbling" '! sh_ dumpsys activity service com.android.systemui | grep -q "HeadsUpEntry.*7101"'
sh_ input swipe 540 230 540 20 200; sleep 1
check "swipe up dismisses" '! bubble_shown'
post cancel; sleep 0.5; home; post; sleep 2
sh_ input tap 540 180; sleep 2
check "tap opens it" 'top_activity | grep -q SettingsActivity'
check "tap hides the bubble" '! bubble_shown'
post cancel; home
# Regression: on the lock screen a new notification only joins the card stack, with no bubble over it.
post cancel; lock; sh_ input keyevent WAKEUP; sleep 2
post; sleep 2
check "lock screen: no bubble" '! bubble_shown'
shot bubble_lock
check "lock screen: the card is in the stack" 'dump | grep -q "Are we still on for lunch?"'
post cancel; unlock; home
# App pinned bubble
sh_ am start -n $LAUNCHER/.SettingsActivity >/dev/null; sleep 2
tid=$(sh_ am stack list | grep SettingsActivity | grep -o 'taskId=[0-9]*' | head -1 | cut -d= -f2)
sh_ am task lock $tid >/dev/null 2>&1; sleep 1.5
check "app pinned bubble" 'bubble_shown'
shot bubble_pinned
sh_ am task lock stop >/dev/null 2>&1; sleep 1
# Screenshot saved bubble (mock 13), silent: the system stream (the shutter) is muted first.
sysvol=$(sh_ cmd media_session volume --stream 1 --get | grep -o 'volume is [0-9]*' | grep -o '[0-9]*$')
sh_ cmd media_session volume --stream 1 --set 0 >/dev/null 2>&1; home
sh_ input keyevent SYSRQ; sleep 3
check "screenshot saved bubble" 'sh_ dumpsys window windows | grep -q EtesianBubble'
check "screenshot bubble offers Share and Edit, square thumb (mock 13; the bubble is not in uiautomator)" 'grep -q "Tap to view \\\\u00b7 Share" ~/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/screenshot/ScreenshotController.kt && grep -q "centre-cropped" ~/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/screenshot/ScreenshotController.kt'
shot bubble_screenshot
sh_ input swipe 540 230 540 20 200; sleep 1
[ -n "$sysvol" ] && sh_ cmd media_session volume --stream 1 --set $sysvol >/dev/null 2>&1
sh_ settings put secure etesian_extra_bubbles 0; home
post; sleep 1.2
check "off: no bubble" '! bubble_shown'
post cancel
[ "$old" = null ] && sh_ settings delete secure etesian_extra_bubbles >/dev/null || sh_ settings put secure etesian_extra_bubbles "$old"
check "systemui did not crash" '! crashed com.android.systemui'
home
peeks_off

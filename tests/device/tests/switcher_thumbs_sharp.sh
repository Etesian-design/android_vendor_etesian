# Open applications previews are sharp: the launcher asks for the system's full-resolution task snapshot (not the
# half-size one, whose text broke up into noise once the card scaled it) and keeps it at least 540 px wide.
sh_ logcat -c
for a in com.android.calculator2 com.android.settings; do sh_ monkey -p $a -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4; done
home; sleep 1
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 2
# long sides: the panel may be landscape natively, so width and height swap places
w=$(sh_ logcat -d -s Etesian:D | grep -o "snapshot [0-9]* [0-9]*x[0-9]*" | awk '{split($3,d,"x"); print (d[1]>d[2]?d[1]:d[2])}' | sort -n | head -1)
full=$(sh_ wm size | grep -o "[0-9]*x[0-9]*" | head -1 | awk -Fx '{print ($1>$2?$1:$2)}')
check "previews come from full-resolution snapshots (long side ${w:-none} of $full px)" '[ -n "$w" ] && [ "$w" -ge "$full" ]'
for a in com.android.calculator2 com.android.settings; do sh_ am force-stop $a; done
check "launcher did not crash" '! crashed $LAUNCHER'
home
# An app left in landscape shows whole on its portrait card (fitted, dark bands above and below), not a cropped corner.
acc0=$(sh_ settings get system accelerometer_rotation | tr -d '\r'); rot0=$(sh_ settings get system user_rotation | tr -d '\r')
sh_ settings put system accelerometer_rotation 0; sh_ settings put system user_rotation 1
sh_ monkey -p com.android.calculator2 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
sh_ settings put system user_rotation 0; home; sleep 2
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 2
home; sleep 1  # and back: the system may still be storing the snapshot on the first visit
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 2
# the card: the image right above the "Calculator" label
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="Calculator"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1)
set -- $(echo "$b" | grep -o '[0-9]\+')
lx=$(( ($1+$3)/2 )); ly=$2
top=$(pixel $lx $(( ly - 800 ))); mid=$(pixel $lx $(( ly - 500 )))  # the card ends just above its label
shot switcher_landscape_card
check "a landscape app's card is fitted whole (band $top, middle $mid)" '[ -n "$top" ] && [ "$top" -lt 60 ] && [ "$mid" -gt 150 ]'
sh_ am force-stop com.android.calculator2
sh_ settings put system user_rotation "$rot0"; sh_ settings put system accelerometer_rotation "$acc0"
home

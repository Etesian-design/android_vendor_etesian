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
# An app left in landscape (phone turned, keyboard slid out) is turned back on its portrait card, as Launcher3 does:
# the preview is portrait and fills the card; before, the card showed the top-left corner of the landscape picture.
acc0=$(sh_ settings get system accelerometer_rotation | tr -d '\r'); rot0=$(sh_ settings get system user_rotation | tr -d '\r')
# simulated: only `wm user-rotation lock` turns the display (writing the settings leaves rotation free)
sh_ wm user-rotation lock 1
sh_ monkey -p com.android.calculator2 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
home; sleep 2
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 2
home; sleep 1  # and back: the system may still be storing the snapshot on the first visit
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 2
# the card: the image right above the "Calculator" label
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="Calculator"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1)
set -- $(echo "$b" | grep -o '[0-9]\+')
lx=$(( ($1+$3)/2 )); ly=$2
top=$(pixel $lx $(( ly - 800 ))); mid=$(pixel $lx $(( ly - 500 )))  # the card ends just above its label
shot switcher_landscape_card
line=$(sh_ logcat -d -s Etesian:D | grep -o "snapshot [0-9]* [0-9]*x[0-9]* rotation [0-9]" | tail -1); last=$(echo "$line" | awk '{print $3}')
check "the landscape app's preview is portrait ($line)" '[ -n "$last" ] && [ "${last%x*}" -lt "${last#*x}" ]'
check "and fills its card, no bands (top $top, middle $mid)" '[ -n "$top" ] && [ "$top" -gt 150 ] && [ "$mid" -gt 150 ]'
# A locked rotation makes Android lay the app out portrait again on the way home, so its snapshot is not turned.
# Only a phone held sideways (or with the keyboard out) keeps it turned; then the preview above was turned back.
echo "$line" | grep -q "rotation [13]" || echo "  note snapshot not turned in this run (simulated rotation): hold the phone sideways to cover turning back"
sh_ am force-stop com.android.calculator2
[ "$acc0" = 1 ] && sh_ wm user-rotation free || sh_ wm user-rotation lock "${rot0:-0}"
home

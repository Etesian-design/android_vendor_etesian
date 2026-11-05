# Settings → Events and notifications → Over apps (2026-11-05, story Phone/Notifications over apps): the style of a
# notification while an app is open. Discreet (N9, the default): one line over the status bar that scrolls to the end
# of the text and goes by itself; system notices are a small inset banner. Strip (Belle): full width over the status
# bar, transparency a setting. Colour: a bubble with squircle-half ends in the app's colour, the system accent or a
# fixed colour. The lock screen is never affected. Driven over a white page in the browser; silent test channel.
J=org.lineageos.jelly; PORT=6121; D="$(cd "$(dirname "${BASH_SOURCE[0]}")/../data" && pwd)"
python3 -m http.server $PORT --bind 127.0.0.1 --directory "$D" >/dev/null 2>&1 & SRV=$!
adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
post() { sh_ "am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind ${1:-message} --es text '${2:-Are we still on for lunch, I can be there by one or later if that suits you better}'" >/dev/null; }
bubble_shown() { sh_ dumpsys window windows | awk '/^  Window #/{f=/EtesianBubble/} f && /mViewVisibility=0x0/{v=1} END{exit !v}'; }
# The bubble window's frame: "left top right bottom"
frame() { sh_ dumpsys window windows | awk '/^  Window #/{f=/EtesianBubble/; v=0} f && /mViewVisibility=0x0/{v=1} f && v && /frame=\[/{print; exit}' | grep -o 'frame=\[[0-9-]*,[0-9-]*\]\[[0-9-]*,[0-9-]*\]' | grep -o '[0-9-]\+' | tr '\n' ' '; }
rgb() { adb exec-out screencap -p > /tmp/hd-px.png; python3 -c "from PIL import Image; print(*Image.open('/tmp/hd-px.png').convert('RGB').getpixel(($1,$2)))"; }
sb=$(sh_ dumpsys window windows | awk '/Window\{.*StatusBar\}/{f=1} f && /frame=\[/{print; exit}' | grep -o 'frame=\[[0-9-]*,[0-9-]*\]\[[0-9-]*,[0-9-]*\]' | grep -o '[0-9-]\+' | sed -n 4p)
old=$(sh_ settings get secure etesian_notif_over); olds=$(sh_ settings get secure etesian_notif_belle_shade); oldc=$(sh_ settings get secure etesian_notif_colour)
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
peeks_on; unlock
page() { sh_ am force-stop $J; sh_ am start -a android.intent.action.VIEW -d "http://127.0.0.1:$PORT/long_page.html" -n $J/.MainActivity >/dev/null; sleep 4; }
put() { sh_ "settings put secure $1 '$2'"; }

# Discreet (N9) is the default
sh_ settings delete secure etesian_notif_over >/dev/null
page; post cancel; post; sleep 2
check "default is Discreet (N9): the line is shown" 'bubble_shown'
read fl ft fr fb <<< "$(frame)"; echo "    frame: $fl $ft $fr $fb"; shot over_n9_line
check "N9: the line covers the status bar (top 0, its height ${sb}px)" '[ "$ft" = 0 ] && [ $(( fb - ft )) -le $(( sb + 4 )) ]'
check "N9: the line is black over the status bar, the page white below" '[ $(pixel 700 $(( sb / 2 ))) -lt 40 ] && [ $(pixel 700 $(( sb + 200 ))) -gt 200 ]'
sleep 3; shot over_n9_scrolled
for _ in $(seq 20); do bubble_shown || break; sleep 1; done
check "N9: the line goes by itself once the text has scrolled" '! bubble_shown'
check "N9: the notification is still there for Events" 'sh_ dumpsys notification --noredact | grep -q "Are we still on for lunch, I can"'
post cancel

# N9 system notice: App pinned shows as the small inset banner under the status bar
tid=$(sh_ am stack list | grep -i jelly | grep -o 'taskId=[0-9]*' | head -1 | cut -d= -f2)
sh_ am task lock $tid >/dev/null 2>&1; sleep 1.5
read fl ft fr fb <<< "$(frame)"; echo "    frame: $fl $ft $fr $fb"; shot over_n9_notice
check "N9: a system notice is an inset banner under the status bar" '[ "$ft" -ge "$sb" ]'
sh_ am task lock stop >/dev/null 2>&1; sleep 1

# Strip (Belle): full width over the status bar; Solid is black, Light lets the white page through
put etesian_notif_over belle; put etesian_notif_belle_shade solid
page; post; sleep 1.5
read fl ft fr fb <<< "$(frame)"; echo "    frame: $fl $ft $fr $fb"; shot over_belle_solid
check "Belle: the strip starts at the top, over the status bar, full width" '[ "$fl" = 0 ] && [ "$ft" = 0 ] && [ "$fr" -ge 1070 ] && [ $(( fb - ft )) -gt $(( sb + 20 )) ]'
solid=$(pixel 1040 $(( (ft + fb) / 2 )))
sh_ input swipe 540 150 540 0 150; sleep 1; post cancel
put etesian_notif_belle_shade light; post; sleep 1.5
read fl ft fr fb <<< "$(frame)"; echo "    frame: $fl $ft $fr $fb"; shot over_belle_light
light=$(pixel 1040 $(( (ft + fb) / 2 )))
check "Belle: transparency follows the setting (solid $solid, light $light)" '[ $solid -lt 15 ] && [ $light -gt $(( solid + 40 )) ]'
sh_ input swipe 540 150 540 0 150; sleep 1; post cancel

# Colour: a fixed green, inset under the status bar
put etesian_notif_over colour; put etesian_notif_colour "#3FA34D"
post; sleep 1.5
read fl ft fr fb <<< "$(frame)"; echo "    frame: $fl $ft $fr $fb"; shot over_colour
mid=$(( (ft + fb) / 2 ))
check "Colour: inset under the status bar" '[ "$ft" -ge "$sb" ]'
check "Colour: the bubble is the chosen green" 'read r g b <<< "$(rgb 980 $mid)"; [ $g -gt $r ] && [ $g -gt $b ] && [ $g -gt 100 ]'
sh_ input tap 540 $mid; sleep 2
check "Colour: tap opens it" '! bubble_shown && top_activity | grep -q SettingsActivity'
post cancel

# The lock screen is never affected, whatever the style
for s in n9 belle colour card; do
  put etesian_notif_over $s; lock; sh_ input keyevent WAKEUP; sleep 2
  post; sleep 2
  check "$s: no bubble over the lock screen" '! bubble_shown'
  post cancel; unlock
done

# Settings: the choice and its options, transparency only for Belle, colour only for Colour
put etesian_notif_over n9
sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity --es section events >/dev/null 2>&1; sleep 1.5
for _ in 1 2 3; do dump | grep -q 'text="Notifications over apps"' && break; sh_ input swipe 540 1600 540 600 300; sleep 1; done
check "Settings: Notifications over apps, Discreet (N9)" 'dump | grep -q "text=\"Discreet (N9)\""'
check "Settings: no Strip transparency while Discreet" '! dump | grep -q "text=\"Strip transparency\""'
tap_text "Notifications over apps" >/dev/null; sleep 1; tap_text "Strip (Belle)" >/dev/null; sleep 1.5
shot over_settings_belle
check "Settings: choosing Strip (Belle) stores it" '[ "$(sh_ settings get secure etesian_notif_over)" = belle ]'
check "Settings: Strip transparency appears for Belle" 'dump | grep -q "text=\"Strip transparency\""'

sh_ am force-stop $J; kill $SRV 2>/dev/null; adb reverse --remove tcp:$PORT >/dev/null 2>&1
for k in "etesian_notif_over:$old" "etesian_notif_belle_shade:$olds" "etesian_notif_colour:$oldc"; do
  v=${k#*:}; [ "$v" = null ] && sh_ settings delete secure ${k%%:*} >/dev/null || sh_ settings put secure ${k%%:*} "$v"; done
check "systemui did not crash" '! crashed com.android.systemui'
peeks_off; home

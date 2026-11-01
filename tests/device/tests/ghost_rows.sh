# Settings → Keep dismissed notifications (story Phone v2 → Try it: ghost rows).
# Off by default: nothing kept. On: a dismissed notification becomes the top ghost row at the end of
# Notifications; size/rows settings; the rows are one item (tap = Earlier page, swipe in dismiss mode = all expire);
# Keep-for expiry. Silent test notifications only; the user's own notifications are never touched.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message --es title "'$1'" --es text "'$2'" >/dev/null; sleep 1.5; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
gh() { set -- $(bounds 'content-desc="Earlier notifications'); [ -n "$4" ] && echo $(( $4 - $2 )) || echo 0; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1; }
# Dismiss the test message from Events (long-press → Dismiss).
dismiss() { set -- $(bounds "text=\"$1\""); [ -n "$4" ] || return 1; local y=$(( ($2+$4)/2 )); sh_ input swipe 540 $y 540 $y 900; sleep 1.2; tap_text Dismiss; sleep 2; }
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "Ghosts.kt and EarlierActivity.kt exist" '[ -f $SRC/Ghosts.kt ] && [ -f $SRC/EarlierActivity.kt ]'
D=$(sh_ wm density | tail -1 | grep -o '[0-9]*$'); dp() { echo $(( $1 * D / 160 )); }
keys="etesian_extra_ghost_rows etesian_extra_actions etesian_ghost_size etesian_ghost_rows etesian_ghost_keep_hours"; declare -A gold
for k in $keys; do gold[$k]=$(sh_ settings get secure $k); done
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
sh_ settings put secure etesian_extra_ghost_rows 0; sh_ settings put secure etesian_extra_actions 1
sh_ settings delete secure etesian_ghost_size >/dev/null; sh_ settings delete secure etesian_ghost_rows >/dev/null; sh_ settings delete secure etesian_ghost_keep_hours >/dev/null
unlock; post "Ghost Zero" "Dismissed while off"; events_pane
dismiss "Ghost Zero"
check "off: dismissing keeps nothing" '! dump | grep -q "Earlier notifications" && ! dump | grep -q "Ghost Zero"'
sh_ settings put secure etesian_extra_ghost_rows 1; sleep 1
for i in 1 2 3 4; do post "Ghost $i" "Body $i"; events_pane; dismiss "Ghost $i"; done
shot ghost_small_25
check "on: dismissed ones kept as one item (4)" 'dump | grep -q "content-desc=\"Earlier notifications (4)\""'
check "newest dismissed is the top ghost row (Title: body)" 'dump | grep -o "text=\"Ghost [0-9]: Body [0-9]\"" | head -1 | grep -q "Ghost 4: Body 4"'
# At the end of Notifications, before the next header; with no current notifications there is no
# Notifications header at all (N9) and the ghost rows stand alone.
order=$(dump | grep -o "text=\"Ghost 4: Body 4\"\|text=\"Feed\"\|text=\"Calendar\"\|text=\"Notifications\"" | sed 's/text=//; s/"//g; s/:.*//' | tr "\n" " ")
check "ghost rows sit at the end of Notifications ($order)" 'echo "$order" | grep -q "^\(Notifications \)\?Ghost 4"'

h25=$(gh); check "small 2.5 rows: two 30dp rows + a 16dp cut row ($h25 px)" '[ $h25 -ge $(dp 70) ] && [ $h25 -le $(dp 82) ]'
check "small 2.5: three rows shown, the 4th not" 'dump | grep -q "Ghost 2: Body 2" && ! dump | grep -q "Ghost 1: Body 1"'
sh_ settings put secure etesian_ghost_rows 1; sleep 1.5
h1=$(gh); check "small 1 row ($h1 px)" '[ $h1 -ge $(dp 26) ] && [ $h1 -le $(dp 36) ] && ! dump | grep -q "Ghost 3: Body 3"'
sh_ settings put secure etesian_ghost_rows 0.5; sleep 1.5
h05=$(gh); check "0.5 is big-only: small shows 1 row ($h05 px)" '[ $h05 -eq $h1 ]'
sh_ settings put secure etesian_ghost_size big; sh_ settings put secure etesian_ghost_rows 1.5; sleep 1.5
shot ghost_big_15
hb=$(gh); check "big 1.5: a normal row + a ~40dp cut row ($hb px)" '[ $hb -ge $(dp 100) ] && [ $hb -le $(dp 130) ] && dump | grep -q "text=\"Ghost 4\""'
sh_ settings put secure etesian_ghost_rows 3; sleep 1.5
check "big 3 rows" '[ $(gh) -gt $(( hb * 3 / 2 )) ]'
sh_ settings put secure etesian_ghost_size small; sh_ settings put secure etesian_ghost_rows 2.5; sleep 1.5
set -- $(bounds 'content-desc="Earlier notifications'); sh_ input tap 540 $(( ($2+$4)/2 )); sleep 2
shot ghost_earlier
check "tap opens the Earlier page" 'dump | grep -q "text=\"Earlier\""'
check "Earlier lists all kept ones" '[ $(dump | grep -o "text=\"Ghost [1-4]\"" | sort -u | wc -l) -eq 4 ]'
tap_text "Ghost 3"; sleep 2.5
check "tapping one opens it like the original" 'top_activity | grep -q SettingsActivity'
sh_ input keyevent BACK; sleep 1
events_pane
# Outside dismiss mode a sideways swipe on the ghost rows pages the home views (N9) and keeps them.
set -- $(bounds 'content-desc="Earlier notifications'); y=$(( ($2+$4)/2 )); sh_ input swipe 900 $y 100 $y 250; sleep 2
check "swipe outside dismiss mode pages, ghosts kept" '[ "$(home_pane)" != events ]'
events_pane
check "ghosts still there after paging" 'dump | grep -q "Earlier notifications"'
# Dismiss mode (long-press): swiping the ghost rows expires them all, like any notification.
set -- $(bounds 'content-desc="Earlier notifications'); y=$(( ($2+$4)/2 )); sh_ input swipe 540 $y 540 $y 900; sleep 1.2
set -- $(bounds 'content-desc="Earlier notifications'); y=$(( ($2+$4)/2 )); sh_ input swipe 200 $y 1000 $y 250; sleep 2
check "dismiss mode: swipe on the ghost rows expires them all" '[ "$(home_pane)" = events ] && ! dump | grep -q "Earlier notifications"'
set -- $(bounds "text=\"$(LC_ALL=C date +%A)\""); sh_ input tap $(( (${1:-0}+${3:-400})/2 )) $(( (${2:-0}+${4:-400})/2 )); sleep 0.8
sh_ am start -f 0x10008000 -n $LAUNCHER/.EarlierActivity >/dev/null 2>&1; sleep 2.5
for _ in 1 2 3; do dump | grep -q "Nothing dismissed" && break; sleep 1.5; done; shot ghost_earlier_empty
check "Earlier page then empty" 'dump | grep -q "Nothing dismissed"'
sh_ input keyevent BACK; sleep 1
sh_ settings put secure etesian_ghost_keep_hours 0.0008; post "Ghost Old" "Expires"; events_pane; dismiss "Ghost Old"
check "kept right after dismissing" 'dump | grep -q "Earlier notifications (1)"'
sleep 4; events_pane; sh_ input swipe 540 900 540 1900 150; sleep 1
check "expires after Keep for" '! dump | grep -q "Earlier notifications"'
sh_ settings put secure etesian_extra_ghost_rows 0
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section dismissed >/dev/null; sleep 2.5
check "settings: the switch" 'tap_text "Keep dismissed notifications" >/dev/null; sleep 1; [ "$(sh_ settings get secure etesian_extra_ghost_rows)" = 1 ]'
sh_ settings put secure etesian_extra_ghost_rows 0
for _ in 1 2 3 4 5 6 7 8 9 10; do dump | grep -q "text=\"Keep for\"" && break; sh_ input swipe 540 1700 540 900 300; sleep 0.8; done
shot ghost_settings
check "settings: its options" 'dump | grep -q "text=\"Size\"" && dump | grep -q "text=\"Rows shown\"" && dump | grep -q "text=\"Keep for\""'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
for k in $keys; do [ "${gold[$k]}" = null ] && sh_ settings delete secure $k >/dev/null || sh_ settings put secure $k "${gold[$k]}"; done
check "launcher did not crash" '! crashed $LAUNCHER'
home

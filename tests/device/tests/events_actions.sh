# Settings → Notification actions and snooze / Inline reply / Live updates (mocks 04, 05, 17).
# Long-press an Events item: N9 menu sheet (Reply, Mark as read, Snooze, Dismiss); Reply opens
# the one-line composer; ongoing progress is pinned first under Notifications. Off: dismiss mode.
# Silent: the launcher's own test channel, no sound; the reply goes back to the launcher itself.
for f in EventsPage.kt EventsReply.kt N9Sheet.kt TestNotifyReceiver.kt; do
  check "$f exists" '[ -f /home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/$f ]'
done
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind ${1:-message} >/dev/null; }
center() { dump | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
longpress() { set -- $(center "$1"); [ -n "$4" ] && sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1; }
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
keys="actions inline_reply live_updates bubbles ghost_rows"  # ghost rows on would keep a dismissed row; declare -A old
for k in $keys; do old[$k]=$(sh_ settings get secure etesian_extra_$k); sh_ settings put secure etesian_extra_$k 0; done
sh_ settings delete global etesian_test_reply >/dev/null
unlock; post cancel; post; post progress; sleep 1.5
events_pane
check "Events pane" '[ "$(home_pane)" = events ]'
check "off: live update not listed" '! dump | grep -q "Downloading map"'
longpress "Anna Virtanen"; sleep 1
check "off: long-press = dismiss mode (no sheet)" '! dump | grep -q "content-desc=\"Events actions\""'
sh_ input keyevent BACK; sleep 1; events_pane
sh_ settings put secure etesian_extra_actions 1; sh_ settings put secure etesian_extra_inline_reply 1
sh_ settings put secure etesian_extra_live_updates 1; sleep 1.5
check "on: live update pinned under Notifications" 'dump | grep -q "Downloading map"'
check "live update has a progress bar" 'dump | grep -q "content-desc=\"Live update progress\""'
shot events_live
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2
check "live update: no status bar chip" '! dump | grep -q "Downloading map\|ongoing_activity_chip"'
shot live_no_chip
home; events_pane
longpress "Anna Virtanen"; sleep 1.2
check "on: actions sheet" 'dump | grep -q "content-desc=\"Events actions\""'
for r in Reply "Mark as read" Snooze Dismiss; do check "sheet row $r" 'dump | grep -q "text=\"$r\""'; done
check "sheet has only the mock's rows (no Edit Events)" '! dump | grep -q "text=\"Edit Events\""'
shot events_sheet
tap_text Snooze; sleep 1
check "snooze choices" 'dump | grep -q "text=\"15 minutes\"" && dump | grep -q "text=\"Tomorrow morning\""'
tap_text "15 minutes"; sleep 1.5
uid=$(sh_ pm list packages -U $LAUNCHER | grep -o 'uid:[0-9]*' | head -1 | cut -d: -f2); key="0|$LAUNCHER|7101|null|$uid"
check "snoozed (gone from Events)" '! dump | grep -q "Anna Virtanen"'
sh_ "cmd notification unsnooze '$key'" >/dev/null 2>&1; sleep 1.5; post; sleep 1.5; events_pane
# Rows can still be settling (row animations, a real notification arriving): retry until the sheet shows.
for _ in 1 2 3; do longpress "Anna Virtanen"; sleep 1.2; dump | grep -q "content-desc=\"Events actions\"" && break; sh_ input keyevent BACK; sleep 1; events_pane; done
tap_text Reply; sleep 2
check "reply composer" 'dump | grep -q "content-desc=\"Inline reply\""'
check "quoted line" 'dump | grep -q "Are we still on for lunch?"'
sh_ input text "Running%s5%smin%slate"; sleep 1
shot events_reply
sh_ input keyevent ENTER; sleep 2
check "reply delivered through RemoteInput" '[ "$(sh_ settings get global etesian_test_reply)" = "Running 5 min late" ]'
sh_ input keyevent BACK; sleep 1
post; sleep 1.5; events_pane; longpress "Anna Virtanen"; sleep 1.2; tap_text "Mark as read"; sleep 1.5
check "mark as read runs the app's action" '[ "$(sh_ settings get global etesian_test_reply)" = read ]'
post; sleep 1.5; events_pane; longpress "Anna Virtanen"; sleep 1.2; tap_text Dismiss; sleep 1.5
check "dismiss removes it" '! dump | grep -q "Anna Virtanen"'
post cancel
for k in $keys; do [ "${old[$k]}" = null ] && sh_ settings delete secure etesian_extra_$k >/dev/null || sh_ settings put secure etesian_extra_$k "${old[$k]}"; done
sh_ settings delete global etesian_test_reply >/dev/null
check "launcher did not crash" '! crashed $LAUNCHER'
home

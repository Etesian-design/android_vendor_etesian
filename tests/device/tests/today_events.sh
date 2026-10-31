# Android extras → Today in Events (story Phone v2, state screen-time): optional "Today" section
# above Notifications with one-line items (lit squircle sized to the line): screen time, all-day
# events, birthdays, upcoming events; the items are user-chosen. Off by default. Uses a temporary
# local test calendar and a test contact, both removed at the end.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1.5; }
SA="caller_is_syncadapter=true&account_name=etesian_test&account_type=LOCAL"
old=$(sh_ settings get secure etesian_extra_today); oitems=$(sh_ settings get secure etesian_today_items)
# Test data: a local calendar with an all-day event today and a timed event in two hours, a contact with a birthday today.
sh_ "content insert --uri 'content://com.android.calendar/calendars?$SA' --bind account_name:s:etesian_test --bind account_type:s:LOCAL --bind name:s:etesian_test --bind calendar_displayName:s:EtesianTest --bind calendar_access_level:i:700 --bind ownerAccount:s:etesian_test --bind visible:i:1 --bind sync_events:i:1 --bind calendar_timezone:s:UTC" 2>/dev/null
cal=$(sh_ content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=etesian_test" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
day0=$(( $(date -u -d "$(date +%Y-%m-%d)" +%s) * 1000 )); soon=$(( ($(date +%s) + 300) * 1000 ))  # in five minutes: still today
sh_ "content insert --uri content://com.android.calendar/events --bind calendar_id:i:$cal --bind title:s:'Etesian all-day test' --bind dtstart:l:$day0 --bind dtend:l:$(( day0 + 86400000 )) --bind allDay:i:1 --bind eventTimezone:s:UTC"
sh_ "content insert --uri content://com.android.calendar/events --bind calendar_id:i:$cal --bind title:s:'Etesian review' --bind dtstart:l:$soon --bind dtend:l:$(( soon + 1800000 )) --bind eventTimezone:s:UTC"
sh_ "content insert --uri content://com.android.contacts/raw_contacts --bind account_name:n: --bind account_type:n:"
rc=$(sh_ content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -o '_id=[0-9]*' | cut -d= -f2 | sort -n | tail -1)
sh_ "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$rc --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Test Birthday'"
sh_ "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$rc --bind mimetype:s:vnd.android.cursor.item/contact_event --bind data2:i:3 --bind data1:s:--$(date +%m-%d)"
sh_ settings put secure etesian_extra_today 0; sh_ settings delete secure etesian_today_items >/dev/null
unlock; events_pane
check "off: no Today section" '! dump | grep -q "text=\"Today\""'
sh_ settings put secure etesian_extra_today 1; sleep 1; events_pane
shot today_events
check "on: Today above Notifications" 'dump | grep -o "text=\"Today\"\|text=\"Notifications\"" | head -1 | grep -q Today'
check "screen time line (or none with < 1 min use)" 'dump | grep -q "screen time · " || ! sh_ dumpsys usagestats | grep -q totalTimeUsed'
check "all-day event line" 'dump | grep -q "text=\"Etesian all-day test\""'
check "birthday line" 'dump | grep -q "Test Birthday’s birthday"'
check "upcoming event line with its time" 'dump | grep -q "text=\"[0-9:APM ]* Etesian review\""'
check "no second Calendar section while Today lists upcoming" '! dump | grep -q "text=\"Calendar\""'
set -- $(bounds 'content-desc="Test Birthday’s birthday"'); rh=$(( ${4:-0} - ${2:-0} )); D=$(sh_ wm density | tail -1 | grep -o '[0-9]*$')
check "single line items (≤ 34dp tall: ${rh}px)" '[ $rh -gt 0 ] && [ $rh -le $(( 34 * D / 160 )) ]'
sh_ settings put secure etesian_today_items birthdays; sleep 1.5; events_pane
check "customised: only the chosen item (birthdays)" 'dump | grep -q "Test Birthday’s birthday" && ! dump | grep -q "screen time"'
check "Calendar section back when upcoming is not in Today" 'dump | grep -q "text=\"Calendar\"" && dump | grep -q "text=\"Etesian review\""'
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section extras >/dev/null; sleep 2
for _ in 1 2 3 4 5 6 7 8; do dump | grep -q "Today in Events: items" && break; sh_ input swipe 540 1700 540 900 300; sleep 0.8; done
check "settings: Today items option" 'dump | grep -q "Today in Events: items"'
# Clean up the test data.
sh_ "content delete --uri 'content://com.android.calendar/calendars?$SA' --where \"name='etesian_test'\""
sh_ "content delete --uri 'content://com.android.contacts/raw_contacts?caller_is_syncadapter=true' --where _id=$rc"
[ "$old" = null ] && sh_ settings delete secure etesian_extra_today >/dev/null || sh_ settings put secure etesian_extra_today $old
[ "$oitems" = null ] && sh_ settings delete secure etesian_today_items >/dev/null || sh_ settings put secure etesian_today_items $oitems
check "test calendar and contact removed" '! sh_ content query --uri content://com.android.calendar/calendars --projection name | grep -q etesian_test && ! sh_ content query --uri content://com.android.contacts/data --projection data1 | grep -q "Test Birthday"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

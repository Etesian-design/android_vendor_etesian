# Android extras → Priority people (story Phone v2, state priority): priority is per contact.
# Long-press a notification about a contact → "Priority person"; that person's notifications from
# any app (matched by Person uri or name) then sit first under Notifications, grouped under them
# with a gold star. Off by default. Uses a temporary test contact, removed at the end.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
center() { set -- $(bounds "$1"); [ -n "$4" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1.5; }
person() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind person --ei id $1 --es title "'$2'" --es text "'$3'" ${4:+--es uri "'$4'"} >/dev/null; sleep 1.2; }
TEL=+306900000071
old=$(sh_ settings get secure etesian_extra_priority); oppl=$(sh_ settings get secure etesian_priority_people)
olda=$(sh_ settings get secure etesian_extra_actions); oldr=$(sh_ settings get secure etesian_extra_inline_reply)
sh_ settings put secure etesian_extra_actions 0; sh_ settings put secure etesian_extra_inline_reply 0
sh_ "content insert --uri content://com.android.contacts/raw_contacts --bind account_name:n: --bind account_type:n:"
rc=$(sh_ content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -o '_id=[0-9]*' | cut -d= -f2 | sort -n | tail -1)
sh_ "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$rc --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Priority Tester'"
sh_ "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$rc --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$TEL --bind data2:i:2"
sh_ settings delete secure etesian_priority_people >/dev/null
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
person 0 "Priority Tester" "Are we still on for lunch?" "tel:$TEL"
sh_ settings put secure etesian_extra_priority 0
unlock; events_pane
check "off: no priority group" '! dump | grep -q "content-desc=\"Priority: "'
set -- $(center 'text="Are we still on for lunch?"'); lx=$1; ly=$2
sh_ input swipe ${lx:-540} ${ly:-900} ${lx:-540} ${ly:-900} 900; sleep 1.2
check "off: no Priority person in the sheet" '! dump | grep -q "text=\"Priority person\""'
sh_ input keyevent BACK; sleep 0.8
# Off, that long-press entered dismiss mode: tap the date to finish it.
set -- $(center "text=\"$(LC_ALL=C date +%A)\""); sh_ input tap ${1:-200} ${2:-300}; sleep 0.8
sh_ settings put secure etesian_extra_priority 1; sleep 1; events_pane
check "on, nobody marked: no group" '! dump | grep -q "content-desc=\"Priority: "'
# Regression: Priority alone must not turn the long-press into a menu (N9 dismiss mode stays).
set -- $(center 'text="Are we still on for lunch?"'); sh_ input swipe ${1:-540} ${2:-900} ${1:-540} ${2:-900} 900; sleep 1.2
check "priority only: long-press opens no sheet" '! dump | grep -q "text=\"Priority person\""'
set -- $(center "text=\"$(LC_ALL=C date +%A)\""); sh_ input tap ${1:-200} ${2:-300}; sleep 0.8
# Marking lives in the approved actions sheet.
sh_ settings put secure etesian_extra_actions 1; sleep 1; events_pane
for _ in 1 2 3; do
  set -- $(center 'text="Are we still on for lunch?"'); sh_ input swipe ${1:-540} ${2:-900} ${1:-540} ${2:-900} 900; sleep 1.2
  shot priority_longpress
  dump | grep -q 'text="Priority person"' && break; sh_ input keyevent BACK; sleep 0.8
done
check "long-press: Priority person" 'dump | grep -q "text=\"Priority person\""'
set -- $(center 'text="Priority person"'); sh_ input tap ${1:-0} ${2:-0}; sleep 1.5
cid=$(sh_ content query --uri content://com.android.contacts/raw_contacts --projection contact_id --where _id=$rc | grep -o 'contact_id=[0-9]*' | cut -d= -f2)
# Then a missed call from the same number and someone else's message (same test app: they would stack).
person 1 "Missed call" "$TEL" "tel:$TEL"
person 2 "Mikko Laine" "Band practice moved to Thursday?"
events_pane
check "stored per contact (id $cid)" 'sh_ settings get secure etesian_priority_people | grep -qw "$cid"'
shot priority_people
check "group: Priority Tester with a star" 'dump | grep -q "content-desc=\"Priority: Priority Tester\"" && dump | grep -q "content-desc=\"Priority person\""'
check "group holds the message and the missed call (matched by number)" 'dump | grep -q "text=\"Are we still on for lunch?\"" && dump | grep -q "text=\"Missed call\""'
set -- $(bounds 'content-desc="Priority: Priority Tester"'); gy=${2:-99999}; set -- $(bounds 'text="Mikko Laine"'); my=${2:-0}
set -- $(bounds 'text="Notifications"'); ny=${2:-99999}
check "group first under Notifications, above other notifications ($ny < $gy < $my)" '[ $ny -lt $gy ] && [ $gy -lt $my ]'
check "the grouped rows are not listed twice" '[ $(dump | grep -o "text=\"Are we still on for lunch?\"" | wc -l) -eq 1 ]'
for _ in 1 2 3; do
  set -- $(center 'text="Missed call"'); sh_ input swipe ${1:-540} ${2:-900} ${1:-540} ${2:-900} 900; sleep 1.2
  dump | grep -q 'text="Not a priority person"' && break; sh_ input keyevent BACK; sleep 0.8
done
set -- $(center 'text="Not a priority person"'); sh_ input tap ${1:-0} ${2:-0}; sleep 1.5
check "Not a priority person: group gone" '! dump | grep -q "content-desc=\"Priority: "'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
sh_ "content delete --uri 'content://com.android.contacts/raw_contacts?caller_is_syncadapter=true' --where _id=$rc"
[ "$old" = null ] && sh_ settings delete secure etesian_extra_priority >/dev/null || sh_ settings put secure etesian_extra_priority $old
for k in actions:$olda inline_reply:$oldr; do n=${k%%:*}; v=${k#*:}; [ "$v" = null ] && sh_ settings delete secure etesian_extra_$n >/dev/null || sh_ settings put secure etesian_extra_$n "$v"; done
[ "$oppl" = null ] && sh_ settings delete secure etesian_priority_people >/dev/null || sh_ settings put secure etesian_priority_people "$oppl"
check "test contact removed" '! sh_ content query --uri content://com.android.contacts/data --projection data1 | grep -q "Priority Tester"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

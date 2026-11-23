# Settings → glance line on the lock screen (mock 18): "Alarm 07:00 · Design review 14:00"
# right-aligned under the date in readable grey. No alarm is set: the test publishes only an event.
check "glance code present" 'grep -q etesian_extra_glance /home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/keyguard/ui/view/layout/sections/KeyguardSliceViewSection.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
old=$(sh_ settings get secure etesian_extra_glance); ev=$(sh_ settings get secure etesian_glance_event)
unlock; home
t=$(( $(date +%s) * 1000 + 3600000 ))
# Late in the day the next hour may be tomorrow; then the line legitimately stays empty.
# A real event in the phone's local calendar: the launcher publishes the next one when the screen comes on (and
# clears the line when the calendar has none), so a setting written by hand would not last.
localcal() { sh_ content query --uri content://com.android.calendar/calendars --projection _id:account_type | grep "account_type=LOCAL" | head -1 | grep -o "_id=[0-9]*" | cut -d= -f2; }
cal=$(localcal)
# A phone with no local calendar yet (Calendar never opened): a test one, removed at the end.
CALURI="content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=EtesianTest&account_type=LOCAL"
if [ -z "$cal" ]; then
  sh_ "content insert --uri '$CALURI' --bind account_name:s:EtesianTest --bind account_type:s:LOCAL --bind name:s:EtesianTest \
    --bind calendar_displayName:s:EtesianTest --bind calendar_access_level:i:700 --bind ownerAccount:s:EtesianTest --bind visible:i:1 --bind sync_events:i:1"
  cal=$(localcal); testcal=1
fi
sh_ content insert --uri content://com.android.calendar/events --bind calendar_id:i:$cal --bind title:s:EtesianTestReview \
  --bind dtstart:l:$t --bind dtend:l:$((t + 1800000)) --bind eventTimezone:s:UTC
sh_ settings put secure etesian_extra_glance 1; sleep 1
lock; sh_ input keyevent WAKEUP; sleep 3
if [ $(date +%H) -lt 23 ]; then check "glance line shown (the calendar's next event)" 'dump | grep -q "EtesianTestReview"'; fi
shot lock_glance
sh_ settings put secure etesian_extra_glance 0; sleep 1
check "off: no glance line" '! dump | grep -o "<node[^>]*content-desc=\"Lock screen glance\"[^>]*>" | grep -q "visible-to-user=\"true\"" ; ! dump | grep -q "EtesianTestReview"'
unlock
[ "$old" = null ] && sh_ settings delete secure etesian_extra_glance >/dev/null || sh_ settings put secure etesian_extra_glance "$old"
[ "$ev" = null ] && sh_ settings delete secure etesian_glance_event >/dev/null || sh_ settings put secure etesian_glance_event "'$ev'"
sh_ "content delete --uri content://com.android.calendar/events --where \"title='EtesianTestReview'\"" 
[ -n "${testcal:-}" ] && sh_ "content delete --uri '$CALURI' --where \"account_name='EtesianTest'\""
check "systemui did not crash" '! crashed com.android.systemui'
home

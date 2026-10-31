# Android extras → glance line on the lock screen (mock 18): "Alarm 07:00 · Design review 14:00"
# right-aligned under the date in readable grey. No alarm is set: the test publishes only an event.
check "glance code present" 'grep -q etesian_extra_glance /home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/keyguard/ui/view/layout/sections/KeyguardSliceViewSection.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
old=$(sh_ settings get secure etesian_extra_glance); ev=$(sh_ settings get secure etesian_glance_event)
unlock; home
t=$(( $(date +%s) * 1000 + 3600000 ))
# Late in the day the next hour may be tomorrow; then the line legitimately stays empty.
sh_ settings put secure etesian_extra_glance 1
sh_ settings put secure etesian_glance_event "'Design review 14:00|$t'"
lock; sh_ input keyevent WAKEUP; sleep 2
if [ $(date +%H) -lt 23 ]; then check "glance line shown" 'dump | grep -q "Design review 14:00"'; fi
shot lock_glance
sh_ settings put secure etesian_extra_glance 0; sleep 1
check "off: no glance line" '! dump | grep -o "<node[^>]*content-desc=\"Lock screen glance\"[^>]*>" | grep -q "visible-to-user=\"true\"" ; ! dump | grep -q "Design review 14:00"'
unlock
[ "$old" = null ] && sh_ settings delete secure etesian_extra_glance >/dev/null || sh_ settings put secure etesian_extra_glance "$old"
[ "$ev" = null ] && sh_ settings delete secure etesian_glance_event >/dev/null || sh_ settings put secure etesian_glance_event "'$ev'"
check "systemui did not crash" '! crashed com.android.systemui'
home

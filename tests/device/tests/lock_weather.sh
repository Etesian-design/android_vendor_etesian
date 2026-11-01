# Optional lock screen weather (MeeCast-like, off by default): Etesian settings → Android extras →
# "Weather on the lock screen"; on → a weather line under the date, off → none.
unlock
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
old=$(sh_ settings get secure etesian_lock_weather); now=$(sh_ settings get secure etesian_weather_now)
sh_ settings put secure etesian_lock_weather 0
# The launcher publishes its weather; use a reading if it has none yet.
[ "$now" = null ] && sh_ settings put secure etesian_weather_now "'0|1|21|°C|Testville'"
sh_ am force-stop com.bompotis.etesian.launcher; sleep 1; home
sh_ am start -n com.bompotis.etesian.launcher/.SettingsActivity --es section lock >/dev/null; sleep 2.5
for _ in 1 2 3 4; do dump | grep -q 'text="Weather"' && break; sh_ input swipe 540 1700 540 900 300; sleep 0.8; done
check "switch on the Lock screen page" 'tap_text "Weather" && sleep 1'
check "switch turns it on" '[ "$(sh_ settings get secure etesian_lock_weather)" = 1 ]'
home; lock; sh_ input keyevent WAKEUP; sleep 2
check "lock screen shows the weather" 'dump | grep -o "<node[^>]*content-desc=\"Lock screen weather\"[^>]*>" | grep -q "text=\"[^\"]\+\""'
sh_ settings put secure etesian_lock_weather 0; sleep 1
check "off: no weather line" '! dump | grep -q "content-desc=\"Lock screen weather\""'
unlock
[ "$old" = null ] && sh_ settings delete secure etesian_lock_weather >/dev/null || sh_ settings put secure etesian_lock_weather "$old"
[ "$now" = null ] && sh_ settings delete secure etesian_weather_now >/dev/null
check "systemui did not crash" '! crashed com.android.systemui'
home

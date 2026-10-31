# Bug 2026-10-27: after a reboot the Events view had no weather until GPS got a fix (indoors: never),
# since only the system's last fix was used. The last coordinates are now remembered.
# Uses a test GPS fix (Helsinki), removed again before the restart.
unlock; home
ev() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 200 1200 900 1200 250; sleep 1.2; done
  for _ in 1 2 3; do sh_ input swipe 540 700 540 1800 150; done; sleep 1; }
has_weather() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o '<node[^>]*content-desc="weather temperature"[^>]*>' | grep -q 'text="[^"]'; }
check "Weather.kt remembers the last fix" 'grep -q weather_last_fix ~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/Weather.kt'
sh_ cmd location providers add-test-provider gps >/dev/null 2>&1
sh_ cmd location providers set-test-provider-enabled gps true >/dev/null 2>&1
sh_ cmd location providers set-test-provider-location gps --location 60.1699,24.9384 >/dev/null 2>&1
sh_ am force-stop $LAUNCHER; sleep 1; ev; for _ in 1 2 3 4 5 6 7 8; do has_weather && break; sleep 2; ev; done
check "weather shows with a fix" 'has_weather'
sh_ cmd location providers remove-test-provider gps >/dev/null 2>&1
sh_ am force-stop $LAUNCHER; sleep 1; ev; for _ in 1 2 3 4 5 6; do has_weather && break; sleep 2; ev; done
check "and after a restart with no fix (remembered)" 'has_weather'
check "launcher did not crash" '! crashed $LAUNCHER'
home

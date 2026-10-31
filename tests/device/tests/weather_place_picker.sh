# Belle Weather's place picker (2026-10-31): tapping the place on a weather widget opens it; typing finds towns (Open-Meteo
# geocoding), picking one shows its weather everywhere, and "Current location" goes back to the phone's location.
# Needs the network. The user's own weather place is kept and put back.
unlock
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_id() { local b; b=$(dump | grep -o "<node[^>]*$1[^>]*>" | head -n 1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '); set -- $b
  [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2; }
prefs() { sh_ cat $LP; }
sh_ "cp $LP /data/local/tmp/wpbak.xml"
wp=$(pref_switch home Widgets 1)
widgets_only BelleWeatherClassicWidget; sleep 2
tap_id 'resource-id="com.bompotis.etesian.launcher:id/bc_place"'
check "tapping the place opens the place picker" 'top_activity | grep -q "WeatherPlaceActivity"'
check "it offers the current location" 'dump | grep -q "text=\"Current location\""'
sh_ input keyevent KEYCODE_MOVE_END; for _ in $(seq 30); do sh_ input keyevent KEYCODE_DEL; done
sh_ input text Oulu; sleep 5
check "typing finds the town, with its country" 'dump | grep -q "text=\"Oulu\"" && dump | grep -q "Finland"'
shot weather_place_picker
tap_id 'text="Oulu"[^>]*class="android.widget.TextView"'; sleep 1
[ "$(top_activity | grep -c WeatherPlaceActivity)" = 0 ] || tap_id 'text="Oulu"'
check "picking it closes the picker" '! top_activity | grep -q "WeatherPlaceActivity"'
check "it is kept with its coordinates" 'prefs | grep -q "name=\"weather_place\">65\." && prefs | grep -q "name=\"weather_city\">Oulu<"'
for _ in 1 2 3 4 5 6; do dump | grep -q 'bc_place"[^>]*' && dump | grep -o '<node[^>]*bc_place[^>]*>' | grep -q 'text="Oulu"' && break; sleep 2; done
check "the widget shows the place picked" 'dump | grep -o "<node[^>]*bc_place[^>]*>" | grep -q "text=\"Oulu\""'
shot weather_place_widget
tap_id 'resource-id="com.bompotis.etesian.launcher:id/bc_place"'
sh_ input keyevent KEYCODE_MOVE_END; for _ in $(seq 30); do sh_ input keyevent KEYCODE_DEL; done
sh_ input text zzqxjvv; sleep 5
check "no match says so" 'dump | grep -q "No places called"'
tap_id 'text="Current location"'
check "Current location forgets the place" '! prefs | grep -q "name=\"weather_city\">[^<]" && ! prefs | grep -q "name=\"weather_place\""'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home
widgets_restore
sh_ "am force-stop $LAUNCHER; cat /data/local/tmp/wpbak.xml > $LP; rm -f /data/local/tmp/wpbak.xml"
pref_switch home Widgets "$wp" >/dev/null; home

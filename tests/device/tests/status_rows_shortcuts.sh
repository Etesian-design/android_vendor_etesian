# 2026-11-22 (user): the N9 status menu's connection rows were shortcuts to their settings, not switches (the
# toggles strip switches). A tap on a row opens that setting and leaves the connection as it was.
unlock; home
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
bt() { sh_ settings get global bluetooth_on; }
# The rows the menu shows by default (Flight mode and Auto-rotate are hidden rows).
for title in Bluetooth "Mobile data" "Internet connection"; do
  b0=$(bt); f0=$(sh_ settings get global airplane_mode_on); r0=$(sh_ settings get global mobile_data); w0=$(sh_ settings get global wifi_on)
  menu; tap_text "$title"; sleep 2
  top=$(top_activity)
  check "$title row opens Settings ($top)" 'echo "$top" | grep -q "com.android.settings"'
  check "$title row switched nothing" '[ "$(bt)" = "$b0" ] && [ "$(sh_ settings get global airplane_mode_on)" = "$f0" ] && [ "$(sh_ settings get global mobile_data)" = "$r0" ] && [ "$(sh_ settings get global wifi_on)" = "$w0" ]'
  check "the menu closed" '! top_activity | grep -q StatusMenuActivity'
  home
done
check "launcher did not crash" '! crashed $LAUNCHER'

# 2026-11-23 audit (user): every row is a shortcut whose page matches its title; switches are the strip's. Flashlight
# and the recorder have no page: tiles only, never offered as rows. Battery and Hotspot rows open their pages.
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
oldx=$(sh_ settings get secure etesian_extra_status_menu); sh_ cp $P /data/local/tmp/sr_prefs.bak
sh_ settings put secure etesian_extra_status_menu 1
sh_ am force-stop $LAUNCHER
sh_ "sed -i 's#<string name=\"status_blocks\">[^<]*</string>##; s#</map>#<string name=\"status_blocks\">profile,volume,strip,row:battery,row:hotspot,row:bt,row:rotate,row:torch</string></map>#' $P"
menu
d=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml)
check "an old Flashlight row is gone (a tile only)" '! echo "$d" | grep -q "text=\"Flashlight\""'
for pair in "Battery:PowerUsageSummary" "Hotspot:TetherSettings" "Bluetooth:BluetoothDashboard" "Auto-rotate:AutoRotate"; do
  title=${pair%%:*}; page=${pair#*:}
  # Each from a clean start: the last row's page (Hotspot has "Bluetooth tethering") must not take the tap.
  sh_ am force-stop com.android.settings; home
  b0=$(bt); menu; tap_text "$title"; sleep 2; top=$(top_activity)
  check "$title row opens its own page ($top)" 'echo "$top" | grep -q "$page"'
  check "$title row switched nothing" '[ "$(bt)" = "$b0" ]'
  home
done
# Edit mode's Add list offers shortcuts only.
menu; b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="Battery"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b
sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; sleep 1.2
sh_ input swipe 540 1700 540 700 300; sleep 1
d=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml)
check "edit mode offers no Flashlight or Screen recorder row" '! echo "$d" | grep -q "content-desc=\"Add Flashlight\"" && ! echo "$d" | grep -q "content-desc=\"Add Screen recorder\""'
check "edit mode offers Location as a shortcut" 'echo "$d" | grep -q "content-desc=\"Add Location\""'
shot status_rows_add
sh_ input keyevent BACK; sleep 1
sh_ am force-stop $LAUNCHER; sh_ "cat /data/local/tmp/sr_prefs.bak > $P"; sh_ rm -f /data/local/tmp/sr_prefs.bak
put_back etesian_extra_status_menu "$oldx"
check "launcher did not crash (shortcuts)" '! crashed $LAUNCHER'
home

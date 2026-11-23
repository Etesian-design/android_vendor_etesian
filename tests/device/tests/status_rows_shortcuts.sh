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

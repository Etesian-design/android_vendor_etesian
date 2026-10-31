# Bug list task: N9 settings main page — quick rows (Flight mode, Internet connection, Mobile network,
# Brightness slider) above the section list; long-press a sub page setting to pin it, long-press to unpin.
unlock
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "<node[^>]*text=\"$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '; }
longpress() { set -- $(bounds "$1"); x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 )); sh_ input swipe $x $y $x $y 900; sleep 1.5; }
main() { sh_ am force-stop com.bompotis.etesian.launcher; sleep 1; home; sh_ am start -n com.bompotis.etesian.launcher/.SettingsActivity >/dev/null; sleep 2.5; }
main
for t in "Flight mode" "Internet connection" "Mobile network" "Brightness" "Home screen"; do
  check "main page row: $t" 'dump | grep -q "text=\"$t\""'
done
check "brightness slider" 'dump | grep -q "id/quick_seek"'
f=$(sh_ settings get global airplane_mode_on)
check "flight mode switch shows the state" 'dump | grep -o "<node[^>]*id/switchWidget[^>]*>" | grep -q "checked=\"$([ "$f" = 1 ] && echo true || echo false)\""'
# Pin "Icon shape" from Applications, check it on the main page, then unpin it.
sh_ am force-stop com.bompotis.etesian.launcher; sleep 1; home; sh_ am start -n com.bompotis.etesian.launcher/.SettingsActivity --es section apps >/dev/null; sleep 2.5
longpress "Icon shape"; tap_text "Pin to Settings"; sleep 1
main
check "pinned setting on the main page" 'dump | grep -q "text=\"Icon shape\""'
check "with its star" 'dump | grep -q "id/quick_star"'
longpress "Icon shape"; tap_text "Unpin"; sleep 1.5
check "unpinned" '! dump | grep -q "text=\"Icon shape\""'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home

# N9 power menu (mock 14): stacked dark buttons Power off / Restart / Lock now / Emergency call over
# a 90% backdrop. Opened with a long press of the power key and closed with Back; nothing is tapped.
check "EtesianPowerMenu.java exists" '[ -f /home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/globalactions/EtesianPowerMenu.java ]'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
unlock; home
sh_ input keyevent --longpress POWER; sleep 2
check "power menu shown" 'dump | grep -q "content-desc=\"Power menu\""'
for t in "Power off" Restart "Lock now" "Emergency call"; do check "button $t" 'dump | grep -q "text=\"$t\""'; done
shot power_menu
# Every option of the stock menu on this build (config_globalActionsList), N9 names for the mock's
# four. Conditional ones: lockdown → "Lock now" (always offered), logout / bugreport / users only
# when enabled; advanced restart adds the Lineage restart targets when on.
CFG=/home/memphisx/android/lineage/frameworks/base/core/res/res/values/config.xml
keys=$(awk '/name="config_globalActionsList"/,/<\/string-array>/' $CFG | grep -o '<item>[a-z]*</item>' | sed 's/<[^>]*>//g')
menu=$(dump)
for k in $keys; do
  case $k in
    emergency) l="Emergency call";; lockdown) l="Lock now";; power) l="Power off";; restart) l="Restart";;
    screenshot) l="Screenshot";; airplane) l="Airplane mode";; settings) l="Settings";;
    bugreport) [ "$(sh_ settings get global bugreport_in_power_menu)" = 1 ] && l="Bug report" || continue;;
    *) continue;;
  esac
  check "stock option $k offered ($l)" 'echo "$menu" | grep -q "text=\"$l"'
done
check "backdrop ~90% dark" '[ $(pixel 30 1500) -lt 40 ]'
sh_ input keyevent BACK; sleep 1
check "Back closes it" '! dump | grep -q "content-desc=\"Power menu\""'
check "systemui did not crash" '! crashed com.android.systemui'
home

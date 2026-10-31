# Bug 2026-10-25: in the Events view the temperature and weather symbol sat off the place name
# (left-aligned under it). Now: symbol and temperature share a centre line, and the pair is
# centred under the place.
unlock; home
# Find Events whichever pane home opened on (one direction alone can miss it after other tests).
for d in "200 900" "900 200"; do for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break 2
  set -- $d; sh_ input swipe $1 1200 $2 1200 250; sleep 1.2; done; done
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
box() { sh_ cat /sdcard/u.xml | grep -o "<node[^>]*content-desc=\"$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '; }
P=$(box "weather place"); T=$(box "weather temperature"); I=$(box "weather symbol")
if [ -n "$P" ] && [ -n "$T" ] && [ -n "$I" ]; then
  read px1 py1 px2 py2 <<< "$P"; read tx1 ty1 tx2 ty2 <<< "$T"; read ix1 iy1 ix2 iy2 <<< "$I"
  dy=$(( (ty1 + ty2) / 2 - (iy1 + iy2) / 2 )); dy=${dy#-}
  dx=$(( (tx1 + ix2) / 2 - (px1 + px2) / 2 )); dx=${dx#-}
  check "temperature and symbol on one centre line ($dy px)" "[ $dy -le 4 ]"
  check "temperature and symbol centred under the place ($dx px)" "[ $dx -le 6 ]"
else
  bad "weather shown in the Events view (place/temperature/symbol)"
fi
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home

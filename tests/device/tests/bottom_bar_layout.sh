# N9 bottom bar (measured on the N9, 480 px wide): first and last button centres 44/480 of the
# width from the edges whatever the count, the others evenly between; actions only their
# squircle; a lone Back at 44/480 on the left; tabs (Store) fill equal cells (2026-10-25).
bounds() { sh_ "uiautomator dump --windows /sdcard/u.xml" >/dev/null 2>&1
  sh_ cat /sdcard/u.xml | tr '>' '\n' | grep "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | tr -cs '0-9' ' '; }
unlock
W=$(sh_ wm size | grep -o '[0-9]*x' | tr -d x)
E=$(( W * 44 / 480 )); T=$(( W * 2 / 100 ))
near() { [ $(( $1 - $2 )) -ge -$T ] && [ $(( $1 - $2 )) -le $T ]; }
sh_ "monkey -p com.bompotis.etesian.feeds -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1; sleep 3
read a1 _ a2 _ <<<"$(bounds 'Add feed')"; read b1 _ b2 _ <<<"$(bounds Refresh)"; read c1 _ c2 _ <<<"$(bounds Search)"
check "three buttons found" "[ -n \"$a1\" ] && [ -n \"$b1\" ] && [ -n \"$c1\" ]"
ca=$(( (a1+a2)/2 )); cb=$(( (b1+b2)/2 )); cc=$(( (c1+c2)/2 ))
check "first centre at 44/480 ($ca vs $E)" "near $ca $E"
check "last centre at 44/480 from the right ($cc vs $((W-E)))" "near $cc $((W-E))"
check "middle evenly between ($cb)" "near $cb $(( (ca+cc)/2 ))"
check "action is only its squircle ($((a2-a1))px)" "[ $((a2-a1)) -lt $((W/5)) ]"
sh_ am force-stop com.bompotis.etesian.feeds
sh_ "am start -W -n com.bompotis.etesian.launcher/.SettingsActivity" >/dev/null; sleep 1; tap_text System; sleep 2
read k1 _ k2 _ <<<"$(bounds Back)"
check "lone Back on the left at 44/480 ($(( (k1+k2)/2 )))" "[ -n \"$k1\" ] && near $(( (k1+k2)/2 )) $E"
home

# 2026-11-23 (user): turning the phone in Open applications zoomed out to a size portrait never has. A card is the screen
# turned with the phone, at the same scale: the landscape card is the portrait one turned (its width the portrait height,
# its height the portrait width), and a portrait app's snapshot is turned to fill it.
unlock; home
acc0=$(sh_ settings get system accelerometer_rotation); rot0=$(sh_ settings get system user_rotation)
for a in com.android.calculator2/.Calculator com.android.settings/.Settings com.android.deskclock/.DeskClock; do sh_ am start -n $a >/dev/null 2>&1; sleep 2; done
switcher() { sh_ input keyevent HOME; sleep 1.5; for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1900 700 300 700 300; sleep 1.5; done; }
# The size of the first card: its thumbnail's width and height.
card() { raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; raw_sh cat /sdcard/u.xml | tr '>' '\n' | grep 'ImageView' |
  grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | paste - - - - | awk '$3-$1>150 {print $3-$1, $4-$2; exit}'; }
sh_ settings put system accelerometer_rotation 0; sh_ settings put system user_rotation 0; sleep 2
switcher; read pw ph < <(card); shot switcher_portrait
sh_ settings put system user_rotation 1; sleep 2.5
A logcat -c
switcher; read lw lh < <(card); shot switcher_landscape
st=$(A shell am stack list | grep 'com.android.settings/' | grep -o 'taskId=[0-9]*' | head -1 | cut -d= -f2)
check "Settings' portrait snapshot is turned to fill its landscape card (task $st)" '[ -n "$st" ] && A logcat -d -s Etesian | grep "snapshot $st " | tail -1 | grep -qE "rotation [13]"'
check "a portrait card was measured (${pw}x${ph})" '[ -n "$pw" ] && [ "$ph" -gt "$pw" ]'
check "the landscape card is landscape (${lw}x${lh})" '[ -n "$lw" ] && [ "$lw" -gt "$lh" ]'
check "its width is the portrait card's height (${ph} → ${lw})" '[ $(( lw * 100 / ph )) -ge 90 ] && [ $(( lw * 100 / ph )) -le 110 ]'
check "its height the portrait card's width (${pw} → ${lh})" '[ $(( lh * 100 / pw )) -ge 90 ] && [ $(( lh * 100 / pw )) -le 110 ]'
sh_ settings put system user_rotation "${rot0:-0}"; sh_ settings put system accelerometer_rotation "${acc0:-0}"; sleep 1.5
check "launcher did not crash" '! crashed $LAUNCHER'
home

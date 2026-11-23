# 2026-11-23 (user): turning the phone in Open applications zoomed out to a size portrait never has. As on the N9 the
# cards keep their size (and shape) when the phone turns; only more of them fit across.
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
# Portrait apps' snapshots stay upright in their (portrait) cards: not turned for the landscape home.
st=$(A shell am stack list | grep 'com.android.settings/' | grep -o 'taskId=[0-9]*' | head -1 | cut -d= -f2)
check "Settings' portrait snapshot is not turned (task $st)" '[ -n "$st" ] && A logcat -d -s Etesian | grep "snapshot $st " | tail -1 | grep -q "rotation 0"'
check "a portrait card was measured (${pw}x${ph})" '[ -n "$pw" ]'
check "the card keeps its width when the phone turns (${pw} → ${lw})" '[ -n "$lw" ] && [ $(( lw * 100 / pw )) -ge 85 ] && [ $(( lw * 100 / pw )) -le 115 ]'
check "and its height (${ph} → ${lh})" '[ -n "$lh" ] && [ $(( lh * 100 / ph )) -ge 85 ] && [ $(( lh * 100 / ph )) -le 115 ]'
sh_ settings put system user_rotation "${rot0:-0}"; sh_ settings put system accelerometer_rotation "${acc0:-0}"; sleep 1.5
check "launcher did not crash" '! crashed $LAUNCHER'
home

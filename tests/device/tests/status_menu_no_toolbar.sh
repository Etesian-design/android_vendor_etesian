# Bug 2026-10-27 (user): opening the status menu (from home or from Open applications edit mode)
# showed the system toolbar with a Back arrow under it, after a white flash. The N9 status menu has
# no toolbar (mock n9-status-menu.png): the launcher now registers it as hidden.
SM=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/StatusMenuActivity.kt
check "StatusMenuActivity hides the system toolbar" 'grep -q "hidden = true" $SM'
unlock
sh_ am start -n com.android.calculator2/.Calculator >/dev/null; sleep 2; sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2
home; for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
# Brightest pixel of the bottom 60 rows per frame (the toolbar's Back arrow is white; the dimmed edit bar is not).
bottom_max() { /home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio
f=[x[-60:] for x in iio.imiter('/tmp/claude-1000/smt.mp4')]
# From the frame where the menu starts to show (the bottom changes), to the end.
s=next((i for i in range(1, len(f)) if abs(float(f[i].mean()) - float(f[0].mean())) > 3), len(f) - 1)
print(max(int(x.max()) for x in f[s:]))"; }
sh_ input swipe 300 500 300 500 1000; sleep 1
check "Open applications edit mode" 'sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q "text=\"Close all\""'
sh_ "screenrecord --size 360x720 --time-limit 3 /sdcard/smt.mp4" & sleep 0.6; sh_ input tap 540 30; wait
adb pull /sdcard/smt.mp4 /tmp/claude-1000/smt.mp4 >/dev/null 2>&1; m=$(bottom_max)
check "status menu over edit mode: no toolbar (bottom max $m)" '[ "$m" -lt 150 ]'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1; home
sh_ "screenrecord --size 360x720 --time-limit 3 /sdcard/smt.mp4" & sleep 0.6; sh_ input tap 540 30; wait
adb pull /sdcard/smt.mp4 /tmp/claude-1000/smt.mp4 >/dev/null 2>&1; m=$(bottom_max)
check "status menu over home: no toolbar (bottom max $m)" '[ "$m" -lt 150 ]'
sh_ input keyevent BACK; sleep 1
check "launcher did not crash" '! crashed $LAUNCHER'
home

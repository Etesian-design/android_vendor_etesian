# User 2026-10-27: Settings search opened a new screen. N9: the field drops in over the page; the
# options stay visible under it until something is typed; results then cover them.
unlock
sh_ am start -W -n com.android.settings/.Settings >/dev/null; sleep 1.5
sh_ input swipe 540 400 540 1100 400; sleep 2
check "pull down opens search" 'top_activity | grep -q SearchActivity'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# uiautomator sees only the search window: look for the options' white text under the field.
lit() { adb exec-out screencap -p > /tmp/claude-1000/ss.png; /home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio; x=iio.imread('/tmp/claude-1000/ss.png')[400:1200,150:900,:3].mean(axis=2); print(int((x>200).mean()*1000))"; }
check "the Settings options show under the empty field" '[ $(lit) -gt 10 ]'
check "no toolbar under it (bottom rows dark)" '[ $(pixel 540 2150) -lt 40 ]'
sh_ input text bluetooth; sleep 2.5
check "typing shows results" 'dump | grep -q "text=\"Bluetooth scanning\""'
check "results cover the options" '! dump | grep -q "text=\"Recent apps, default apps\""'
sh_ input keyevent BACK; sleep 0.6; sh_ input keyevent BACK; sleep 1
check "Back closes search to Settings" 'top_activity | grep -q com.android.settings/'
check "search did not crash" '! crashed com.android.settings.intelligence'
home

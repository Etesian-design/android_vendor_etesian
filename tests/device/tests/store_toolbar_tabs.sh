# Bug 2026-10-25 #8: the Store drew its own Home / Categories / Search / Updates row. They must be
# the system toolbar's tabs (icons, through the API), and work.
unlock; home
sh_ am start -S -n com.bompotis.etesian.store/.ui.MainActivity >/dev/null; sleep 3
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "no own tab row in the Store" '! sh_ cat /sdcard/u.xml | grep -q "text=\"Categories\""'
check "Home view shows" 'sh_ cat /sdcard/u.xml | grep -q "text=\"New applications\""'
sh_ input tap 405 2090; sleep 1.5   # second of four tab cells: Categories
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "toolbar Categories tab switches the view" '! sh_ cat /sdcard/u.xml | grep -q "text=\"New applications\""'
sh_ input keyevent BACK; sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "Back returns to Home" 'sh_ cat /sdcard/u.xml | grep -q "text=\"New applications\""'
home

# 2026-11-19 (user): the apps as on the N9 (compared with real N9 screens). Each opened fresh from the home screen:
# - Clock opens on its one N9 page, the clock over the alarms, and never asks for permissions;
# - Calendar opens on the month (its day's events under it);
# - Calculator is black with an orange result, keys without boxes, and nothing on top (its menu is on the bar);
# - Contacts has no drawer button on top; its menu (with Labels and accounts) is the N9 sheet;
# - Files opens on every document on the phone, not a folder;
# - every menu button on the bar is the N9's three bars.
A() { command adb "$@"; }
dump() { A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml; }
open_app() { sh_ am force-stop $1; sh_ monkey -p $1 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4; }
px() { A exec-out screencap -p | python3 -c "import sys,io;from PIL import Image;im=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB');print(*im.getpixel(($1,$2)))"; }
unlock

open_app com.android.deskclock; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_clock.png"
check "Clock does not ask for permissions" '! echo "$u" | grep -q "com.android.permissioncontroller"'
check "Clock opens on the clock over the alarms" 'echo "$u" | grep -q "text=\"Alarms\""'

open_app org.lineageos.etar; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_calendar.png"
check "Calendar opens on the month (its tab selected, not only lit)" 'echo "$u" | grep -q "content-desc=\"Month\"[^>]*selected=\"true\""'

open_app com.android.calculator2; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_calculator.png"
set -- $(px 40 700)
check "Calculator is black ($*)" '[ $1 -lt 30 ] && [ $2 -lt 30 ] && [ $3 -lt 30 ]'
check "Calculator has nothing on top (no overflow menu)" '! echo "$u" | grep -q "content-desc=\"More options\""'
check "Calculator's menu is on the bar" 'echo "$u" | grep -q "content-desc=\"Menu\""'
sh_ input keyevent 8; sh_ input keyevent 9; sleep 1
c=$(A shell uiautomator dump /sdcard/u.xml >/dev/null; A shell cat /sdcard/u.xml | grep -o 'resource-id="com.android.calculator2:id/formula"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
if [ -n "$c" ]; then set -- $c; A exec-out screencap -p > /tmp/hd-calc.png
  orange=$(python3 -c "from PIL import Image;im=Image.open('/tmp/hd-calc.png').convert('RGB').crop(($1,$2,$3,$4));print(sum(1 for r,g,b in im.getdata() if r>200 and 110<g<170 and b<60))"); fi
check "the number is orange (${orange:-?} px)" '[ "${orange:-0}" -gt 50 ]'

open_app com.android.contacts; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_contacts.png"
check "Contacts has no drawer button on top" '! echo "$u" | grep -qi "content-desc=\"Open navigation drawer\""'
m=$(echo "$u" | grep -o 'content-desc="More options"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | tail -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
if [ -n "$m" ]; then set -- $m; A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; fi
u=$(dump); A exec-out screencap -p > "$SHOTS/apps_contacts_menu.png"
check "its menu is the N9 sheet, with Labels and accounts" 'echo "$u" | grep -q "text=\"Labels and accounts\""'
sh_ input keyevent BACK; sleep 1

open_app com.android.documentsui; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_files.png"
check "Files opens on every document" 'echo "$u" | grep -q "text=\"Documents\""'

for p in com.android.deskclock org.lineageos.etar com.android.calculator2 com.android.contacts com.android.documentsui; do sh_ am force-stop $p; done
check "the launcher's menu glyph is three bars" 'grep -q "M4,5.5h16" ~/android/lineage/vendor/etesian/launcher/app/src/main/res/drawable/tb_menu.xml'
home

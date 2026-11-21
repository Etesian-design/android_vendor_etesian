# 2026-11-19 (user): the apps as on the N9 (compared with real N9 screens). Each opened fresh from the home screen:
# - Clock opens on its one N9 page, the clock over the alarms, and never asks for permissions;
# - Calendar opens on the month (its day's events under it);
# - Calculator is black with an orange result, keys without boxes, and nothing on top (its menu is on the bar);
# - Contacts has no drawer button on top; its menu (with Labels and accounts) is the N9 sheet;
# - Files opens on every document on the phone as the N9's one list, newest first, grouped by age, under an orange title;
# - Clock's world page has Add city on the bar, not a floating pill;
# - Events' Clear and Refresh are the N9's filled grey pills (no rim);
# - Browser: the address bar at the bottom, Top sites as large names in a list;
# - every menu button on the bar is the N9's three bars.
A() { command adb "$@"; }
dump() { A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml; }
open_app() { sh_ am force-stop $1; sh_ monkey -p $1 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4; }
px() { A exec-out screencap -p | python3 -c "import sys,io;from PIL import Image;im=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB');print(*im.getpixel(($1,$2)))"; }
unlock

open_app com.android.deskclock; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_clock.png"
check "Clock does not ask for permissions" '! echo "$u" | grep -q "com.android.permissioncontroller"'
check "Clock opens on the clock over the alarms" 'echo "$u" | grep -q "text=\"Alarms\""'

# The world clock page: Add city on the bar, no floating pill.
A shell input tap $(A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml | grep -o 'content-desc="World clock"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'); sleep 1.5
u=$(dump); A exec-out screencap -p > "$SHOTS/apps_clock_world.png"
SH=$(A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2); cy=$(echo "$u" | grep -o 'content-desc="Cities"[^>]*bounds="\[[0-9]*,[0-9]*' | grep -o '[0-9]*$' | head -1)
check "the world clock's Add city is on the bar (${cy:-?} of $SH)" '[ -n "$cy" ] && [ "$cy" -gt $((SH * 7 / 8)) ]'
check "and there is no floating pill" '! echo "$u" | grep -q "resource-id=\"com.android.deskclock:id/fab\""'

open_app org.lineageos.etar; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_calendar.png"
check "Calendar opens on the month (its tab selected, not only lit)" 'echo "$u" | grep -q "content-desc=\"Month\"[^>]*selected=\"true\""'

open_app com.android.calculator2; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_calculator.png"
set -- $(px 40 700); cr=$1 cg=$2 cb=$3
check "Calculator is black ($cr $cg $cb)" '[ $cr -lt 30 ] && [ $cg -lt 30 ] && [ $cb -lt 30 ]'
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

# A document of today's for the list (removed again below).
sh_ 'echo etesian > /sdcard/Documents/etesian_test_note.txt'; sh_ content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
sh_ am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Documents/etesian_test_note.txt >/dev/null 2>&1; sleep 2
open_app com.android.documentsui; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_files.png"
check "Files opens on every document" 'echo "$u" | grep -q "text=\"Documents\""'
check "one list of documents, not folders (our note is in it)" 'echo "$u" | grep -q "text=\"etesian_test_note.txt\"\|text=\"etesian_test_note\""'
set -- $(px 700 200); hr=$1 hg=$2 hb=$3
check "the title bar is the N9's orange ($hr $hg $hb)" '[ $hr -gt 220 ] && [ $hg -gt 90 ] && [ $hg -lt 135 ] && [ $hb -lt 50 ]'
sh_ rm -f /sdcard/Documents/etesian_test_note.txt

# Browser: the address bar at the bottom (above the toolbar), Top sites as the N9's list of names, ≡ for the menu.
open_app org.lineageos.jelly; u=$(dump); A exec-out screencap -p > "$SHOTS/apps_browser.png"
ub=$(echo "$u" | grep -o 'resource-id="org.lineageos.jelly:id/urlBarLayout"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
set -- $ub; ubt=${2:-0}; SHt=$(A shell wm size | grep -o '[0-9]*$' | tail -1)
check "the browser's address bar is at the bottom ($ubt of $SHt)" '[ "$ubt" -gt $((SHt / 2)) ]'
check "Top sites is a list of names, no tiles" 'echo "$u" | grep -q "text=\"Top sites\"" && ! echo "$u" | grep -q "class=\"android.widget.GridLayout\""'
sh_ am force-stop org.lineageos.jelly

for p in com.android.deskclock org.lineageos.etar com.android.calculator2 com.android.contacts com.android.documentsui; do sh_ am force-stop $p; done
check "Events' Clear and Refresh are filled N9 pills" 'grep -q "background = N9.sectionButton(context)" ~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/EventsPage.kt'
check "the launcher's menu glyph is three bars" 'grep -q "M4,5.5h16" ~/android/lineage/vendor/etesian/launcher/app/src/main/res/drawable/tb_menu.xml'
home

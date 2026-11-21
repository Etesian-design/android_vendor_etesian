# User 2026-11-19, Calendar: the N9 agenda has no date header at the top; its first row is today's orange bar, then
# today's events or a grey "No events for today", then the next day with events (a grey bar) however far ahead. No
# "Touch to view events before / after" rows. A list that does not scroll has no fast scroll and takes the full width.
# Month and week keep a date bar, at the bottom just above the toolbar (it was cut off at the top). The ≡ opens the
# N9 menu with Settings, not Android's overflow list.
A() { command adb "$@"; }   # real screen coordinates (screenshots and dumps)
xml() { A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml; }
nb() { echo "$1" | grep -o "$2[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
set -- $(A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' '); SW=$1; SH=$2
E=org.lineageos.etar
unlock
# A local test calendar with one event 16 days ahead and nothing today; removed afterwards.
sh_ "content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=hdtest&account_type=LOCAL' --bind account_name:s:hdtest --bind account_type:s:LOCAL --bind name:s:hdtest --bind calendar_displayName:s:hdtest --bind calendar_color:i:-16776961 --bind calendar_access_level:i:700 --bind ownerAccount:s:hdtest --bind visible:i:1 --bind sync_events:i:1"
cal=$(sh_ content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=hdtest" | grep -o '_id=[0-9]*' | cut -d= -f2 | head -1)
d=$(( $(date +%s) / 86400 + 16 ))
sh_ "content insert --uri content://com.android.calendar/events --bind calendar_id:i:$cal --bind title:s:hdtestnext --bind dtstart:l:$(( d * 86400 + 36000 ))000 --bind dtend:l:$(( d * 86400 + 39600 ))000 --bind eventTimezone:s:UTC --bind hasAlarm:i:0"
today_events=$(sh_ content query --uri "content://com.android.calendar/instances/when/$(( $(date +%s) / 86400 * 86400 ))000/$(( ($(date +%s) / 86400 + 1) * 86400 ))000" --projection title 2>/dev/null | grep -c "Row:")

sh_ am force-stop $E
sh_ am start -W -n $E/com.android.calendar.AllInOneActivity >/dev/null; sleep 3
u=$(xml); set -- $(nb "$u" 'content-desc="Agenda"'); [ -n "$1" ] && A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2.5
u=$(xml); A exec-out screencap -p > "$SHOTS/calendar_agenda.png"
check "agenda: no date header at the top (the bar is gone in the agenda)" '! echo "$u" | grep -q "resource-id=\"$E:id/toolbar\""'
set -- $(nb "$u" 'text="Today - [^"]*"') 9999 9999 0 0; tt=$2; tr=$3
check "agenda: today's orange bar is the first row, at the top ($tt)" '[ $tt -lt $(( SH / 6 )) ]'
check "agenda: its colour is the N9 orange" 'python3 -c "
from PIL import Image; im=Image.open(\"$SHOTS/calendar_agenda.png\").convert(\"RGB\"); c=im.getpixel((40,$tt+8))
import sys; print(c); sys.exit(0 if c[0]>220 and 90<c[1]<130 and c[2]<60 else 1)"'
if [ "$today_events" = 0 ]; then
  check "agenda: \"No events for today\" under it" 'echo "$u" | grep -q "text=\"No events for today\""'
fi
check "agenda: the next event, 16 days ahead, is listed" 'echo "$u" | grep -q "text=\"hdtestnext\""'
check "agenda: no \"Touch to view events\" rows" '! echo "$u" | grep -q "Touch to view events"'
check "agenda: a list that does not scroll takes the full width (no fast scroll; bar to $tr of $SW)" '[ $tr -ge $(( SW - SW / 20 )) ]'

# ≡: the N9 menu with Settings.
set -- $(nb "$u" 'content-desc="More options"')
if [ -n "$1" ]; then A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; fi
u=$(xml); A exec-out screencap -p > "$SHOTS/calendar_menu.png"
check "≡ opens the N9 menu with Settings and About" 'echo "$u" | grep -q "text=\"Settings\"" && echo "$u" | grep -q "text=\"About Calendar\"" && echo "$u" | grep -q "text=\"Go to date\""'
check "and not Android's overflow list (no previous / next items)" '! echo "$u" | grep -q "text=\"Previous\"\|text=\"Next\""'
set -- $(nb "$u" 'text="Settings"'); [ -n "$1" ] && A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
check "its Settings opens Calendar's settings ($(top_activity))" 'top_activity | grep -q "$E/.*[Ss]ettings"'
A shell input keyevent BACK; sleep 1.5

# Month and week: the date bar sits at the bottom, above the toolbar, as tall as its text (week has a second line,
# the one that was cut off at the top).
datebar() {  # datebar <Toolbar tab label> <name>
  local u bt bb boxes sub label=$1 name=$2
  u=$(xml); set -- $(nb "$u" "content-desc=\"$label\""); [ -n "$1" ] && A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2.5
  u=$(xml); A exec-out screencap -p > "$SHOTS/calendar_$name.png"
  set -- $(nb "$u" "resource-id=\"$E:id/toolbar\"") 0 0 0 0; bt=$2; bb=$4
  check "$name: the date bar is at the bottom ($bt..$bb of $SH)" '[ $bt -gt $(( SH * 2 / 3 )) ]'
  # A line too tall for its box is cut at the box's bottom edge (the phone reports the box, not the cut): ink in
  # the box's bottom rows means cut-off letters; a line that fits leaves empty space under its baseline.
  boxes=$(echo "$u" | grep -o 'text="[^"]\+"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -c '0-9\n' ' ' | awk -v t=$bt -v b=$bb '$2 >= t && $4 <= b + 1 {print $1","$2","$3","$4}' | paste -sd';')
  sub=$(python3 -c "
from PIL import Image
im=Image.open('$SHOTS/calendar_$name.png').convert('L'); n=0
for bx in '$boxes'.split(';'):
    if not bx: continue
    l,t,r,b=map(int,bx.split(','))
    row=[im.getpixel((x,b-2)) for x in range(l,min(r,im.width))]
    med=sorted(row)[len(row)//2]
    if sum(1 for p in row if abs(p-med)>40)>3: n+=1
print(n)")
  check "$name: nothing in the date bar is cut off ($sub of $(echo "$boxes" | tr ';' '\n' | grep -c .) lines)" '[ "$sub" = 0 ] && [ -n "$boxes" ]'
}
datebar Month month
datebar Week week
# 2026-11-20 (user): "a white line at the top of the app (below the top status bar)" in the week view: the layout's old
# 1dp top padding showed the grid's light background above the dark week strip. Nothing light right above the strip.
u=$(xml); set -- $(nb "$u" 'resource-id="org.lineageos.etar:id/n9_week_strip"')
st=$2  # the strip's top, named: inside check, $1.. are check's own
gap=$(python3 -c "
from PIL import Image
im=Image.open('$SHOTS/calendar_week.png').convert('L')
print(sum(1 for y in range(max(0,${2:-0}-6),${2:-0}) if im.getpixel((${3:-0}//2,y))>150))")
check "week: the dark strip starts right under the status bar (light rows above it: $gap)" '[ -n "$st" ] && [ "$gap" = 0 ]'

# Leave it on the month, where it opens (Calendar remembers the last view).
u=$(xml); set -- $(nb "$u" 'content-desc="Month"'); [ -n "$1" ] && A shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5
sh_ "content delete --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=hdtest&account_type=LOCAL' --where \"name='hdtest'\""
check "test calendar removed" '! sh_ content query --uri content://com.android.calendar/calendars | grep -q name=hdtest'
check "calendar did not crash" '! crashed $E'
sh_ am force-stop $E; home

# The N9 fast scroll is universal: every list with a fast scroller uses the Etesian one
# (grey band, section labels, dot, dark finger band with the big section), and Calendar's agenda
# shows full dates ("Mon 27 Oct").
R=/home/memphisx/android/lineage
FS=$R/vendor/etesian/fastscroll/src/com/bompotis/etesian/fastscroll
check "one shared look (source)" '[ -f $FS/FastScrollCore.java ] && grep -q "FastScrollCore" $FS/EtesianFastScroller.java && grep -q "FastScrollCore" $FS/EtesianListFastScroller.java && grep -q "70f / 480f" $FS/FastScrollCore.java'
for f in packages/apps/Dialer/java/com/android/dialer/contactsfragment/ContactsFragment.java \
         packages/apps/Etar/app/src/main/java/com/android/calendar/agenda/AgendaListView.java \
         packages/apps/DeskClock/res/layout/cities_activity.xml \
         packages/apps/Twelve/app/src/main/java/org/lineageos/twelve/fragments/N9LibraryListFragment.kt \
         packages/apps/DocumentsUI/src/com/android/documentsui/dirlist/DirectoryFragment.java \
         packages/apps/Contacts/src/com/android/contacts/list/PinnedHeaderListView.java; do
  check "uses the Etesian scroller: ${f##*/}" "[ -f $R/$f ] && grep -q 'com.bompotis.etesian.fastscroll\|EtesianFastScroller\|EtesianListFastScroller' $R/$f"
done
check "agenda sections are full dates (source)" "grep -q 'EEE d MMM' $R/packages/apps/Etar/app/src/main/java/com/android/calendar/agenda/AgendaListView.java"
# Row text never runs under the band (70/480 of the width, reserved at the list's end).
under_band() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o '<node[^>]*text="[^"]\+"[^>]*bounds="[^"]*"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | tr -c '0-9\n' ' ' | awk -v lim=$(( 1080 - 1080 * 70 / 480 + 4 )) '$2 > 300 && $4 < 2000 && $3 > lim {n++} END {print n+0}'; }
# On the phone: Calendar's agenda, finger on the band shows a date.
PY=${PY:-/home/memphisx/android/.venv-tools/bin/python}
unlock
E=org.lineageos.etar
# A local test calendar with an event a day (no reminders), removed afterwards.
had=$(sh_ content query --uri content://com.android.calendar/calendars --projection _id:name | grep -c "name=hdtest")
[ "$had" = 0 ] && sh_ "content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=hdtest&account_type=LOCAL' --bind account_name:s:hdtest --bind account_type:s:LOCAL --bind name:s:hdtest --bind calendar_displayName:s:hdtest --bind calendar_color:i:-16776961 --bind calendar_access_level:i:700 --bind ownerAccount:s:hdtest --bind visible:i:1 --bind sync_events:i:1"
cal=$(sh_ content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=hdtest" | grep -o '_id=[0-9]*' | cut -d= -f2)
now=$(date +%s)
for i in $(seq 0 29); do d=$(( now / 86400 + i )); sh_ "content insert --uri content://com.android.calendar/events --bind calendar_id:i:$cal --bind title:s:hdtest$i --bind dtstart:l:$(( d * 86400 + 36000 ))000 --bind dtend:l:$(( d * 86400 + 39600 ))000 --bind eventTimezone:s:UTC --bind hasAlarm:i:0"; done
sh_ am force-stop $E
sh_ am start -a android.intent.action.VIEW -d content://com.android.calendar/time/$(date +%s)000 -n $E/com.android.calendar.AllInOneActivity >/dev/null; sleep 3
if top_activity | grep -q $E; then
  sh_ input tap 754 2090; sleep 2.5  # toolbar: Agenda (4th of 5)
  sh_ input swipe 540 1500 540 1000 200; sleep 0.3
  sh_ input swipe 1040 900 1040 1100 2500 &
  sleep 1.5; adb exec-out screencap -p > /tmp/fs-agenda.png; wait
  band=$($PY -c "
from PIL import Image; im=Image.open('/tmp/fs-agenda.png').convert('L'); w,h=im.size
c=im.crop((40,int(h*.3),w//2,int(h*.7))); px=list(c.tobytes()); print(sum(1 for p in px if p<70)*100//len(px))")
  check "agenda: dark finger band shows ($band% dark)" "[ $band -ge 20 ]"
  sleep 1; sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  n=$(under_band); check "agenda: no row text under the band ($n)" "[ $n = 0 ]"
  check "agenda rows show event titles" 'sh_ cat /sdcard/u.xml | grep -o "<node[^>]*text=\"hdtest[0-9]*\"[^>]*>" | grep -v "bounds=\"\[0,0\]\[0," | grep -q .'
fi
sh_ "content delete --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=hdtest&account_type=LOCAL' --where \"name='hdtest'\""
check "test calendar removed" '! sh_ content query --uri content://com.android.calendar/calendars | grep -q name=hdtest'
check "calendar did not crash" '! crashed org.lineageos.etar'
sh_ am force-stop $E
# Store categories: the counts stay left of the band.
S=com.bompotis.etesian.store
sh_ am start -n $S/.ui.MainActivity >/dev/null 2>&1; sleep 4
if top_activity | grep -q $S; then
  sh_ input tap 405 2090; sleep 2.5  # toolbar: Categories
  n=$(under_band); check "store categories: no row text under the band ($n)" "[ $n = 0 ]"
fi
sh_ am force-stop $S
# Dialer contacts: after a jump the pinned letter is the new section's, not over it.
eval "$(sed -n 5,6p $R/vendor/etesian/tests/device/tests/contacts_fastscroll.sh)"
for l in A B C D E F G H I J K L M N O P Q R S T U V W X Y Z; do add "${l}ara Hdtest"; done; sleep 2
D=com.android.dialer
sh_ am start -n $D/.main.impl.MainActivity >/dev/null 2>&1; sleep 3
if top_activity | grep -q $D; then
  sh_ input tap 660 2090; sleep 2.5  # toolbar: Contacts
  n=$(under_band); check "dialer contacts: no row text under the band ($n)" "[ $n = 0 ]"
  sh_ input swipe 540 1600 540 1000 200; sleep 0.3; sh_ input swipe 1040 900 1040 1100 1500; sleep 1.5
  sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  # Single-letter headers in the left column within one row height of each other = overlap.
  ov=$(sh_ cat /sdcard/u.xml | grep -o '<node[^>]*text="[A-Z]"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | tr -c '0-9\n' ' ' | awk '$1 < 150 {print $2}' | sort -n | awk 'NR > 1 && $1 - p < 60 {n++} {p = $1} END {print n+0}')
  check "dialer: pinned letter not over a row letter ($ov)" "[ $ov = 0 ]"
fi
sh_ am force-stop $D; cleanup
check "dialer did not crash" '! crashed com.android.dialer'
home

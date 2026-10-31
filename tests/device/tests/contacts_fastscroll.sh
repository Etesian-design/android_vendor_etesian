# Task: Contacts N9 fast scroll (n9-contacts-fastscroll*.png): grey band right with section letters and a
# dot, finger → dark band with the big letter, every small move steps one section; blue main header.
# Adds 26 temporary "Hdtest" contacts (one per letter) and removes them at the end.
unlock
add() { sh_ "id=\$(content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: >/dev/null; content query --uri content://com.android.contacts/raw_contacts --projection _id --sort '_id DESC' | head -1 | sed 's/.*_id=//'); content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$1'"; }
cleanup() { sh_ content delete --uri "content://com.android.contacts/raw_contacts?caller_is_syncadapter=true" --where "display_name\ like\ \'%Hdtest%\'"; }
cleanup
for l in A B C D E F G H I J K L M N O P Q R S T U V W X Y Z; do add "${l}ara Hdtest"; done
sh_ am force-stop com.android.contacts; sh_ monkey -p com.android.contacts -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
# rgb x y -> "r g b"
rgb() { adb exec-out screencap -p > /tmp/hd-cf.png; python3 -c "from PIL import Image; print(*Image.open('/tmp/hd-cf.png').convert('RGB').getpixel(($1,$2)))"; }
read r g b <<< "$(rgb 540 150)"
check "main page header is Contacts blue ($r $g $b)" '[ $b -gt 180 ] && [ $r -lt 60 ]'
# Scroll: the grey band shows (~70/480 of the width).
sh_ input swipe 400 1500 400 900 300; read r g b <<< "$(rgb 1040 1200)"
check "grey band on the right while scrolling ($r $g $b)" '[ $r -gt 150 ] && [ $r -lt 215 ] && [ $((($r-$b)*($r-$b))) -lt 100 ]'
# Brightest pixel of an area left of the band (one point can land on a name's text).
r=$(adb exec-out screencap -p > /tmp/hd-cf.png; python3 -c "from PIL import Image; i=Image.open('/tmp/hd-cf.png').convert('L'); print(max(i.getpixel((x,y)) for x in range(700,960,4) for y in range(1250,1350,4)))")
check "list is not covered left of it ($r)" '[ $r -gt 230 ]'
# Finger on the band: dark band across the list up to the scroll band.
sh_ input swipe 400 1500 400 1300 300
(sh_ input swipe 1040 1000 1040 1000 2500 &) ; sleep 1.2; adb exec-out screencap -p > /tmp/hd-cf.png; sleep 1.6
band=$(python3 -c "from PIL import Image; im=Image.open('/tmp/hd-cf.png').convert('RGB'); print(*im.getpixel((300,1000)), *im.getpixel((1040,1000)))"); read r g b r2 g2 b2 <<< "$band"
check "dark band at the finger ($r $g $b)" '[ $r -lt 90 ] && [ $g -lt 90 ]'
check "it stops at the scroll band ($r2)" '[ $r2 -gt 120 ]'
# Every small move steps one section.
first() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="[A-Z]ara Hdtest"' | head -1 | cut -c7; }
code() { printf %d "'$1"; }
sh_ input swipe 400 1500 400 1300 300
sh_ input swipe 1040 500 1040 500 200; sleep 0.8; a=$(first)
sh_ input swipe 400 1300 400 1500 300
sh_ input swipe 1040 500 1040 560 400; sleep 0.8; b=$(first)
check "a small drag steps one section ($a -> $b)" '[ -n "$a" ] && [ -n "$b" ] && [ $(code $b) -eq $(( $(code $a) + 1 )) ]'
f=/home/memphisx/android/lineage/vendor/etesian/fastscroll/src/com/bompotis/etesian/fastscroll/EtesianListFastScroller.java
check "scroller sizes from the N9 (source)" '[ -f $f ] && grep -q "70f / 480f" ${f%/*}/FastScrollCore.java && grep -q "150f / 854f" ${f%/*}/FastScrollCore.java'
check "contacts did not crash" '! crashed com.android.contacts'
cleanup; sh_ am force-stop com.android.contacts; home

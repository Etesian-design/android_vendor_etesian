# Task: Contacts N9 fast scroll (n9-contacts-fastscroll*.png): grey band right with section letters and a
# dot, finger → dark band with the big letter, every small move steps one section; blue main header.
# Adds 26 temporary "Hdtest" contacts (one per letter) and removes them at the end.
unlock
add() { sh_ "id=\$(content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: >/dev/null; content query --uri content://com.android.contacts/raw_contacts --projection _id --sort '_id DESC' | head -1 | sed 's/.*_id=//'); content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$1'"; }
cleanup() { sh_ content delete --uri "content://com.android.contacts/raw_contacts?caller_is_syncadapter=true" --where "display_name\ like\ \'%Hdtest%\'"; }
cleanup
# 2026-11-20 (user): no fast scroll when the list doesn't scroll: with only the phone's own few contacts (no Hdtest
# ones yet) no column, no letters in it, and the rows take the full width.
few=$(sh_ content query --uri content://com.android.contacts/contacts --projection _id | grep -c _id=)
if [ "$few" -le 5 ]; then
  sh_ am force-stop com.android.contacts; sh_ monkey -p com.android.contacts -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
  adb exec-out screencap -p > /tmp/hd-cf0.png
  # The section rules' own letters may sit at the right end of a rule (the list's headers); what must not be there
  # is the column: no grey band and no reserved strip (the background there is the list's own, row after row).
  dk=$(python3 -c "
from PIL import Image; i=Image.open('/tmp/hd-cf0.png').convert('L')
bg=i.getpixel((700,1500))
print(sum(1 for y in range(300,1900,4) if abs(sorted(i.getpixel((x,y)) for x in range(990,1076,4))[10]-bg)>8))")
  sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  rowr=$(sh_ cat /sdcard/u.xml | grep -o '<node[^>]*class="android.view.View[^"]*"[^>]*clickable="true"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | tr -c '0-9\n' ' ' | awk '$2 > 300 && $4 < 2000 {print $3}' | sort -n | tail -1)
  check "a short list ($few contacts) has no column: the right edge is the list's background ($dk rows differ)" '[ $dk -lt 10 ]'
  check "a short list's rows take the full width (rightmost row edge ${rowr:-none})" '[ -z "$rowr" ] || [ $rowr -ge 1060 ]'
  shot cf-short
fi
for l in A B C D E F G H I J K L M N O P Q R S T U V W X Y Z; do add "${l}ara Hdtest"; done
sh_ am force-stop com.android.contacts; sh_ monkey -p com.android.contacts -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
# rgb x y -> "r g b"
rgb() { adb exec-out screencap -p > /tmp/hd-cf.png; python3 -c "from PIL import Image; print(*Image.open('/tmp/hd-cf.png').convert('RGB').getpixel(($1,$2)))"; }
read r g b <<< "$(rgb 540 150)"
check "main page header is Contacts blue ($r $g $b)" '[ $b -gt 180 ] && [ $r -lt 60 ]'
# 2026-11-19 (user): at rest the band is the app's colour, only its letters show; a finger on it turns it grey, and
# a moving finger brings the dark band with the big section. Touches go below Breeze's handle (mid right edge): it
# takes the touches on its part of the edge.
# 2026-11-20 (user): a swipe scroll puts the dimmed bar with its dot on top of the letters; it goes after the list
# stops.
sh_ input swipe 400 1500 400 900 300; sleep 0.3; read r g b <<< "$(rgb 1040 1200)"
check "while swipe scrolling the band is dimmed grey ($r $g $b)" '[ $r -gt 120 ] && [ $r -lt 225 ]'
sleep 2; read r g b <<< "$(rgb 1040 1200)"
check "a second after the scroll stops it is the list's own colour again ($r $g $b)" '[ $r -gt 225 ]'
# Brightest pixel of an area left of the band (one point can land on a name's text).
r=$(adb exec-out screencap -p > /tmp/hd-cf.png; python3 -c "from PIL import Image; i=Image.open('/tmp/hd-cf.png').convert('L'); print(max(i.getpixel((x,y)) for x in range(700,960,4) for y in range(1250,1350,4)))")
check "list is not covered left of it ($r)" '[ $r -gt 230 ]'
# 2026-11-20 (user): "Letters are always visible. Only thing that changes is the grey scroll bar showing up on top of
# the letters": one column of letters, in the band, and none left of it on the section rules (the rows used to draw
# their own there too). A rule row is dark all across; letters only in part. Breeze's handle (mid right edge) is
# left out.
sleep 1.5; set -- $(adb exec-out screencap -p > /tmp/hd-cf.png; python3 -c "
from PIL import Image; i=Image.open('/tmp/hd-cf.png').convert('L')
ys=list(range(300,780,2))+list(range(1120,1900,2))
def letters(x0,x1):
    n=0
    for y in ys:
        d=sum(1 for x in range(x0,x1,2) if i.getpixel((x,y))<160)
        if 0<d<(x1-x0)//2*6//10: n+=d
    return n
print(letters(960,1076), letters(760,930))")
inband=$1 beside=$2
check "at rest one column of letters, in the band ($inband there, $beside left of it)" '[ $inband -ge 20 ] && [ $beside -lt 10 ]'
# A still finger on the band: it turns grey.
sh_ input swipe 400 1500 400 1300 300
(sh_ input swipe 1040 1500 1040 1500 2500 &) ; sleep 1.4; read r g b <<< "$(rgb 1040 1300)"; sleep 1.4
check "a finger on the band turns it grey ($r $g $b)" '[ $r -gt 120 ] && [ $r -lt 225 ] && [ $((($r-$b)*($r-$b))) -lt 100 ]'
# A moving finger: the dark band across the list, up to the scroll band.
sh_ input swipe 400 1500 400 1300 300
(sh_ input swipe 1040 1400 1040 1600 2500 &) ; sleep 1.6; adb exec-out screencap -p > /tmp/hd-cf.png; sleep 1.2
read y0 <<< "$(python3 -c "
from PIL import Image; im=Image.open('/tmp/hd-cf.png').convert('L')
ys=[y for y in range(1200,1800,4) if im.getpixel((300,y))<90]; print(sum(ys)//len(ys) if ys else 0)")"
band=$(python3 -c "from PIL import Image; im=Image.open('/tmp/hd-cf.png').convert('RGB'); print(*im.getpixel((300,$y0)), *im.getpixel((1040,$y0)))"); read r g b r2 g2 b2 <<< "$band"
check "a moving finger brings the dark band (at y $y0: $r $g $b)" '[ $y0 -gt 0 ] && [ $r -lt 90 ] && [ $g -lt 90 ]'
check "it stops at the scroll band ($r2)" '[ $r2 -gt 120 ]'
# Every small move steps one section.
first() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="[A-Z]ara Hdtest"' | head -1 | cut -c7; }
code() { printf %d "'$1"; }
# One gesture: a touch alone only greys the band, the first move picks a section, one more small move steps one.
sh_ input swipe 400 1500 400 1300 300
# Above Breeze's handle and early in the alphabet, so the list can still scroll to the next section; the first move
# is over the 4 dp a move needs.
sh_ input motionevent DOWN 1040 400; sleep 0.3
sh_ input motionevent MOVE 1040 420; sleep 0.8; a=$(first)
# Just over one section step on both phones (Pro1-X and 7 Pro 5G densities).
sh_ input motionevent MOVE 1040 465; sleep 0.8; b=$(first)
sh_ input motionevent UP 1040 465
check "a small drag steps one section ($a -> $b)" '[ -n "$a" ] && [ -n "$b" ] && [ $(code $b) -eq $(( $(code $a) + 1 )) ]'
f=/home/memphisx/android/lineage/vendor/etesian/fastscroll/src/com/bompotis/etesian/fastscroll/EtesianListFastScroller.java
check "scroller sizes from the N9 (source)" '[ -f $f ] && grep -q "70f / 480f" ${f%/*}/FastScrollCore.java && grep -q "150f / 854f" ${f%/*}/FastScrollCore.java'
check "contacts did not crash" '! crashed com.android.contacts'
cleanup; sh_ am force-stop com.android.contacts; home

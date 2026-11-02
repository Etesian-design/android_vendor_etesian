# User 2026-11-02: on the N9 text fields and buttons have squircle-half ends: a squircle as tall as the control (n = 2.8),
# cut in half and pulled apart. The patched apps draw them as nine-patches (patches/tools/squircle-shapes.py). For each
# field, the rim on the row 0.75 of the half-height above the middle: a squircle puts it ~19% of the half-height in
# from the side, a pill ~34%, a small-radius rectangle at the side itself.
unlock
bounds() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep "$1" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
# rim <shot> <l> <t> <r> <b>: where the rim is, in % of the half-height; it differs from the page by 20+ levels
rim() { python3 -c "
from PIL import Image; im=Image.open('$1').convert('L'); l,t,r,b=$2,$3,$4,$5; h=(b-t)/2; y=int(t+h-0.75*h)
bg=im.getpixel((max(l-6,0),y)); xs=[x for x in range(l,int(l+h)) if abs(im.getpixel((x,y))-bg)>20]
print(round((xs[0]-l)/h*100) if xs else -1)"; }
field() {  # field <name> <package> <uiautomator match>
  sh_ am force-stop $2; sh_ monkey -p $2 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3
  local b; b=$(bounds "$3"); shot "fields_$1"
  check "$1: the field is on screen" '[ -n "$b" ]'
  local p; p=$(rim "$SHOTS/fields_$1.png" $(echo $b | tr ' ' ' '))
  check "$1: squircle-half ends (rim at ${p}% in; 12-27)" '[ "$p" -ge 12 ] && [ "$p" -le 27 ]'
}
field Browser org.lineageos.jelly 'resource-id="org.lineageos.jelly:id/urlCardView"'
field Messaging com.android.messaging 'resource-id="com.android.messaging:id/conversation_list_search_field"'
field Phone com.android.dialer 'resource-id="com.android.dialer:id/search_box_collapsed"'
home
for p in org.lineageos.jelly com.android.messaging com.android.dialer; do check "$p did not crash" '! crashed $p'; done

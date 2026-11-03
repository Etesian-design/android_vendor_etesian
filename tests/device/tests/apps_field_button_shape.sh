# User 2026-11-02: on the N9 a text field's ends are a squircle as tall as the field (n = 2.8, the icons' curve), cut in
# half and pulled apart. The Applications search field, checked at two points of its top-left end: one a pill (circle)
# leaves outside and the squircle fills, one a small-radius rectangle fills and the squircle leaves outside.
unlock
sp=$(pref_switch apps "Pull down to search" 1)
apps_pane
sh_ input swipe 540 300 540 2050 1200; sleep 1.5
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
check "pull down opens the search field" '[ -n "$b" ]'
set -- $b; l=$1; t=$2; h=$(( $4 - $2 )); r=$(( h / 2 ))
shot apps_search_field
inside=$(pixel $(( l + r * 27 / 100 )) $(( t + r - r * 75 / 100 )))
outside=$(pixel $(( l + r * 5 / 100 )) $(( t + r - r * 95 / 100 )))
check "the end is a squircle, not a pill (fill at 0.27r in, 0.75r up: $inside)" '[ "$inside" -gt 25 ]'
check "nor a small-radius rectangle (black at the corner: $outside)" '[ "$outside" -lt 15 ]'
sh_ input keyevent BACK; sleep 0.5; sh_ input keyevent BACK; sleep 0.5
# Buttons have the same ends (blanco button background): the edit bar's Done button, dark on black.
sh_ input swipe 540 1100 540 1100 1500; sleep 1.5
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="Done"[^>]*' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
check "long press shows the edit bar's Done button" '[ -n "$b" ]'
set -- $b; l=$1; t=$2; h=$(( $4 - $2 )); r=$(( h / 2 ))
shot apps_edit_done
# Dark face on a dark bar: find the 1px light rim on the row 0.75r above the middle. A squircle puts it ~0.19r in from
# the button's left edge (a circle 0.34r, a small-radius rectangle at the edge itself).
rim=$(python3 -c "
from PIL import Image; im=Image.open('$SHOTS/apps_edit_done.png').convert('L'); y=$t+$r-$r*75//100
xs=[x for x in range($l, $l+$r) if im.getpixel((x,y)) > 40]; print(round((xs[0]-$l)/$r*100) if xs else -1)")
check "the Done button's end is a squircle (rim at ${rim}% of r in, squircle ~19)" '[ "$rim" -ge 12 ] && [ "$rim" -le 27 ]'
sh_ input keyevent BACK; sleep 0.5
pref_switch apps "Pull down to search" "$sp" >/dev/null; home
check "launcher did not crash" '! crashed $LAUNCHER'

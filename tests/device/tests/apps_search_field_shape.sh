# User 2026-11-02: on the N9 a text field's ends are a squircle as tall as the field (n = 2.8, the icons' curve), cut in
# half and pulled apart. The Applications search field, checked at two points of its top-left end: one a pill (circle)
# leaves outside and the squircle fills, one a small-radius rectangle fills and the squircle leaves outside.
unlock
sp=$(pref_switch apps "Pull down to search" 1)
home; for _ in 1 2 3; do sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q 'text="Torrent Player"' && break; sh_ input swipe 900 1200 100 1200 250; sleep 1.2; done
sh_ input swipe 540 300 540 2050 1200; sleep 1.5
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
check "pull down opens the search field" '[ -n "$b" ]'
set -- $b; l=$1; t=$2; h=$(( $4 - $2 )); r=$(( h / 2 ))
shot apps_search_field
inside=$(pixel $(( l + r * 27 / 100 )) $(( t + r - r * 75 / 100 )))
outside=$(pixel $(( l + r * 5 / 100 )) $(( t + r - r * 95 / 100 )))
check "the end is a squircle, not a pill (fill at 0.27r in, 0.75r up: $inside)" '[ "$inside" -gt 25 ]'
check "nor a small-radius rectangle (black at the corner: $outside)" '[ "$outside" -lt 15 ]'
sh_ input keyevent BACK; sleep 0.5
pref_switch apps "Pull down to search" "$sp" >/dev/null; home
check "launcher did not crash" '! crashed $LAUNCHER'

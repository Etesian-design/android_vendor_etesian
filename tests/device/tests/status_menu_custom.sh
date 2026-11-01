# Settings → status menu customisation and music (mocks 01, 01c, 01e, 07, 07b): toggle
# strip, edit mode (drag handle, ✕, ✎, Done / Add item), toggles editor, hidden rows with the
# handle, the small music widget. Off: the N9 menu as before (no strip). Taps only open views;
# nothing is switched (no toggle tile is tapped).
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
for f in StatusMenuActivity.kt StatusLayout.kt Glyphs.kt; do check "$f exists" '[ -f $SRC/$f ]'; done
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
desc() { dump | grep -q "content-desc=\"$1\""; }
tap_desc() { local b; b=$(dump | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1); set -- $(echo "$b" | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
old=$(sh_ settings get secure etesian_extra_status_menu); oldm=$(sh_ settings get secure etesian_extra_music)
sh_ cp $P /data/local/tmp/hl_prefs.bak 2>/dev/null
unlock; home
sh_ settings put secure etesian_extra_status_menu 0; sh_ settings put secure etesian_extra_music 0
menu
check "off: no toggle strip" '! desc Toggles'
check "off: N9 rows" 'dump | grep -q "text=\"Flight mode\""'
shot status_off
check "off: brightness is a flat bar (no knob)" 'desc Brightness'
check "off: no Settings button (the N9 status menu never had one)" '! dump | grep -q "text=\"Settings\""'
sh_ input keyevent BACK; sleep 1
sh_ settings put secure etesian_extra_status_menu 1; sh_ settings put secure etesian_extra_music 1
menu
check "on: toggle strip" 'desc Toggles'
check "strip tiles" 'desc Flashlight && desc Auto-rotate'
# Nothing playing here: no music widget outside edit mode (status_music_block.sh covers it).
check "music block hidden while nothing plays" '! desc "Now playing" || sh_ dumpsys media_session | grep -q "state=PLAYING\|state=PAUSED"'
shot status_on
# Edit mode: long-press a row
b=$(dump | grep -o 'text="Bluetooth"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b
sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; sleep 1.2
check "edit mode: Done and Add item" 'dump | grep -q "text=\"Done\"" && dump | grep -q "text=\"Add item\""'
check "edit mode: drag handles" 'desc "Drag Profile"'
check "edit mode: pencil on the strip" 'desc "Edit toggles"'
check "edit mode: remove marks" 'desc "Remove Bluetooth"'
check "edit mode: Add list under the blocks (mock 1c)" 'desc "Add list"'
check "edit mode: solid background above the edit bar" '[ $(pixel 540 1750) -lt 10 ]'
shot status_edit
# The edit bar sits at the very bottom (mock 1c): no gap left for the hidden toolbar.
eb=$(dump | grep -o 'text="Add item"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tail -1)
check "edit bar at the bottom edge (Add item ends at $eb)" '[ -n "$eb" ] && [ $(( $(sh_ wm size | grep -o "x[0-9]*" | tail -1 | tr -d x) - eb )) -lt 60 ]'
# Drag to reorder (mock 1c): Bluetooth's handle dragged above the first row; the order is saved.
gy() { dump | grep -o "content-desc=\"Drag $1\"[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ' | awk '{print $1+($3-$1)/2, $2+($4-$2)/2}'; }
first=$(dump | grep -o 'content-desc="Drag [^"]*"' | sed 's/content-desc="Drag //;s/"//' | grep -v "Profile\|Volume\|Toggles\|Bluetooth\|Brightness\|Media\|Alarm\|Music" | head -1)
set -- $(gy Bluetooth); bx=${1%.*}; by=${2%.*}; set -- $(gy "$first"); fy=${2%.*}
sh_ input swipe $bx $by $bx $(( fy - 40 )) 1200; sleep 1.5
set -- $(gy Bluetooth); nby=${2%.*}; set -- $(gy "$first"); nfy=${2%.*}
check "drag moves Bluetooth above $first" '[ -n "$nby" ] && [ "$nby" -lt "$nfy" ]'
tap_text "Add item"; sleep 1.2
# the list offers only what is not shown yet: take the first slider it lists (Brightness, Alarm volume, ...)
sl=$(dump | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -B1 -x Slider | head -1)
check "Add item lists sliders ($sl)" '[ -n "$sl" ] && [ "$sl" != Slider ]'
tap_text "$sl"; sleep 1.2
check "$sl slider added" 'desc "Drag $sl"'
tap_text "Add item"; sleep 1.2; tap_text "Flight mode"; sleep 1.2
check "Flight mode row added" 'desc "Drag Flight mode"'
tap_desc "Edit toggles"; sleep 1.2
check "toggles editor" 'desc "Toggles editor"'
check "editor lists available" 'dump | grep -q "text=\"Available\""'
shot status_toggles_editor
tap_text Done; sleep 1; tap_text Done; sleep 1.2
check "back to normal mode" '! dump | grep -q "text=\"Add item\""'
check "brightness in menu" 'desc Brightness'
yof() { dump | grep -o "\(text\|content-desc\)=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]" | head -1 | grep -o "[0-9]*\]$" | tr -d "]"; }
bry=$(yof Brightness); bty=$(yof Bluetooth)
check "brightness sits under Volume, above the rows (mock 1a)" '[ -n "$bry" ] && [ -n "$bty" ] && [ "$bry" -lt "$bty" ]'
# Hidden rows: 3 visible rows max, reveal by handle
sh_ input keyevent BACK; sleep 1
sh_ am start -n $LAUNCHER/.SettingsActivity --es section status >/dev/null; sleep 2
tap_text "Visible rows"; sleep 1; tap_text "3"; sleep 1; sh_ input keyevent BACK; sleep 1
sh_ am force-stop $LAUNCHER; sleep 1; menu
check "4 rows, 3 visible: the more handle" 'desc "More rows"'
check "hidden row not shown" '! dump | grep -q "text=\"Flight mode\""'
shot status_handle
sh_ settings put secure etesian_extra_music 0; sh_ am force-stop $LAUNCHER; sleep 1; menu
check "no Settings button or divider at the end (mock 1)" '! dump | grep -q "text=\"Settings\""'
shot status_brightness
tap_desc "More rows"; sleep 1
check "handle reveals the hidden row" 'dump | grep -q "text=\"Flight mode\""'
sh_ input keyevent BACK; sleep 1; menu
sh_ input swipe 540 700 540 1500 300; sleep 1
check "swipe down reveals too" 'dump | grep -q "text=\"Flight mode\""'
# Play on dialog (only with a media session)
if desc "Play on"; then tap_desc "Play on"; sleep 1; check "Play on dialog" 'dump | grep -q "text=\"This phone\""'; shot status_play_on; sh_ input keyevent BACK; fi
sh_ input keyevent BACK; sleep 1
sh_ cp /data/local/tmp/hl_prefs.bak $P 2>/dev/null; sh_ am force-stop $LAUNCHER
[ "$old" = null ] && sh_ settings delete secure etesian_extra_status_menu >/dev/null || sh_ settings put secure etesian_extra_status_menu "$old"
[ "$oldm" = null ] && sh_ settings delete secure etesian_extra_music >/dev/null || sh_ settings put secure etesian_extra_music "$oldm"
check "launcher did not crash" '! crashed $LAUNCHER'
home

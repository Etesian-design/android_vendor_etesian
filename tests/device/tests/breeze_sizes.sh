# Etesian settings → Breeze: handle thickness and height, floating button size (2026-11-18, user). From a bottom-bar
# button (48 dp) up to three app icons, and never more than a quarter of the screen. Raw phone pixels.
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
backup=/data/local/tmp/bsize_prefs.xml; sh_ cp $P $backup
frame() { raw_sh dumpsys window windows | grep -A40 "Window{[^ ]* u0 $1}" | grep -o "frame=\[[0-9-]*,[0-9-]*\]\[[0-9-]*,[0-9-]*\]" | head -1 | grep -o '[0-9-]\+' | tr '\n' ' '; }
D=$(raw_sh wm density | grep -o '[0-9]*' | tail -1); px() { echo $(( $1 * D / 160 )); }
RW=$(raw_sh wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1); RH=$(raw_sh wm size | grep -o 'x[0-9]*' | tail -1 | tr -d x)
setp() { sh_ am force-stop $LAUNCHER; sh_ "sed -e '/name=\"side_sense\"/d' -e '/name=\"sidebar_side\"/d' -e '/name=\"breeze_/d' -e 's#</map>#    <boolean name=\"side_sense\" value=\"true\" />\n    <string name=\"sidebar_side\">$1</string>\n    <int name=\"breeze_thickness\" value=\"$2\" />\n    <int name=\"breeze_height\" value=\"$3\" />\n    <int name=\"breeze_button_size\" value=\"$4\" />\n</map>#' $P > /data/local/tmp/bs.xml; cat /data/local/tmp/bs.xml > $P; rm /data/local/tmp/bs.xml"; home; sleep 2; }
unlock
setp right 20 100 56
read L T R B <<< "$(frame EtesianBreeze)"
check "height 100 dp ($((B-T)) px)" '[ $((B-T)) -eq $(px 100) ]'
check "thickness 20 dp: a wider window ($((R-L)) px)" '[ $((R-L)) -ge $(( $(px 20) + $(px 20) )) ]'
setp right 12 9999 56
read L T R B <<< "$(frame EtesianBreeze)"
check "height never past a quarter of the screen ($((B-T)) <= $((RH/4)))" '[ $((B-T)) -le $((RH/4)) ]'
setp right 12 10 56
read L T R B <<< "$(frame EtesianBreeze)"
check "height never under a bottom-bar button (48 dp: $((B-T)))" '[ $((B-T)) -ge $(( $(px 48) - 1 )) ]'
setp free 12 140 120
read L T R B <<< "$(frame EtesianBreeze)"
check "button 120 dp ($((R-L)) px), square" '[ $((R-L)) -eq $(px 120) ] && [ $((B-T)) -eq $(px 120) ]'
setp free 12 140 9999
read L T R B <<< "$(frame EtesianBreeze)"
check "button never past a quarter of the screen ($((R-L)) <= $((RW/4)))" '[ $((R-L)) -le $((RW/4)) ]'
# The sliders on the settings page.
sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section sidesense >/dev/null; sleep 2.5
for t in "Handle thickness" "Handle height" "Floating button size"; do check "slider: $t" "tap_text '$t'"; done
check "sliders are N9 settings rows, no dialog" '! sh_ dumpsys window windows | grep -q "mCurrentFocus.*Dialog"'
sh_ input keyevent BACK; sleep 0.5
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
check "launcher did not crash" '! crashed $LAUNCHER'

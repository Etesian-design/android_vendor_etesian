# Breeze: long-press the edge bar and drag it into the screen: it becomes a floating button that stays there;
# a tap opens Breeze's menu; dragged back to an edge it is the bar again. (Raw phone pixels: windows are
# measured by dumpsys, so no mapping.)
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
pref() { sh_ cat $P | grep -o "name=\"$1\">[^<]*" | cut -d'>' -f2; }
frame() { raw_sh dumpsys window windows | grep -A40 "Window{[^ ]* u0 $1}" | grep -o "frame=\[[0-9-]*,[0-9-]*\]\[[0-9-]*,[0-9-]*\]" | head -1 | grep -o '[0-9-]\+' | tr '\n' ' '; }
has() { sh_ dumpsys window windows | grep -q "u0 $1}"; }
drag() { raw_sh "input motionevent DOWN $1 $2; sleep 1.2; input motionevent MOVE $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); input motionevent MOVE $3 $4; sleep 0.3; input motionevent UP $3 $4"; }
backup=/data/local/tmp/breeze_prefs.xml; sh_ cp $P $backup
setp() { sh_ am force-stop $LAUNCHER; sh_ "sed -e '/name=\"side_sense\"/d' -e '/name=\"sidebar_side\"/d' -e 's#</map>#    <boolean name=\"side_sense\" value=\"true\" />\n    <string name=\"sidebar_side\">right</string>\n</map>#' $P > /data/local/tmp/bp.xml; cat /data/local/tmp/bp.xml > $P; rm /data/local/tmp/bp.xml"; home; sleep 2; }
unlock; setp
check "Breeze on: the bar is shown" 'has EtesianBreeze'
read L T R B <<< "$(frame EtesianBreeze)"; RW=$(raw_sh wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1); RH=$(raw_sh wm size | grep -o 'x[0-9]*' | tail -1 | tr -d x)
check "bar on the right edge" '[ -n "$R" ] && [ "$R" -ge $((RW - 2)) ]'
drag $(( (L+R)/2 )) $(( (T+B)/2 )) $((RW/2)) $((RH/2)); sleep 1.5
check "dragged into the screen: saved as the floating button" '[ "$(pref sidebar_side)" = free ]'
read L T R B <<< "$(frame EtesianBreeze)"
check "button is square" '[ -n "$B" ] && [ $((R-L)) -eq $((B-T)) ]'
check "button where it was let go" '[ -n "$B" ] && [ $(( (L+R)/2 - RW/2 )) -le 40 ] && [ $(( RW/2 - (L+R)/2 )) -le 40 ]'
shot breeze_button
cx=$(( (L+R)/2 )); cy=$(( (T+B)/2 ))
raw_sh input tap $cx $cy; sleep 1.5
check "tap: Breeze menu" 'has EtesianBreezeMenu'
shot breeze_menu
read L T R B <<< "$(frame EtesianBreezeMenu)"
check "menu under the button" '[ -n "$T" ] && [ "$T" -gt $cy ]'
raw_sh input tap $((RW/2)) $((RH/8)); sleep 1
check "tap elsewhere closes the menu" '! has EtesianBreezeMenu'
drag $cx $cy 5 $((RH/3)); sleep 1.5
check "dragged to the left edge: the bar there" '[ "$(pref sidebar_side)" = left ]'
read L T R B <<< "$(frame EtesianBreeze)"
check "bar on the left edge, slim" '[ "$L" = 0 ] && [ $((R-L)) -lt $((B-T)) ]'
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
check "launcher did not crash" '! crashed $LAUNCHER'

# Etesian settings → Breeze → Add and arrange buttons (2026-11-18, user): Breeze's strip as it will look at the
# bottom, every button there is to add above it, grouped as the add menu groups them. Long-press and drag onto the
# strip to add (where it is let go), along it to move, off it to remove; a tap adds at the end. Raw phone pixels.
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
cfg=$(raw_sh settings get secure etesian_toolbar_config)
raw_sh settings put secure etesian_toolbar_config "'$(echo "$cfg" | python3 -c "import json,sys; d=json.load(sys.stdin) if sys.stdin else {}; d.pop('breeze',None); print(json.dumps(d))" 2>/dev/null || echo '{}')'"
labels() { raw_sh settings get secure etesian_toolbar_config | python3 -c "import json,sys; print(','.join(b.get('label','') for b in json.load(sys.stdin).get('breeze',[])))" 2>/dev/null; }
xml() { raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; command adb shell cat /sdcard/u.xml | tr -d '\r'; }
# Centre (raw px) of the element whose text or content-desc is exactly $1.
at() { xml | grep -o "\(text\|content-desc\)=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ' | { read a b c d; [ -n "$d" ] && echo "$(( (a+c)/2 )) $(( (b+d)/2 ))"; }; }
box() { xml | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
resumed() { sh_ dumpsys activity activities | grep -m1 "ResumedActivity" | grep -o '[A-Za-z]*Activity' | tail -1; }
# Long-press at ($1,$2), drag to ($3,$4) in steps, let go.
ddrag() { raw_sh "input motionevent DOWN $1 $2; sleep 1.0; for i in 1 2 3 4 5 6 7 8; do input motionevent MOVE \$(( $1 + ($3-$1)*i/8 )) \$(( $2 + ($4-$2)*i/8 )); sleep 0.05; done; sleep 0.5; input motionevent UP $3 $4"; sleep 1.5; }
unlock
sh_ am start -n $LAUNCHER/.SettingsActivity --es section sidesense >/dev/null; sleep 2.5
tap_text "Add and arrange buttons"; sleep 2.5
check "opens the editor" '[ "$(resumed)" = BreezeButtonsActivity ]'
shot breeze_editor
for t in "System buttons" "Settings pages" "Macros"; do check "category: $t" '[ -n "$(at "$t")" ]'; done
read SL ST SR SB <<< "$(box "Breeze strip")"
check "the strip at the bottom, under the buttons" '[ -n "$SB" ] && [ $ST -gt $(( $(raw_sh wm size | grep -o "x[0-9]*" | tail -1 | tr -d x) * 2 / 3 )) ]'
check "the strip shows Breeze's buttons (defaults)" '[ -n "$(at "On Breeze: Back")" ] && [ -n "$(at "On Breeze: Home")" ] && [ -n "$(at "On Breeze: Open applications")" ]'
# Drag Menu onto the strip's left end: it goes first.
read mx my <<< "$(at "Add Menu")"
ddrag $mx $my $((SL + 20)) $(( (ST+SB)/2 ))
check "dragged onto the strip's start: added first ($(labels))" '[ "$(labels)" = "Menu,Back,Home,Open applications" ]'
# Drag Home off the strip, up into the list: removed.
read hx hy <<< "$(at "On Breeze: Home")"
ddrag $hx $hy $hx $((ST - 600))
check "dragged off the strip: removed ($(labels))" '[ "$(labels)" = "Menu,Back,Open applications" ]'
# Drag Back along the strip to its end.
read bx by <<< "$(at "On Breeze: Back")"
ddrag $bx $by $((SR - 20)) $by
check "dragged along the strip: moved to the end ($(labels))" '[ "$(labels)" = "Menu,Open applications,Back" ]'
# Tap adds at the end; the strip holds five.
read wx wy <<< "$(at "Add Wi-Fi")"; raw_sh input tap $wx $wy; sleep 1
read wx wy <<< "$(at "Add Bluetooth")"; raw_sh input tap $wx $wy; sleep 1
check "taps add at the end ($(labels))" '[ "$(labels)" = "Menu,Open applications,Back,Wi-Fi,Bluetooth" ]'
read wx wy <<< "$(at "Add Display")"; raw_sh input tap $wx $wy; sleep 1
check "a sixth is refused (Breeze holds five)" '[ "$(labels)" = "Menu,Open applications,Back,Wi-Fi,Bluetooth" ] && xml | grep -q "Breeze holds 5 buttons"'
# Apps, grouped (loaded in the background), further down.
check "apps listed" 'for _ in 1 2 3 4 5 6; do [ -n "$(at Apps)" ] && break; raw_sh input swipe 700 1500 700 700 200; sleep 0.7; done; [ -n "$(at Apps)" ]'
sh_ input keyevent BACK; sleep 1.5
check "Back: the Breeze page again, listing the new buttons" '[ "$(resumed)" = SettingsActivity ] && tap_text Bluetooth'
sh_ input keyevent BACK; sleep 0.5
[ "$cfg" = null ] && raw_sh settings delete secure etesian_toolbar_config >/dev/null || raw_sh settings put secure etesian_toolbar_config "'$cfg'"
home
check "launcher did not crash" '! crashed $LAUNCHER'

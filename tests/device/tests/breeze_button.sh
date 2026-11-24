# Breeze: a thick handle clear of the curved edge's grip strip. Held still it picks up (also when the touch only
# arrives after creeping in from the edge) and drags: away from the edge it turns into the floating button, back
# at an edge into the handle. Every gesture vibrates. Its menu slides out of Breeze's side, flush with it, its top
# level with Breeze; buttons on a strip, nothing to edit. Hidden on the lock screen; light or dark.
# (Raw phone pixels: windows are measured by dumpsys, so no mapping.)
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
pref() { sh_ cat $P | grep -o "name=\"$1\">[^<]*" | cut -d'>' -f2; }
frame() { raw_sh dumpsys window windows | grep -A40 "Window{[^ ]* u0 $1}" | grep -o "frame=\[[0-9-]*,[0-9-]*\]\[[0-9-]*,[0-9-]*\]" | head -1 | grep -o '[0-9-]\+' | tr '\n' ' '; }
has() { sh_ dumpsys window windows | grep -q "u0 $1}"; }
# The newest of the launcher's vibrations (its time). Not a count: the history keeps only the latest entries, so once
# it is full (after earlier tests) a new vibration drops an old one and a count stays the same.
buzzes() { raw_sh dumpsys vibrator_manager | grep "$LAUNCHER" | grep -o '^ *[0-9-]* [0-9:.]*' | sort | tail -n 1 | tr -d ' '; }
grey() { command adb exec-out screencap -p | python3 -c "import sys,io;from PIL import Image;im=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('L');print(im.getpixel(($1,$2)))"; }
D=$(raw_sh wm density | grep -o '[0-9]*' | tail -1); px() { echo $(( $1 * D / 160 )); }
RW=$(raw_sh wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1); RH=$(raw_sh wm size | grep -o 'x[0-9]*' | tail -1 | tr -d x)
# Held still at ($1,$2), then dragged to ($3,$4).
drag() { raw_sh "input motionevent DOWN $1 $2; sleep 1.0; input motionevent MOVE $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 0.2; input motionevent MOVE $3 $4; sleep 0.6; input motionevent UP $3 $4"; }
backup=/data/local/tmp/breeze_prefs.xml; sh_ cp $P $backup
cfg=$(raw_sh settings get secure etesian_toolbar_config)
raw_sh settings put secure etesian_toolbar_config "'$(echo "$cfg" | python3 -c "import json,sys; d=json.load(sys.stdin) if sys.stdin else {}; d.pop('breeze',None); print(json.dumps(d))" 2>/dev/null || echo '{}')'"
# Default sizes: the samples below are placed for the default handle (the user may have resized it).
setp() { sh_ am force-stop $LAUNCHER; sh_ "sed -e '/name=\"side_sense\"/d' -e '/name=\"sidebar_side\"/d' -e '/name=\"breeze_theme\"/d' -e '/name=\"breeze_thickness\"/d' -e '/name=\"breeze_height\"/d' -e '/name=\"breeze_button_size\"/d' -e 's#</map>#    <boolean name=\"side_sense\" value=\"true\" />\n    <string name=\"sidebar_side\">right</string>\n    <string name=\"breeze_theme\">$1</string>\n</map>#' $P > /data/local/tmp/bp.xml; cat /data/local/tmp/bp.xml > $P; rm /data/local/tmp/bp.xml"; home; sleep 2; }
unlock; setp light
check "Breeze on: the handle is shown" 'has EtesianBreeze'
read L T R B <<< "$(frame EtesianBreeze)"
check "handle on the right edge" '[ -n "$R" ] && [ "$R" -ge $((RW - 2)) ]'
check "handle window thick (over 30 dp)" '[ -n "$L" ] && [ $((R-L)) -ge $(px 30) ]'
HY=$(( (T+B)/2 )); HX=$((RW - $(px 20)))
# Drawn 14-26 dp in from the edge (12 dp thick; 2026-11-24, owner: a thicker handle grows out over the curve, its inner
# edge stays at 26 dp): that inner edge is outside the controller's 60 px grip strip, and the touch area reaches past it.
# The strip is the OnePlus 7 Pro's (guacamoleg) touch controller's; other phones have none.
[ "$(sh_ getprop ro.product.device)" = guacamoleg ] && \
check "handle's inner edge ($(px 26) px in) outside the 60 px grip strip" '[ $(px 26) -gt 60 ]'
light=$(grey $HX $HY); setp dark; dark=$(grey $HX $HY)
check "theme: light handle light ($light), dark handle dark ($dark)" '[ "$light" -gt 180 ] && [ "$dark" -lt 90 ]'
setp light
b0=$(buzzes); raw_sh input tap $HX $HY; sleep 1.5
check "tap on the handle: Breeze menu" 'has EtesianBreezeMenu'
check "tap felt (vibration)" '[ "$(buzzes)" != "$b0" ]'
read ML MT MR MB <<< "$(frame EtesianBreezeMenu)"
check "menu against the right side (no gap)" '[ -n "$MR" ] && [ "$MR" -ge $((RW - 1)) ]'
check "menu's top level with the handle ($MT vs $T)" '[ -n "$MT" ] && [ $((MT - T)) -le 2 ] && [ $((T - MT)) -le 2 ]'
check "menu takes touches" '! raw_sh dumpsys input | grep "EtesianBreezeMenu, " | grep -q NOT_TOUCHABLE'
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "no editing in the menu (no Add widget, no Edit)" '! sh_ cat /sdcard/u.xml | grep -q "Add widget\|Edit Breeze"'
shot breeze_menu
raw_sh input tap $((RW/2)) $((RH/8)); sleep 1
check "tap elsewhere closes the menu" '! has EtesianBreezeMenu'
# 2026-11-18 (user): with the menu open the screen behind is dimmed and inactive, Breeze too: a tap outside only
# closes the menu, it never reaches the app.
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2.5
act0=$(sh_ dumpsys activity activities | grep -m1 ResumedActivity | grep -o '/[A-Za-z.$]*')
PX=$((RW/4)); PY=$((RH*3/5))
# Mean brightness of the left half (text on the app's page), where the menu never is.
mean() { command adb exec-out screencap -p | python3 -c "import sys,io;from PIL import Image,ImageStat;im=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('L');w,h=im.size;print(int(ImageStat.Stat(im.crop((0,h//10,w//3,h*9//10))).mean[0]*100))"; }
g0=$(mean)
raw_sh input tap $HX $HY; sleep 1.5
g1=$(mean); gh=$(grey $HX $HY)
check "menu open: the screen behind dimmed ($g0 -> $g1)" 'has EtesianBreezeScrim && [ $g1 -lt $((g0 * 3 / 4)) ]'
check "menu open: Breeze steps back (handle $gh, was $light)" '[ "$gh" -lt 150 ]'
shot breeze_menu_dim
raw_sh input tap $PX $PY; sleep 1.5
act1=$(sh_ dumpsys activity activities | grep -m1 ResumedActivity | grep -o '/[A-Za-z.$]*')
check "a tap on the app behind: the menu closes, the app is not touched ($act0 / $act1)" '! has EtesianBreezeMenu && ! has EtesianBreezeScrim && [ "$act0" = "$act1" ]'
check "Breeze back after" '[ $(grey $HX $HY) -gt 180 ]'
raw_sh input tap $HX $HY; sleep 1.5
check "Breeze's tap opens it again" 'has EtesianBreezeMenu'
# Open, the menu covers Breeze (flush with its side, level with it): Breeze can't be reached until it closes.
read ML MT MR MB <<< "$(frame EtesianBreezeMenu)"
check "the open menu covers Breeze (handle $HX,$HY; menu $ML $MT $MR $MB)" '[ $HX -ge $ML ] && [ $HY -ge $MT ] && [ $HY -le $MB ]'
raw_sh input tap $PX $PY; sleep 1.5
check "closed again" '! has EtesianBreezeMenu'
home
b0=$(buzzes); raw_sh input swipe $HX $HY $HX $((HY - $(px 80))) 120; sleep 1.5
check "flick felt (vibration)" '[ "$(buzzes)" != "$b0" ]'
home
b0=$(buzzes)
raw_sh "input motionevent DOWN $HX $HY; sleep 1.0; input motionevent MOVE $((RW/2)) $((RH/2)); sleep 0.1"
command adb exec-out screencap -p > "$SHOTS/breeze_morph_early.png"
check "long press: picked up (drag layer)" 'has EtesianBreezeDrag'
check "long press felt (vibration)" '[ "$(buzzes)" != "$b0" ]'
sleep 0.6; command adb exec-out screencap -p > "$SHOTS/breeze_morph_done.png"
raw_sh "input motionevent UP $((RW/2)) $((RH/2))"; sleep 1.5
check "the button turns up slowly (still changing after the first moment)" '/home/memphisx/android/.venv-tools/bin/python -c "
import sys
from PIL import Image
a=Image.open(\"$SHOTS/breeze_morph_early.png\").convert(\"L\"); b=Image.open(\"$SHOTS/breeze_morph_done.png\").convert(\"L\")
r=$(px 24); box=($RW//2-r,$RH//2-r,$RW//2+r,$RH//2+r)
A=list(a.crop(box).getdata()); B=list(b.crop(box).getdata())
sys.exit(0 if sum(abs(x-y) for x,y in zip(A,B))/len(A)>3 else 1)"'
check "let go in the middle: the floating button" '[ "$(pref sidebar_side)" = free ] && ! has EtesianBreezeDrag'
# The icon's green leaf (in both versions) is on screen once Breeze floats; Android's robot has none.
sleep 0.5; command adb exec-out screencap -p > "$SHOTS/breeze_icon.png"
check "the floating button shows Etesian's icon (its leaf)" 'python3 -c "
import sys; from PIL import Image
im=Image.open(\"$SHOTS/breeze_icon.png\").convert(\"RGB\")
n=sum(1 for r,g,b in im.getdata() if 80<=r<=175 and g>=140 and b<=90 and g-r>=35)
print(n, file=sys.stderr); sys.exit(0 if n>=30 else 1)"'
read L T R B <<< "$(frame EtesianBreeze)"
check "button square, where it was let go" '[ -n "$B" ] && [ $((R-L)) -eq $((B-T)) ] && [ $(( (L+R)/2 - RW/2 )) -le 40 ] && [ $(( RW/2 - (L+R)/2 )) -le 40 ]'
cx=$(( (L+R)/2 )); cy=$(( (T+B)/2 ))
raw_sh input tap $cx $cy; sleep 1.5
read ML MT MR MB <<< "$(frame EtesianBreezeMenu)"
check "button's menu: against a side, top level with the button" '[ -n "$MT" ] && { [ "$ML" = 0 ] || [ "$MR" -ge $((RW-1)) ]; } && [ $((MT - T)) -le 2 ] && [ $((T - MT)) -le 2 ]'
raw_sh input tap $((RW/2)) $((RH/8)); sleep 1; sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2
raw_sh input tap $cx $cy; sleep 1.5
read ML MT MR MB <<< "$(frame EtesianBreezeMenu)"
raw_sh input tap $(( ML + (MR - ML) / 2 )) $(( MT + $(px 28) )); sleep 1.5
check "a menu button runs (Home, the middle default)" 'sh_ dumpsys activity activities | grep -m1 "ResumedActivity" | grep -q HomeActivity'
drag $cx $cy 5 $((RH/3)); sleep 1.5
check "dragged to the left edge: the handle there" '[ "$(pref sidebar_side)" = left ]'
read L T R B <<< "$(frame EtesianBreeze)"
HY=$(( (T+B)/2 ))
# The controller hands a touch over only once it has crept in: it lands moving, then rests.
raw_sh "input motionevent DOWN $(px 4) $HY; input motionevent MOVE $(px 10) $HY; input motionevent MOVE $(px 18) $HY; sleep 1.0"
check "crept in, then held: picked up" 'has EtesianBreezeDrag'
raw_sh "input motionevent UP $(px 18) $HY"; sleep 1.5
check "let go at the edge: the handle again" '[ "$(pref sidebar_side)" = left ] && has EtesianBreeze'
lock
check "locked: Breeze hidden" '! has EtesianBreeze'
unlock; sleep 1.5
check "unlocked: Breeze back" 'has EtesianBreeze'
[ "$cfg" = null ] && raw_sh settings delete secure etesian_toolbar_config >/dev/null || raw_sh settings put secure etesian_toolbar_config "'$cfg'"
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
check "launcher did not crash" '! crashed $LAUNCHER'
# 2026-11-18 (user): the floating button is Etesian's icon (brand/, deep on dark, pearl on light), not Android's robot.
S=~/android/lineage/vendor/etesian/launcher/app/src
check "the button draws Etesian's icon, both versions" 'grep -q "R.drawable.etesian_icon_deep" $S/platform/java/com/bompotis/etesian/launcher/platform/SideBar.kt && grep -q "R.drawable.etesian_icon_pearl" $S/platform/java/com/bompotis/etesian/launcher/platform/SideBar.kt && ! grep -q "ic_launcher_foreground" $S/platform/java/com/bompotis/etesian/launcher/platform/SideBar.kt && test -s $S/main/res/drawable-nodpi/etesian_icon_deep.png'
# 2026-11-18 (user): the launcher's own app icon is Etesian's (deep), not Android Studio's robot.
check "the launcher's app icon is Etesian's" 'command adb shell pm dump com.bompotis.etesian.launcher >/dev/null && grep -q "ic_launcher_etesian_fg" $S/main/res/mipmap-anydpi-v26/ic_launcher.xml && ! ls $S/main/res/drawable/ic_launcher_foreground.xml 2>/dev/null'

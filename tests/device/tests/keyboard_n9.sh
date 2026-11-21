# 2026-11-18 (user): N9 keyboard: lower-case letters (shift once = one capital), caps lock by a double tap on shift
# (the key turns plain white), a globe between the comma and the space bar, a click on every key, keys lit like the
# icons and darker when pressed. And no white bar above the SMS composer.
# Raw 1440x3120 coordinates (command adb), from the keyboard as drawn on this phone.
L=~/android/lineage/packages/inputmethods/LatinIME
xml() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# The node's text comes before its resource-id in the dump: read the whole node.
field() { xml | grep -o '<node [^>]*resource-id="com.android.messaging:id/compose_message_text"[^>]*' | grep -o ' text="[^"]*"' | cut -d'"' -f2; }
tapr() { command adb shell input tap $1 $2; sleep 0.45; }
unlock
sh_ am start -a android.intent.action.SENDTO -d sms:5550100 -p com.android.messaging >/dev/null; sleep 3
# No white bar: the gap view above the composer is gone (0 px high).
TM=$(xml | grep -o 'resource-id="com.android.messaging:id/top_margin"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"')
check "no gap above the SMS composer ($TM)" 'echo "$TM" | python3 -c "
import sys,re; b=sys.stdin.read(); m=re.findall(r\"\\d+\",b); sys.exit(0 if not m or int(m[3])-int(m[1])==0 else 1)"'
CB=$(xml | grep -o 'resource-id="com.android.messaging:id/compose_message_text"[^>]*bounds="[^"]*"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
CY=$(echo "$CB" | python3 -c "import sys,re;n=list(map(int,re.findall(r'\d+',sys.stdin.read())));print((n[1]+n[3])//2)")
sh_ input tap 540 $CY; sleep 1.5  # from the UI dump: mapped coordinates
check "keyboard shown" 'sh_ dumpsys input_method | grep -q "mInputShown=true"'
command adb exec-out screencap -p > "$SHOTS/keyboard_n9.png"
# Key rows from the screenshot: the black keyboard area's key centres.
read QX QY AX AY SX SY CX CY GX GY SPX SPY <<<"$(python3 - "$SHOTS/keyboard_n9.png" <<'PY'
import sys
from PIL import Image
im=Image.open(sys.argv[1]).convert('L'); w,h=im.size
# keyboard top: first row from the bottom up where the whole width is near black between keys
col=[im.getpixel((w//2,y)) for y in range(h)]
rows=[];y=h-1
# key rows: runs of rows where pixel at x=w*0.05 (first key) is lit (> 20)
x=int(w*0.06); run=None
for y in range(h//2,h):
    v=im.getpixel((x,y))>18
    if v and run is None: run=y
    if not v and run is not None:
        if y-run>40: rows.append((run+y)//2)
        run=None
if run is not None and h-run>40: rows.append((run+h)//2)
rows=rows[-4:]
r1,r2,r3,r4=rows
print(int(w*0.05),r1, int(w*0.10),r2, int(w*0.07),r3, int(w*0.20),r4, int(w*0.30),r4, int(w*0.55),r4)
PY
)"
echo "  rows: q($QX,$QY) a($AX,$AY) shift($SX,$SY) comma($CX,$CY) globe($GX,$GY) space($SPX,$SPY)"
check "keys lit like the icons: a key is lighter at its top than its bottom" 'python3 -c "
from PIL import Image; im=Image.open(\"$SHOTS/keyboard_n9.png\").convert(\"L\")
x=$QX+0; y=$QY; t=im.getpixel((x-22,y-50)); b=im.getpixel((x-22,y+45)); print(t,b); import sys; sys.exit(0 if t>b+6 else 1)"'
check "the globe sits between the comma and the space bar" 'grep -q "THEME_ID_N9) {" $L/java/src/com/android/inputmethod/latin/LatinIME.java && python3 -c "
from PIL import Image; im=Image.open(\"$SHOTS/keyboard_n9.png\").convert(\"L\")
import sys; v=im.getpixel(($GX,$GY)); g=im.getpixel(($GX - 72,$GY)); print(v,g); sys.exit(0 if v>25 else 1)"'
# Typing: first letter capital (start of a sentence), then lower case.
tapr $QX $QY; tapr $QX $QY
check "letters are lower case after the first (got \"$(field)\")" '[ "$(field)" = "Qq" ]'
# Shift once: one capital, then lower case again.
tapr $SX $SY; tapr $QX $QY; tapr $QX $QY
check "shift once: one capital only (got \"$(field)\")" '[ "$(field)" = "QqQq" ]'
# Double tap: caps lock, the shift key plain white.
command adb shell "input tap $SX $SY; input tap $SX $SY"; sleep 0.8
command adb exec-out screencap -p > "$SHOTS/keyboard_capslock.png"
check "caps lock: the shift key is plain white" 'python3 -c "
from PIL import Image; im=Image.open(\"$SHOTS/keyboard_capslock.png\").convert(\"RGB\")
p=im.getpixel(($SX - 40,$SY - 30)); print(p); import sys; sys.exit(0 if min(p)>=245 else 1)"'
tapr $QX $QY; tapr $QX $QY
check "caps lock keeps capitals (got \"$(field)\")" '[ "$(field)" = "QqQqQQ" ]'
tapr $SX $SY
# Pressed keys sink in: the pressed colours are darker than the resting ones.
check "pressed keys are darker (recessed)" 'grep -q "key_background_pressed_top_n9\">#0B0B0B" $L/java/res/values/colors-n9.xml'
# Sound: on by default; the N9 click is the system keypress sound.
check "key clicks on by default" '! sh_ "cat /data/user_de/0/com.android.inputmethod.latin/shared_prefs/*.xml 2>/dev/null; cat /data/data/com.android.inputmethod.latin/shared_prefs/*.xml 2>/dev/null" | grep -q "name=\"sound_on\" value=\"false\"" && grep -q "config_default_sound_enabled\">true" $L/java/res/values/config-per-form-factor.xml'
# 2026-11-18 (user): two sounds, as on the N9: a lower click when a key goes down, a higher one when it comes up
# (holding a key plays only the first). Release is FX_KEYPRESS_INVALID, mapped to the release click.
check "Etesian's press and release clicks are installed" 'sh_ ls /product/media/audio/ui/etesian/vkb_press.wav /product/media/audio/ui/etesian/vkb_release.wav >/dev/null'
check "the release click is the finger lifting, not the key's release event" 'grep -q "performReleaseAudioFeedback();" $L/java/src/com/android/inputmethod/keyboard/PointerTracker.java && ! grep -q "performReleaseAudioFeedback();" $L/java/src/com/android/inputmethod/latin/LatinIME.java && grep -q "FX_KEYPRESS_INVALID" $L/java/src/com/android/inputmethod/latin/AudioAndHapticFeedbackManager.java'
check "the sound overlays map press and release to two different sounds" 'grep -q "FX_KEYPRESS_STANDARD\" file=\"etesian/vkb_press.wav" ~/android/lineage/vendor/etesian/overlay/EtesianSounds/res/xml/audio_assets.xml && grep -q "FX_KEYPRESS_INVALID\" file=\"etesian/vkb_release.wav" ~/android/lineage/vendor/etesian/overlay/EtesianSounds/res/xml/audio_assets.xml && grep -q "FX_KEYPRESS_INVALID\" file=\"n9/static_release.wav" ~/android/lineage/vendor/etesian/n9-files/sounds-overlay/res/xml/audio_assets.xml'
# The globe with one language: offers the languages and keyboards.
tapr $GX $GY; sleep 1
check "globe opens the languages / keyboards choice" 'sh_ dumpsys window windows | grep -qi "mCurrentFocus.*\(latin\|InputMethod\|Dialog\|chooser\)" || xml | grep -qi "Language\|input method\|keyboard"'
command adb shell input keyevent BACK; sleep 0.5
# 2026-11-19 (user): the release click follows the finger: "if you press and hold you only hear the first sound and if
# you release way later you still hear the second." Count the clicks the keyboard plays (it logs "in" / "out", never
# the key) for a quick tap, a long hold (the accents panel), a held backspace (repeating) and a hold on the space bar.
# BACK after the chooser can close the composer too: open it again, as at the start, until the keyboard is up.
for _ in 1 2 3; do sh_ dumpsys input_method | grep -q "mInputShown=true" && break
  sh_ dumpsys window | grep -q "mCurrentFocus.*messaging" || { sh_ am start -a android.intent.action.SENDTO -d sms:5550100 -p com.android.messaging >/dev/null; sleep 3; }
  sh_ input tap 540 $CY; sleep 1.5; done
check "the keyboard is up again for the click counts" 'sh_ dumpsys input_method | grep -q "mInputShown=true"'
clicks() { command adb logcat -c; command adb shell "$1"; sleep 0.6; command adb logcat -d -s EtesianKeyClick:V | grep -c " $2\$"; }
# The keyboard plays no click on a silent ringer, and run.sh silences it: normal ringer for the counts (Do Not
# Disturb, total silence, still keeps them inaudible), silent again after.
sh_ cmd audio set-ringer-mode NORMAL; sleep 1
BX=$(( QX * 186 / 10 )); BY=$SY   # backspace: the right end of the shift row
n=$(clicks "input tap $QX $QY" out); i=$(clicks "input tap $QX $QY" in)
check "a quick tap: one press click and one release click (in $i, out $n)" '[ "$i" = 1 ] && [ "$n" = 1 ]'
n=$(clicks "input swipe $QX $QY $QX $QY 1600" out)
check "a long hold (accents panel): the release click when the finger lifts ($n)" '[ "$n" = 1 ]'
command adb logcat -c; command adb shell "input swipe $QX $QY $QX $QY 1600"; sleep 0.6; hi=$(command adb logcat -d -s EtesianKeyClick:V | grep -c " in$")
check "and only the press click while held ($hi)" '[ "$hi" = 1 ]'
n=$(clicks "input swipe $BX $BY $BX $BY 1600" out)
check "a held backspace (repeating): one release click when let go ($n)" '[ "$n" = 1 ]'
n=$(clicks "input swipe $SPX $SPY $SPX $SPY 1600" out)
check "a hold on the space bar: the release click too ($n)" '[ "$n" = 1 ]'
sh_ cmd audio set-ringer-mode SILENT
# Clean up the draft.
for _ in 1 2 3 4 5 6 7 8; do command adb shell input keyevent DEL; done
command adb shell input keyevent BACK; command adb shell input keyevent BACK
home

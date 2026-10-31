# Task: the keyboard hosts Messaging's N9 white strip: one white line above the keys with attach and
# emoticon squircles, the suggestions, the "154(1)" counter and a black Send (editor action SEND).
# Nothing is sent; the typed draft is cleared again.
IME=/home/memphisx/android/lineage/packages/inputmethods/LatinIME/java
MSG=/home/memphisx/android/lineage/packages/apps/Messaging
SV=$IME/src/com/android/inputmethod/latin/suggestions/SuggestionStripView.java
CV=$MSG/src/com/android/messaging/ui/conversation/ComposeMessageView.java
check "source: keyboard strip and composer exist" "[ -f $SV ] && [ -f $IME/res/layout/suggestions_strip.xml ] && [ -f $CV ]"
check "source: Send only for IME_ACTION_SEND" "grep -q 'IME_ACTION_SEND' $SV"
check "source: Messaging asks for the strip and hands it over" "grep -q 'etesian.strip=attach,emoticon' $MSG/res/layout/compose_message_view.xml && grep -q 'etesian.strip_hosted' $CV"
check "source: 52dp strip only in the N9 theme, 40dp elsewhere" "grep -q 'config_suggestions_strip_height\">40dp' $IME/res/values/config.xml && grep -q 'etesianStripHeight\">@dimen/config_suggestions_strip_height_n9' $IME/res/values/themes-n9.xml && grep -q 'strip_height_n9\">52dp' $IME/res/values/themes-n9-dimens.xml"
PY=${PY:-/home/memphisx/android/.venv-tools/bin/python}
M=com.android.messaging; K=com.android.inputmethod.latin
ime_up() { sh_ dumpsys input_method | grep -q "mInputShown=true"; }
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
wdump() { sh_ uiautomator dump --windows /sdcard/w.xml >/dev/null 2>&1; sh_ cat /sdcard/w.xml; }
bounds() { grep -o "<node[^>]*id/$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '; }
tap_b() { read x1 y1 x2 y2 <<< "$1"; sh_ input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); }
unlock
check "Etesian keyboard is the IME" 'sh_ settings get secure default_input_method | grep -q "^$K/"'
sh_ am force-stop $M
sh_ am start -a android.intent.action.SENDTO -d smsto: -p $M >/dev/null; sleep 3
if top_activity | grep -q $M; then
  sh_ input text 5550100; sleep 1; sh_ input keyevent ENTER; sleep 2.5
  tap_b "$(dump | bounds compose_message_text)"; sleep 1
  sh_ input text hdtest; sleep 2
  w=$(wdump); adb exec-out screencap -p > ${SHOTS:-/tmp}/kbd-strip-up.png
  check "Messaging's own strip hidden while the keyboard hosts it" '! dump | grep -q "id/n9_ime_strip"'
  check "keyboard strip: attach and emoticon" 'echo "$w" | grep -q "$K:id/etesian_strip_attach" && echo "$w" | grep -q "$K:id/etesian_strip_emoticon"'
  check "keyboard strip: counter 154(1) and Send" 'echo "$w" | grep -q "text=\"154(1)\"" && echo "$w" | grep -q "$K:id/etesian_strip_send"'
  check "keyboard strip: suggestions in it" 'echo "$w" | grep -o "<node[^>]*package=\"$K\"[^>]*>" | grep -q "text=\"hdtest\""'
  read sx1 sy1 sx2 sy2 <<< "$(echo "$w" | bounds etesian_strip_end)"
  read bx1 by1 bx2 by2 <<< "$(echo "$w" | bounds etesian_strip_send)"
  read cx1 cy1 cx2 cy2 <<< "$(echo "$w" | bounds etesian_strip_counter)"
  d1=$(( (by1 + by2) / 2 - (sy1 + sy2) / 2 )); d2=$(( (cy1 + cy2) / 2 - (by1 + by2) / 2 ))
  check "Send centred in the strip ($d1) and with the counter ($d2)" "[ \${d1#-} -le 6 ] && [ \${d2#-} -le 6 ]"
  # Column between the emoticon and the words: under the composer one white band of the strip's
  # height, then the dark keys at once (two lines would make the white band twice as tall).
  read tx1 ty1 tx2 ty2 <<< "$(dump | bounds compose_message_text)"
  r=$($PY -c "
from PIL import Image; im=Image.open('${SHOTS:-/tmp}/kbd-strip-up.png').convert('L')
x=225; y=$ty2+4
while im.getpixel((x,y))<245: y+=1
y0=y
while y<im.size[1] and im.getpixel((x,y))>=200: y+=1
print(y-y0, im.getpixel((x,min(y+6,im.size[1]-1))), $sy2-$sy1)")
  read band below sh <<< "$r"
  check "one white band ($band px, strip $sh px) then dark keys ($below)" "[ $band -ge $((sh - 12)) ] && [ $band -le $((sh + 30)) ] && [ $below -le 100 ]"
  tap_b "$(echo "$w" | bounds etesian_strip_emoticon)"; sleep 1.5
  check "emoticon from the keyboard goes into the field" 'dump | grep -o "<node[^>]*id/compose_message_text\"[^>]*>" | grep -q "text=\"[^\"]*:)"'
  ime_up && { sh_ input keyevent BACK; sleep 1.5; }
  adb exec-out screencap -p > ${SHOTS:-/tmp}/kbd-strip-down.png
  check "keyboard down: no strip anywhere" '! ime_up && ! wdump | grep -q "id/etesian_strip_send\|id/n9_ime_strip"'
  # Slide-out keyboard (simulated): no on-screen keyboard, Messaging shows its own strip.
  sh_ setprop debug.etesian.messaging_hwkbd true
  tap_b "$(dump | bounds compose_message_text)"; sleep 1.5
  ime_up && { sh_ input keyevent BACK; sleep 1.5; }
  adb exec-out screencap -p > ${SHOTS:-/tmp}/kbd-strip-hwkbd.png
  check "hardware keyboard: Messaging's own strip" '! ime_up && dump | grep -q "id/n9_ime_strip"'
  sh_ setprop debug.etesian.messaging_hwkbd false
  sh_ input keycombination KEYCODE_CTRL_LEFT KEYCODE_A; sh_ input keyevent DEL; sleep 1
  for _ in 1 2 3; do sh_ input keyevent BACK; sleep 1; done
  sh_ am force-stop $M
  check "nothing sent or drafted" '! adb shell content query --uri content://mms-sms/conversations --projection thread_id:address 2>/dev/null | grep -q 5550100'
else bad "new message opens"; fi
# Another app's field without SEND (Settings search, action SEARCH): plain suggestions, no Send.
sh_ am start -a com.android.settings.action.SETTINGS_SEARCH >/dev/null; sleep 3
ime_up || { sh_ input tap 540 150; sleep 1.5; }
sh_ input text wifi; sleep 2
w=$(wdump); adb exec-out screencap -p > ${SHOTS:-/tmp}/kbd-strip-search.png
check "search field: keyboard up with suggestions" 'ime_up && echo "$w" | grep -q "$K:id/suggestions_strip"'
check "search field: no Send, attach or emoticon" '! echo "$w" | grep -q "id/etesian_strip_send\|id/etesian_strip_attach\|id/etesian_strip_emoticon"'
sh_ input keycombination KEYCODE_CTRL_LEFT KEYCODE_A; sh_ input keyevent DEL
for _ in 1 2; do sh_ input keyevent BACK; sleep 1; done
# Leave no search keyboard behind for the next test (it stays over the next app otherwise).
sh_ am force-stop com.android.settings.intelligence; home
for _ in 1 2 3; do ime_up || break; sh_ input keyevent BACK; sleep 1; done
check "search keyboard gone afterwards" '! ime_up'
check "keyboard and messaging did not crash" '! crashed $K && ! crashed $M'
home

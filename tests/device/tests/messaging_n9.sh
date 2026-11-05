# Task: N9 Messaging — first-run page (n9-messaging-empty.png), new message sheet (n9-sms-compose.png) and
# conversation (Storybook messaging-conversation). Nothing is sent; the typed draft is cleared again.
MSG=/home/memphisx/android/lineage/packages/apps/Messaging
L=$MSG/res/layout/compose_message_view.xml; J=$MSG/src/com/android/messaging/ui/conversation/ComposeMessageView.java
check "source: compose layout and view exist" "[ -f $L ] && [ -f $J ]"
check "source: IME strip holds Send and counter" "awk '/n9_ime_strip/{f=1} f&&/send_message_button/{s=1} f&&/message_body_size/{c=1} END{exit !(s&&c)}' $L"
check "source: strip follows WindowInsets ime" "grep -q 'WindowInsets.Type.ime()' $J"
check "source: strip follows the slide keyboard" "grep -q HARDKEYBOARDHIDDEN_NO $J && grep -q messaging_hwkbd $J"
unlock
ime_up() { sh_ dumpsys input_method | grep -q "mInputShown=true"; }
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# With the Etesian keyboard up, its strip (com.android.inputmethod.latin:id/etesian_strip_*)
# hosts attach, emoticon, counter and Send; the IME window shows only in a --windows dump.
wdump() { sh_ uiautomator dump --windows /sdcard/w.xml >/dev/null 2>&1; sh_ cat /sdcard/w.xml; }
M=com.android.messaging
open_list() { sh_ am force-stop $M; sh_ monkey -p $M -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3.5; }
# First run (forced with a debug property: the user's conversations stay).
sh_ setprop debug.etesian.messaging_empty true; open_list
check "first-run sentence" 'dump | grep -q "text=\"Send texts or chat via your favourite online services\""'
# N9 has no toolbar there: the page's stripes run to the bottom edge (bottom 3% ~ the band above).
PY=${PY:-/home/memphisx/android/.venv-tools/bin/python}
edge() { adb exec-out screencap -p > /tmp/mbar.png; cp /tmp/mbar.png ${SHOTS:-/tmp}/m-firstrun.png; $PY -c "
from PIL import Image; im=Image.open('/tmp/mbar.png').convert('L'); w,h=im.size
m=lambda a,b: sum(im.crop((w//3,int(h*a),w,int(h*b))).tobytes())//((w-w//3)*(int(h*b)-int(h*a)))
print(abs(m(.975,1)-m(.94,.965)))"; }
check "first-run page: the search pill does not pop the keyboard up" '! ime_up'
d=$(edge); check "first-run page: no bottom toolbar ($d)" "[ $d -lt 25 ]"
check "Send a text / Add chat account buttons" 'dump | grep -q "text=\"Send a text\"" && dump | grep -q "text=\"Add chat account\""'
tap_text "Add chat account"; sleep 3
check "Add chat account opens account settings" 'top_activity | grep -q "com.android.settings"'
sh_ input keyevent BACK; sleep 1.5
top_activity | grep -q $M && { tap_text "Send a text"; sleep 2.5; }
check "Send a text opens the new message sheet" 'dump | grep -q "text=\"New text message\""'
sh_ setprop debug.etesian.messaging_empty false
# New message sheet.
check "sheet: Cancel and To:" 'dump | grep -q "text=\"Cancel\"" && dump | grep -q "text=\"To:\""'
tap_text "Cancel"; sleep 2
check "Cancel leaves the sheet" '! dump | grep -q "text=\"New text message\""'
# Conversation: time inside the bubble as dd/mm/yy | h:mm, N9 composer hint.
open_list
check "list (not the first-run page) with conversations" '! dump | grep -q "Send texts or chat"'
b=$(dump | grep -o '<node[^>]*resource-id="com.android.messaging:id/conversation_name"[^>]*>' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
if [ -n "$b" ] && top_activity | grep -q $M; then
  set -- $b; sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3
  check "timestamp dd/mm/yy | time" 'dump | grep -q "text=\"[0-9][0-9]/[0-9][0-9]/[0-9][0-9] | [0-9]*:[0-9][0-9]"'
  check "composer hint" 'dump | grep -q "Write your message here"'
  check "service icon in the composer" 'dump | grep -q "id/n9_service_icon"'

  ime_up && { sh_ input keyevent BACK; sleep 1.5; }  # keyboard down if it came up
  if ! dump | grep -q "id/n9_ime_strip"; then
    adb exec-out screencap -p > ${SHOTS:-/tmp}/m-kbd-down.png
    ok "keyboard down: composer only (no IME strip)"
  else bad "keyboard down: composer only (no IME strip)"; fi
  check "keyboard down: no Send/counter/emoticon on the compose line" '! dump | grep -qE "id/(send_message_button|message_body_size|n9_emoticon|attach_media_button)\""'
  b=$(dump | grep -o '<node[^>]*id/compose_message_text"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  read x1 y1 x2 y2 <<< "$b"; sh_ input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); sleep 2
  check "keyboard up: strip with emoticon and attach (in the keyboard)" 'wdump | grep -q "id/etesian_strip_emoticon\"" && wdump | grep -q "id/etesian_strip_attach\""'
  check "counter always shown as 160(1) style" 'wdump | grep -o "<node[^>]*id/etesian_strip_counter\"[^>]*>" | grep -q "text=\"[0-9]*([0-9]*)\""'
  ime_up && { sh_ input keyevent BACK; sleep 1.5; }
fi
# 2026-11-05, captured on the N9: received messages are cyan on the RIGHT, what you sent is white on the LEFT,
# on a flat light grey. A received and a sent SMS for a fictional number, removed again afterwards.
N=5550123; now=$(( $(date +%s) * 1000 ))
adb root >/dev/null 2>&1; sleep 1
sh_ appops set com.android.shell WRITE_SMS allow   # writes from the shell are dropped silently otherwise
sh_ "content insert --uri content://sms --bind address:s:$N --bind body:s:'Received test message' --bind type:i:1 --bind read:i:1 --bind date:l:$((now - 60000))"
sh_ "content insert --uri content://sms --bind address:s:$N --bind body:s:'Sent test message' --bind type:i:2 --bind read:i:1 --bind date:l:$now"
sh_ am force-stop $M; sh_ am start -a android.intent.action.SENDTO -d "smsto:$N" -p $M >/dev/null; sleep 4
ime_up && { sh_ input keyevent BACK; sleep 1.5; }
adb exec-out screencap -p > ${SHOTS:-/tmp}/m-sides.png; dump > /tmp/m-sides.xml
sides=$($PY - "${SHOTS:-/tmp}/m-sides.png" <<'PYEOF'
import re, sys
from PIL import Image
x = open('/tmp/m-sides.xml').read(); im = Image.open(sys.argv[1]).convert('RGB'); W = im.size[0]
out = []
for who, text in (('in', 'Received test message'), ('out', 'Sent test message')):
    m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % text, x)
    if not m: out.append(who + ':missing'); continue
    x1, y1, x2, y2 = map(int, m.groups())
    side = 'right' if (x1 + x2) / 2 > W / 2 else 'left'
    r, g, b = im.getpixel((x2 + 4 if who == 'in' else x1 - 4, (y1 + y2) // 2))   # just beside the text, inside the bubble
    colour = 'cyan' if b > 150 and r < 90 else 'white' if min(r, g, b) > 225 else '#%02x%02x%02x' % (r, g, b)
    out.append('%s:%s:%s' % (who, side, colour))
icons = [tuple(map(int, m.groups())) for m in re.finditer(r'id/conversation_icon"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]
texts = {t: tuple(map(int, m.groups())) for t in ('Received test message', 'Sent test message')
         for m in [re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % t, x)] if m}
for who, t in (('in', 'Received test message'), ('out', 'Sent test message')):
    if t not in texts: continue
    ty = (texts[t][1] + texts[t][3]) / 2
    row = [i for i in icons if i[1] - 120 <= ty <= i[3] + 120]
    if row: out.append('%sicon:%s' % (who, 'right' if (row[0][0] + row[0][2]) / 2 > W / 2 else 'left'))
r, g, b = im.getpixel((W // 2, 400))
out.append('bg:#%02x%02x%02x' % (r, g, b))
print(' '.join(out))
PYEOF
)
echo "    $sides"
check "received message: cyan on the right" 'echo "$sides" | grep -q "in:right:cyan"'
check "sent message: white on the left" 'echo "$sides" | grep -q "out:left:white"'
check "pictures go with their messages (received right, sent left)" 'echo "$sides" | grep -q "inicon:right" && echo "$sides" | grep -q "outicon:left"'
check "conversation background: flat light grey" 'echo "$sides" | grep -qi "bg:#dee2e6\|bg:#dde1e5\|bg:#dfe3e7"'
sh_ content delete --uri content://sms --where "address='$N'" >/dev/null
# A group (two fictional senders): received messages on the LEFT next to their sender's picture, the tail
# pointing at it; what you sent on the right.
G1=5550124; G2=5550125
gt=$(sh_ "content query --uri 'content://mms-sms/threadID?recipient=$G1&recipient=$G2'" | grep -o '_id=[0-9]*' | cut -d= -f2)
sh_ "content insert --uri content://sms --bind thread_id:l:$gt --bind address:s:$G1 --bind body:s:'Group hello from one' --bind type:i:1 --bind read:i:1 --bind date:l:$((now - 30000))"
sh_ "content insert --uri content://sms --bind thread_id:l:$gt --bind address:s:$G2 --bind body:s:'Group hello from two' --bind type:i:1 --bind read:i:1 --bind date:l:$((now - 20000))"
sh_ "content insert --uri content://sms --bind thread_id:l:$gt --bind address:s:$G1 --bind body:s:'Group reply sent' --bind type:i:2 --bind read:i:1 --bind date:l:$((now - 10000))"
sh_ am force-stop $M; sh_ "am start -a android.intent.action.SENDTO -d 'smsto:$G1,$G2' -p $M" >/dev/null; sleep 4
ime_up && { sh_ input keyevent BACK; sleep 1.5; }
adb exec-out screencap -p > ${SHOTS:-/tmp}/m-group.png; dump > /tmp/m-group.xml
group=$($PY - "${SHOTS:-/tmp}/m-group.png" <<'PYEOF'
import re, sys
from PIL import Image
x = open('/tmp/m-group.xml').read(); im = Image.open(sys.argv[1]).convert('RGB'); W = im.size[0]
box = lambda m: tuple(map(int, m.groups()))
icons = [box(m) for m in re.finditer(r'id/conversation_icon"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]
out = []
for who, t in (('in1', 'Group hello from one'), ('in2', 'Group hello from two'), ('out', 'Group reply sent')):
    m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % t, x)
    if not m: out.append(who + ':missing'); continue
    x1, y1, x2, y2 = box(m); side = 'right' if (x1 + x2) / 2 > W / 2 else 'left'
    row = [i for i in icons if i[1] - 120 <= (y1 + y2) / 2 <= i[3] + 120]
    pic = ('right' if (row[0][0] + row[0][2]) / 2 > W / 2 else 'left') if row else 'none'
    # Received: the tail at the top left points at the picture (cyan just right of the picture, at the top).
    tail = ''
    if who != 'out' and row:
        px = [im.getpixel((xx, row[0][1] + 4)) for xx in range(row[0][2], row[0][2] + 40)]   # across the gap
        tail = ':tail' if any(b > 150 and r < 90 for r, g, b in px) else ':notail'
    out.append('%s:%s:pic-%s%s' % (who, side, pic, tail))
print(' '.join(out))
PYEOF
)
echo "    $group"
check "group: received messages on the left with their sender's picture" 'echo "$group" | grep -q "in1:left:pic-left" && echo "$group" | grep -q "in2:left:pic-left"'
check "group: the tail points at the picture" 'echo "$group" | grep -q "in1:left:pic-left:tail"'
check "group: what you sent on the right" 'echo "$group" | grep -q "out:right"'
sh_ content delete --uri content://sms --where "thread_id=$gt" >/dev/null; sh_ appops set com.android.shell WRITE_SMS default
check "messaging did not crash" '! crashed com.android.messaging'
sh_ am force-stop $M
# New message to a test number: blue chip, "155(1)"-style counter, black Send with the word.
# Never sent: the text is cleared and the draft dropped.
sh_ am start -a android.intent.action.SENDTO -d smsto: -p $M >/dev/null; sleep 3
if top_activity | grep -q $M; then
  sh_ input text 5550100; sleep 1; sh_ input keyevent ENTER; sleep 2.5
  adb exec-out screencap -p > /tmp/mchip.png
  blue=$($PY -c "
from PIL import Image; im=Image.open('/tmp/mchip.png').convert('RGB')
print(sum(1 for x in range(100,700,4) for y in range(190,290,4) if (lambda p: p[2]>170 and p[0]<60 and p[1]<140)(im.getpixel((x,y)))))")
  check "recipient chip is blue ($blue)" "[ $blue -ge 60 ]"
  b=$(dump | grep -o '<node[^>]*id/compose_message_text"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  read x1 y1 x2 y2 <<< "$b"; sh_ input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); sleep 1
  sh_ input text hdtest; sleep 1.5
  w=$(wdump)
  b=$(echo "$w" | grep -o '<node[^>]*id/etesian_strip_send"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  if [ -n "$b" ]; then
    read x1 y1 x2 y2 <<< "$b"; adb exec-out screencap -p > /tmp/msend.png
    r=$($PY -c "
from PIL import Image; im=Image.open('/tmp/msend.png').convert('L'); c=im.crop(($x1,$y1,$x2,$y2)); px=list(c.tobytes())
print(sum(1 for p in px if p<40)*100//len(px), sum(1 for p in px if p>200)*100//len(px))")
    read dark white <<< "$r"
    check "Send: black button ($dark%) with white text ($white%)" "[ $dark -ge 50 ] && [ $white -ge 3 ]"
  else bad "Send button shown (keyboard strip)"; fi
  check "counter counts down (154(1))" 'echo "$w" | grep -q "text=\"154(1)\""'
  # N9: one white line on top of the keys (the keyboard's), under the compose line; Messaging's
  # own strip stays hidden meanwhile (tests/keyboard_strip.sh checks the band in detail).
  adb exec-out screencap -p > ${SHOTS:-/tmp}/m-kbd-up.png
  check "Messaging's own strip hidden while the keyboard hosts it" '! dump | grep -q "id/n9_ime_strip"'
  st=$(echo "$w" | grep -o '<node[^>]*id/etesian_strip_end"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  ct=$(dump | grep -o '<node[^>]*id/compose_message_text"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  read sx1 sy1 sx2 sy2 <<< "$st"; read cx1 cy1 cx2 cy2 <<< "$ct"
  check "strip under the composer ($sy1 >= $cy2)" "[ -n \"$sy1\" ] && [ $sy1 -ge $cy2 ]"
  r=$($PY -c "from PIL import Image; im=Image.open('${SHOTS:-/tmp}/m-kbd-up.png').convert('L'); print(im.getpixel((225,$sy1+4)), im.getpixel((225,$sy2+8)))")
  read top below <<< "$r"
  check "strip is white ($top) and dark keyboard directly below it ($below)" "[ $top -ge 245 ] && [ $below -le 100 ]"
  ime_up && { sh_ input keyevent BACK; sleep 1.5; }
  adb exec-out screencap -p > ${SHOTS:-/tmp}/m-new-kbd-down.png
  check "new message, keyboard down: strip hidden" '! dump | grep -q "id/n9_ime_strip"'
  # Slide-out keyboard: the strip stands alone at the bottom while the composer has focus.
  # Simulated with debug.etesian.messaging_hwkbd (the real config is checked when it is open).
  sh_ setprop debug.etesian.messaging_hwkbd true
  b=$(dump | grep -o '<node[^>]*id/compose_message_text"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  read x1 y1 x2 y2 <<< "$b"; sh_ input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); sleep 1
  ime_up && { sh_ input keyevent BACK; sleep 1.5; }
  adb exec-out screencap -p > ${SHOTS:-/tmp}/m-hwkbd.png
  check "hardware keyboard: strip shown without the on-screen keyboard" '! ime_up && dump | grep -q "id/n9_ime_strip"'
  check "hardware keyboard: draft kept (hdtest)" 'dump | grep -q "text=\"hdtest\""'
  sh_ setprop debug.etesian.messaging_hwkbd false
  if sh_ dumpsys input | grep -q "KeyboardType: 2" && sh_ dumpsys window | grep -q "hardKeyboardHidden=1\|mHardKeyboardAvailable=true"; then
    check "real slide keyboard open: strip shown" 'dump | grep -q "id/n9_ime_strip"'
  fi
  sh_ input keycombination KEYCODE_CTRL_LEFT KEYCODE_A; sh_ input keyevent DEL; sleep 1
  for _ in 1 2 3; do sh_ input keyevent BACK; sleep 1; done
  sh_ am force-stop $M
  check "nothing sent or drafted" '! adb shell content query --uri content://mms-sms/conversations --projection thread_id:address 2>/dev/null | grep -q 5550100'
fi
check "messaging did not crash" '! crashed com.android.messaging'
sh_ am force-stop $M; home

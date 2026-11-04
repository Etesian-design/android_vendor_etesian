# Upstream LatinIME (2026-11, rebased onto in 2f1bdc9) added a clipboard suggestion to the strip. With the
# N9 strip hosted, a fresh copy must show inside it next to attach, emoticon, the counter and Send.
# Nothing is sent; the copied word is typed into an empty draft, copied, and deleted again.
M=com.android.messaging; K=com.android.inputmethod.latin
wdump() { sh_ uiautomator dump --windows /sdcard/w.xml >/dev/null 2>&1; sh_ cat /sdcard/w.xml; }
unlock
sh_ am force-stop $M
sh_ am start -a android.intent.action.SENDTO -d smsto: -p $M >/dev/null; sleep 3
sh_ input text 5550100; sleep 1; sh_ input keyevent ENTER; sleep 2.5
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o '<node[^>]*id/compose_message_text"[^>]*>' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
set -- $b; cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
sh_ input tap $cx $cy; sleep 1
sh_ input text cliptest42; sleep 1
sh_ input keycombination CTRL_LEFT A; sleep 0.5; sh_ input keyevent COPY; sleep 0.5; sh_ input keyevent DEL; sleep 0.5
sh_ input keyevent BACK; sleep 1; sh_ input tap $cx $cy; sleep 2
w=$(wdump); shot keyboard_clipboard
check "clipboard suggestion shown in the keyboard strip" 'echo "$w" | grep -q "$K:id/clipboard_suggestion_strip" && echo "$w" | grep -o "<node[^>]*package=\"$K\"[^>]*>" | grep -q "text=\"cliptest42\""'
check "the N9 strip keeps attach, emoticon and Send beside it" 'echo "$w" | grep -q "$K:id/etesian_strip_attach" && echo "$w" | grep -q "$K:id/etesian_strip_emoticon" && echo "$w" | grep -q "$K:id/etesian_strip_send"'
# N9 squircle rule: the chip's ends are squircle halves. 0.27r in from the right end, 0.75r above the middle,
# a pill is empty (0.73^2 + 0.75^2 > 1) while an n = 2.8 squircle is filled (0.73^2.8 + 0.75^2.8 < 1).
set -- $(echo "$w" | grep -o "<node[^>]*text=\"cliptest42\"[^>]*package=\"$K\"[^>]*>" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
shape=$(python3 -c "
from PIL import Image; im=Image.open('$SHOTS/keyboard_clipboard.png').convert('L'); l,t,r_,b=$1,$2,$3,$4
r=(b-t)/2; cy=(t+b)/2; g=lambda x,y: im.getpixel((int(x),int(y)))
body=g(r_-r, cy+r*0.9); out=g(r_+4, cy); end=g(r_-0.27*r, cy-0.75*r)
print(int(abs(end-body) < abs(end-out)), body, out, end)")
check "clipboard chip ends are squircle halves, not a pill ($shape)" '[ "${shape%% *}" = 1 ]'
check "keyboard did not crash" '! crashed $K'
sh_ input keyevent BACK; sleep 0.5; sh_ am force-stop $M

# User 2026-11-20: Etesian settings → Single hand mode → Size: the miniature above the slider redraws while the slider
# moves (the small screen grows and shrinks), and the setting follows it.
unlock
old=$(sh_ settings get secure etesian_single_hand_size)
D=$(raw_sh wm density | grep -o '[0-9]*' | tail -1)
sh_ am start -S -W -n $LAUNCHER/.SettingsActivity --es section gestures >/dev/null; sleep 2.5
tap_text "Single hand mode" >/dev/null 2>&1 || true   # brings the category on screen (scrolls until found)
sh_ input swipe 540 1500 540 1100 300; sleep 1
# Measured and dragged in the phone's own pixels: through the 1080x2160 mapping the drag missed the slider.
bar() { raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; raw_sh cat /sdcard/u.xml | tr '>' '\n' | grep 'class="android.widget.SeekBar"' | grep -o 'bounds="[^"]*"' | tail -1 | tr -cs '0-9' ' '; }
read l t r b <<<"$(bar)"
check "the Size slider is on screen ($l $t $r $b)" '[ -n "$b" ]'
# Drags start inside the slider, never at its ends: within 18dp of the screen's side a touch is the N9 edge swipe, and
# the right 64dp at mid-height is the Breeze handle's (SideBar reach); either takes the touch and the app goes. They
# may end at the slider's ends.
in=$(( 30 * D / 160 )); rs=$(( l + (r - l) * 3 / 4 ))
y=$(( (t + b) / 2 )); top=$(( t - 300 * D / 160 )); [ $top -lt 0 ] && top=0
A shell input swipe $rs $y $((l + 5)) $y 600; sleep 1.2; s1=$(sh_ settings get secure etesian_single_hand_size)
A exec-out screencap -p > "$SHOTS/single_hand_small.png"
# Moving and held: the preview redraws before the finger lifts.
A shell input swipe $((l + in)) $y $((r - 5)) $y 600; sleep 1.2; s2=$(sh_ settings get secure etesian_single_hand_size)
A exec-out screencap -p > "$SHOTS/single_hand_big.png"
check "the slider sets the size (smallest $s1, largest $s2)" '[ "$s1" -le 55 ] && [ "$s2" -ge 85 ]'
# The miniature: pixels that changed between the two, above the slider.
ch=$(python3 -c "
from PIL import Image, ImageChops
a=Image.open('$SHOTS/single_hand_small.png').convert('L').crop((0,$top,$r,$t))
b=Image.open('$SHOTS/single_hand_big.png').convert('L').crop((0,$top,$r,$t))
d=ImageChops.difference(a,b); print(sum(1 for p in d.getdata() if p>40))")
check "the miniature above it redraws with the size ($ch px changed)" '[ "$ch" -gt 3000 ]'
# Live while sliding: a slow drag, a screenshot with the finger still down, part way.
A shell input swipe $rs $y $(( (l + r) / 2 )) $y 3000 &
sleep 2.2; A exec-out screencap -p > "$SHOTS/single_hand_mid.png"; wait; sleep 0.5
mid=$(python3 -c "
from PIL import Image, ImageChops
a=Image.open('$SHOTS/single_hand_big.png').convert('L').crop((0,$top,$r,$t))
b=Image.open('$SHOTS/single_hand_mid.png').convert('L').crop((0,$top,$r,$t))
print(sum(1 for p in ImageChops.difference(a,b).getdata() if p>40))")
check "it follows the finger before it lifts ($mid px changed mid-drag)" '[ "$mid" -gt 1000 ]'
check "launcher did not crash" '! crashed $LAUNCHER'
if [ "$old" = null ]; then sh_ settings delete secure etesian_single_hand_size >/dev/null; else sh_ settings put secure etesian_single_hand_size $old; fi
home

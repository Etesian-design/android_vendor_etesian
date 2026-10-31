# Lock-screen notifications, iPhone-style accordion (user decision 2026-10-26): every card past the
# last full one waits hidden behind it; sliding up uncovers the next card's lower part until it is
# fully exposed, then it is carried up; at the top cards fold away under the clock block's edge, so
# the clock and date are never covered. Card size follows Android's font size and display size.
# Positions come from SystemUI's test log (setprop log.tag.EtesianLock DEBUG):
#   "layout limit=<y> card=<top>:<clipTop>:<height>:<v|h> ..." in screen px.
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind stack --ei count ${1:-6} >/dev/null; }
last() { sh_ logcat -d -s EtesianLock:D | grep "layout limit" | tail -1; }
# Every visible card's drawn top (top + clip) is at or below the limit; prints the offenders.
over() { echo "$1" | tr ' ' '\n' | awk -F'[=:]' -v L=$(echo "$1" | grep -o 'limit=[0-9-]*' | cut -d= -f2) '$1=="card" && $5=="v" && $2+$3 < L-1 {print}'; }
partial() { echo "$1" | tr ' ' '\n' | awk -F'[=:]' -v L=$(echo "$1" | grep -o 'limit=[0-9-]*' | cut -d= -f2) '$1=="card" && $5=="v" && $3>0 && $3<$4 && $2>=L {n++} END {print n+0}'; }
folding() { echo "$1" | tr ' ' '\n' | awk -F'[=:]' -v L=$(echo "$1" | grep -o 'limit=[0-9-]*' | cut -d= -f2) '$1=="card" && $5=="v" && $2<L {n++} END {print n+0}'; }
hiddenn() { echo "$1" | grep -o ':h' | wc -l; }
height1() { echo "$1" | grep -o 'card=[0-9-]*:[0-9]*:[0-9]*' | cut -d: -f3 | awk '{s+=$1} END {print s+0}'; }
datebottom() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="[A-Z][a-z]*day, [^"]*"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | grep -o '[0-9]*\]"$' | tr -d ']"'; }
fs=$(sh_ settings get system font_scale)
sh_ setprop log.tag.EtesianLock DEBUG
unlock; home
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
# 3 of ours (4+ from one app would be auto-grouped into one row) plus whatever the phone has.
post 3; sleep 2
lock; sh_ input keyevent WAKEUP; sleep 2.5
sh_ logcat -c; sh_ input motionevent DOWN 540 1950; sh_ input motionevent MOVE 540 1949; sleep 0.5
r=$(last)
check "test log present" '[ -n "$r" ]'
check "rest: the rest wait hidden behind the last full card" '[ $(hiddenn "$r") -ge 1 ]'
check "rest: nothing reaches above the clock block" '[ -z "$(over "$r")" ]'
d=$(datebottom); L=$(echo "$r" | grep -o 'limit=[0-9]*' | cut -d=  -f2)
check "fold line is under the date ($d < $L)" '[ -n "$d" ] && [ "$d" -le "$L" ]'
shot lock_stack_f00
seen_partial=0; seen_fold=0; bad=""
y=1949
for i in $(seq 1 16); do
  y=$((y-80)); sh_ input motionevent MOVE 540 $y; sleep 0.35
  r=$(last); [ $(partial "$r") -gt 0 ] && seen_partial=1; [ $(folding "$r") -gt 0 ] && seen_fold=1
  o=$(over "$r"); [ -n "$o" ] && bad="$bad $i:$o"
  shot lock_stack_f$(printf %02d $i)
done
check "sliding up uncovers the next card's lower part (partly revealed card seen)" '[ $seen_partial = 1 ]'
check "cards fold away under the clock block's edge" '[ $seen_fold = 1 ]'
check "the clock block is never covered while sliding up ($bad)" '[ -z "$bad" ]'
bad=""
for i in $(seq 17 26); do
  y=$((y+128)); sh_ input motionevent MOVE 540 $y; sleep 0.35
  r=$(last); o=$(over "$r"); [ -n "$o" ] && bad="$bad $i:$o"
  shot lock_stack_f$i
done
sh_ input motionevent UP 540 $y; sleep 1
check "…nor while sliding back down ($bad)" '[ -z "$bad" ]'
whites() { /home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open('$1').convert('L'); w,h=im.size
print(sum(1 for y in range(80,330,2) for x in range(w//3,w,2) if im.getpixel((x,y))>235))"; }
w0=$(whites $SHOTS/lock_stack_f00.png); w1=$(whites $SHOTS/lock_stack_f16.png)
check "the clock stays fully visible at the top of the slide ($w0 vs $w1 white px)" '[ "$w1" -ge $(( w0 * 7 / 10 )) ]'
h1=$(height1 "$(last)")
# Size follows Android's font size.
sh_ settings put system font_scale $(echo "${fs:-1.0}" | awk '{print $1+0.3}'); sleep 3
lock; sh_ input keyevent WAKEUP; sleep 2.5
sh_ logcat -c; sh_ input motionevent DOWN 540 1950; sh_ input motionevent MOVE 540 1949; sleep 0.6; sh_ input motionevent UP 540 1949
h2=$(height1 "$(last)")
check "a larger font size makes the cards taller (total height) ($h1 -> $h2)" '[ -n "$h1" ] && [ -n "$h2" ] && [ "$h2" -gt "$h1" ]'
shot lock_stack_font13
sh_ settings put system font_scale ${fs:-1.0}; [ "$fs" = null ] && sh_ settings put system font_scale 1.0
sleep 2
unlock
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
sh_ setprop log.tag.EtesianLock ""
check "systemui did not crash" '! crashed com.android.systemui'
home

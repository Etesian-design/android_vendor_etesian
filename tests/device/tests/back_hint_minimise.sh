# Bug 2026-10-29 #8: sliding an application away slowly, the Back chevron popped up at about half way
# (the finger's downward drift counted as the curved Back). A curve is a short move in, then mostly down.
E=~/android/lineage/vendor/etesian/launcher/app/src/platform/java/com/bompotis/etesian/launcher/platform/EdgeGestures.kt
check "curved Back needs more down than in, early in the slide" 'grep -q "across(e) > d && progress < curveMaxProgress" $E'
unlock
sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
sh_ "input motionevent DOWN 2 1000; y=1000; for x in 30 80 140 200 260 330 400 470 540 600; do y=\$((y+22)); input motionevent MOVE \$x \$y; done"; sleep 0.5
n=$(sh_ dumpsys window windows | grep -c "EtesianBack")
sh_ input motionevent UP 600 1220 >/dev/null; sleep 1.5
check "no Back chevron while minimising with a drift ($n)" '[ "$n" -eq 0 ]'
home

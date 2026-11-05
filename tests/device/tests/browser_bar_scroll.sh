# 2026-11-05: scrolling down a page, the address bar flashed (hid, came back, hid...) and the page jumped
# with it until far enough down. Hiding the bar resizes the page, Chromium's own scroll fix-up looked like
# a scroll back up, and that showed it again. Slow drags down must hide it once, and never show it.
J=org.lineageos.jelly; PORT=6121; D="$(cd "$(dirname "${BASH_SOURCE[0]}")/../data" && pwd)"
python3 -m http.server $PORT --bind 127.0.0.1 --directory "$D" >/dev/null 2>&1 & SRV=$!
adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1
unlock
bars() { sh_ logcat -d | grep -c "MainActivity: bars $1"; }
for run in portrait landscape; do
  [ $run = landscape ] && { sh_ settings put system accelerometer_rotation 0; sh_ settings put system user_rotation 1; sleep 2; }
  sh_ am force-stop $J; sh_ am start -a android.intent.action.VIEW -d "http://127.0.0.1:$PORT/long_page.html" -n $J/.MainActivity >/dev/null; sleep 4
  W=$(sh_ wm size | grep -o '[0-9]*x[0-9]*' | tail -1); [ $run = landscape ] && set -- ${W#*x} ${W%x*} || set -- ${W%x*} ${W#*x}
  x=$(( $1 / 2 )); y0=$(( $2 * 6 / 10 )); y1=$(( $2 * 45 / 100 ))
  sh_ logcat -c
  for i in 1 2 3 4 5 6; do sh_ input swipe $x $y0 $x $y1 1200; sleep 0.6; done   # slow drags: the page goes down
  for i in 1 2 3; do sh_ input swipe $x $y0 $x $y1 250; sleep 0.8; done           # and quick ones (where it flashed)
  h=$(bars hidden); s=$(bars shown); shot browser_bar_scroll_$run; sh_ logcat -d | grep "MainActivity:" > "$SHOTS/browser_bar_scroll_$run.log"
  check "$run: scrolling down hides the address bar once (hidden $h, shown $s)" '[ "$h" = 1 ] && [ "$s" = 0 ]'
  sh_ logcat -c
  sh_ input swipe $x $y1 $x $y0 400; sleep 1
  check "$run: scrolling back up shows it again (shown $(bars shown))" '[ "$(bars shown)" -ge 1 ]'
done
sh_ settings put system user_rotation 0; sleep 1
check "browser did not crash" '! crashed $J'
sh_ am force-stop $J
adb reverse --remove tcp:$PORT >/dev/null; kill $SRV

# N9: an application opened from Open applications (or the app grid) slides in from the side the
# last one was swiped out to: swiped from the left edge it leaves to the right, so the next comes
# from the right; swiped from the right edge, from the left. The minimise action counts as a right-edge swipe.
R=~/android/lineage/vendor/etesian/launcher/app/src/platform/java/com/bompotis/etesian/launcher/platform
check "minimise records the exit side" 'grep -q "RestoreTransition.fromRight = edge == Edge.LEFT" $R/WindowDrag.kt'
check "restore enters from that side" 'grep -q "if (fromRight) end.x + off else end.x - off" $R/RestoreTransition.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# Tap the Calculator card in Open applications.
card() { local b; b=$(dump | grep -o '<node[^>]*\(text\|content-desc\)="Calculator"[^>]*>' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
unlock
for side in left right; do
  sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
  if [ $side = left ]; then sh_ input swipe 2 1000 700 1000 250; want=right; else sh_ input swipe 1077 1000 380 1000 250; want=left; fi
  sleep 2.5; sh_ logcat -c; card; sleep 2
  got=$(sh_ logcat -d -s EtesianRestore | grep -o "enter from [a-z]*" | tail -1)
  check "swiped out from the $side edge: next comes in from the $want ($got)" '[ "$got" = "enter from $want" ]'
done
check "launcher did not crash" '! crashed $LAUNCHER'
home

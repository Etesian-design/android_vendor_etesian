# Edge swipe minimise: the swipe feedback slides the app away ONCE; home is then started with no
# system window animation (a second slide of the app over Open applications was the bug).
G=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/GestureService.kt
check "goHome starts home without a window animation" 'grep -q "makeCustomAnimation(this, 0, 0)" $G && grep -q "startActivity(home, still)" $G'
D=~/android/lineage/vendor/etesian/launcher/app/src/platform/java/com/bompotis/etesian/launcher/platform/WindowDrag.kt
check "window drag shows Open applications without a window animation" '! grep -q "startActivity(HomeActivity.switcherIntent" $D && grep -q "showSwitcherStill" $D'
# Regression 2026-10-28: after the window-drag minimise, home was started again with the default
# window animation, so Open applications slid in a second time. Ask the window manager directly:
# no transition after the swipe may be animated by the default (stock slide) handler.
unlock
for run in 1 2 3; do
  sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
  sh_ wm logging enable-text WM_DEBUG_WINDOW_TRANSITIONS >/dev/null 2>&1; sh_ logcat -c
  sh_ input swipe 2 1000 700 1000 250; sleep 2.5
  n=$(sh_ logcat -d | grep -c "animated by com.android.wm.shell.transition.DefaultTransitionHandler")
  sh_ wm logging disable-text WM_DEBUG_WINDOW_TRANSITIONS >/dev/null 2>&1
  check "minimise $run: no second (default) window animation ($n)" '[ "$n" -eq 0 ]'
done
check "pane is Open applications" '[ "$(home_pane)" = switcher ]'
check "launcher did not crash" '! crashed $LAUNCHER'
home

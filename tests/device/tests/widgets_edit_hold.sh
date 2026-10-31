# Bugs 2026-10-29 #3 #4 #6 #7: swiping across the Widgets view turned edit mode on (the hold timer
# on a widget kept running once the pager took the swipe); edit mode only started on a widget, not
# on empty space; the picker opened collapsed; widgets resized from one corner only.
W=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/WidgetsPage.kt
P=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/WidgetPicker.kt
check "edit hold is timed on the page and cancelled by moves/cancel" 'grep -q "private val editHold" $W && grep -q "ACTION_CANCEL -> removeCallbacks(editHold)" $W'
check "no per-widget hold timer left" '! grep -q "postDelayed(longPress" $W'
check "resize handles on three corners (not the top right)" 'grep -q "Gravity.BOTTOM or Gravity.START, -1, 1" $W && grep -q "Gravity.TOP or Gravity.START, -1, -1" $W && ! grep -q "Gravity.TOP or Gravity.END, 1" $W'
check "picker opens with every app expanded" 'grep -q "expanded += groups.map" $P'
unlock
wp=$(pref_switch home Widgets 1)
widgets_pane
# A slow swipe that starts on a widget and leaves the view must not start edit mode.
sh_ input swipe 540 700 1000 700 1200; sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "swiping away does not turn edit mode on" '! sh_ cat /sdcard/u.xml | grep -q "text=\"Done\""'
widgets_pane
for _ in 1 2 3 4 5 6; do sh_ input swipe 540 1800 540 400 200; done; sleep 1
# Hold on empty space (below the last widget) turns edit mode on.
sh_ input swipe 540 1900 540 1900 1200; sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "holding empty space turns edit mode on" 'sh_ cat /sdcard/u.xml | grep -q "text=\"Done\""'
tap_text "Done"; sleep 1
home
pref_switch home Widgets "$wp" >/dev/null; home

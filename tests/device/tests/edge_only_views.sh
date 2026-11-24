# 2026-11-22 (user): the home screen's gestures can be changed, the N9 way staying the default. An edge's view
# ("After swiping an app away") can be that edge's only: it leaves the row of views and comes in from that edge, from
# an application or on the home screen (e.g. Sailfish: the bottom always brings Applications). The bottom and one side
# only, so the row always keeps views to swipe between.
unlock
# The user's settings are put back exactly at the end, from a copy (choosing them back in Settings did not restore them).
PF=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
sh_ cp $PF /data/local/tmp/edge_prefs.xml
panes() { for _ in 1 2 3 4 5 6 7 8; do echo "$(home_pane)"; sh_ input swipe 1000 1200 100 1200 300; sleep 1.2; done | sort -u | tr '\n' ' '; }
sheet() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'content-desc="pane:[a-z]*"' | tr '\n' ' '; }
pref_list gestures "From the bottom" "Applications" >/dev/null
pref_switch gestures "Only from the bottom" 1 >/dev/null
# Back out of Etesian settings already lands home, so this Home is a Home on the home screen: it brings Applications,
# now the bottom's own view, as the sheet. Back sends it away before the row is read.
home; sleep 1
sheet | grep -q "pane:apps" && { sh_ input keyevent BACK; sleep 1; }
row=$(panes)
check "Applications left the row ($row)" '! echo "$row" | grep -q "apps"'
check "the row keeps its other views" 'echo "$row" | grep -q switcher'
# Home on the home screen brings Applications (N9); as the bottom's own view it comes in as the sheet, showing its icons.
# It came in blank (black) when the pager had hidden the view, and the next swipe then shut it.
sh_ input keyevent HOME; sleep 1.5
check "Home on the home screen brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && sh_ cat /sdcard/u.xml | grep -q "text=\"Calculator\""'
sh_ input keyevent BACK; sleep 1
check "Back sends it away ($(sheet))" '! sheet | grep -q "pane:apps"'
sh_ input swipe 540 2155 540 1400 300; sleep 1
# 2026-11-24 (user): a view that had been beside the shown one kept the row's sideways offset and came in black, the
# pane named but nothing drawn: its icons must be on screen.
icons() { sh_ cat /sdcard/u.xml | grep -q 'text="Calculator"'; }
check "a swipe up from the bottom on the home screen brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && icons'
shot edge_only_apps
sh_ input keyevent BACK; sleep 1
check "Back sends it away again" '! sheet | grep -q "pane:apps"'
# 2026-11-24 (user): with Applications in from the bottom, the other edges did nothing; the user was stuck in it until
# opening an app. Every other edge sends it away and does what it does in the row (both sides: Open applications).
pref_list gestures "From the left side" "Open applications" >/dev/null; pref_list gestures "From the right side" "Open applications" >/dev/null
for side in "5 1200 900 1200" "1075 1200 180 1200"; do
  home; sheet | grep -q "pane:apps" || { sh_ input swipe 540 2155 540 1400 300; sleep 1; }
  sh_ input swipe $side 300; sleep 1.2
  check "a swipe in from the side ($side) sends Applications away to Open applications ($(sheet))" '! sheet | grep -q "pane:apps" && [ "$(home_pane)" = switcher ]'
done
sh_ am start -n com.android.calculator2/.Calculator >/dev/null 2>&1; sleep 2
sh_ input swipe 540 2155 540 900 300; sleep 2
check "leaving an application from the bottom brings Applications, its icons shown ($(sheet))" 'sheet | grep -q "pane:apps" && icons'
sh_ input keyevent HOME; sleep 1
# One side only: with the left side's view its own, the right side's switch is off limits.
pref_list gestures "From the left side" "Events" >/dev/null; pref_switch gestures "Only from the left side" 1 >/dev/null
pref_list gestures "From the right side" "Applications" >/dev/null
sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity --es section gestures >/dev/null 2>&1; sleep 1.5
for _ in 1 2 3; do sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q 'text="Only from the right side"' && break; sh_ input swipe 540 1700 540 700 300; sleep 0.8; done
check "the right side cannot have its own view too" 'sh_ cat /sdcard/u.xml | tr ">" "\n" | awk "/text=\"Only from the right side\"/{f=1} f&&/enabled=/{print; exit}" | grep -q "enabled=\"false\""'
# Back as it was, exactly.
sh_ am force-stop $LAUNCHER; sh_ cp /data/local/tmp/edge_prefs.xml $PF
u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u $PF; sh_ chmod 660 $PF; sh_ restorecon $PF >/dev/null 2>&1
check "the user's settings are back" '[ "$(sh_ md5sum $PF | cut -d" " -f1)" = "$(sh_ md5sum /data/local/tmp/edge_prefs.xml | cut -d" " -f1)" ]'
sh_ rm /data/local/tmp/edge_prefs.xml
home; sleep 1
# The row follows the settings put back: Applications is in it unless the user's own settings give it to an edge.
if sh_ cat $PF | grep -q 'name="edge_only_[a-z]*" value="true"'; then
  check "with the user's own edge views back, the row follows them" 'panes | grep -q switcher'
else check "with it off, Applications is back in the row" 'panes | grep -q apps'; fi
check "launcher did not crash" '! crashed $LAUNCHER'

# Settings → Gestures → After swiping an app away (2026-11-18, user): per side, Open applications (the default),
# where you were, Applications, Events or Widgets.
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
backup=/data/local/tmp/leave_prefs.xml; sh_ cp $P $backup
setp() { sh_ am force-stop $LAUNCHER; sh_ "sed -e '/name=\"leave_to_/d' -e 's#</map>#    <string name=\"leave_to_left\">$1</string>\n    <string name=\"leave_to_right\">$2</string>\n    <string name=\"leave_to_bottom\">$3</string>\n</map>#' $P > /data/local/tmp/lp.xml; cat /data/local/tmp/lp.xml > $P; rm /data/local/tmp/lp.xml"; home; sleep 2; }
app() { sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2.5; }
unlock
setp events apps switcher
app; sh_ input swipe 2 1100 900 1100 200; sleep 2.5
check "from the left: Events ($(home_pane))" '[ "$(home_pane)" = events ]'
app; sh_ input swipe 1077 1100 150 1100 200; sleep 2.5
check "from the right: Applications ($(home_pane))" '[ "$(home_pane)" = apps ]'
app; sh_ "input motionevent DOWN 540 2155; for y in \$(seq 2155 -80 700); do input motionevent MOVE 540 \$y; done; input motionevent UP 540 700"; sleep 2.5
check "from the bottom: Open applications ($(home_pane))" '[ "$(home_pane)" = switcher ]'
# Where you were: home was on Events when the app was opened.
setp last last last
sh_ am start -n $LAUNCHER/.HomeActivity --ei etesian.page 0 >/dev/null; sleep 2
app; sh_ input swipe 1077 1100 150 1100 200; sleep 2.5
check "where you were: back to Events ($(home_pane))" '[ "$(home_pane)" = events ]'
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
check "the settings are on the Gestures page" 'sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section gestures >/dev/null; sleep 2.5; tap_text "From the bottom"'
sh_ input keyevent BACK; sleep 0.5; sh_ input keyevent BACK; home
check "launcher did not crash" '! crashed $LAUNCHER'

# Bug 2026-10-29: in Etesian settings a section page slid in from the left (it reused the
# application-open slide). On the N9 a sub page comes in from the right and Back sends it right.
S=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/SettingsActivity.kt
check "a section opens with the sub page slides" 'grep -q "OVERRIDE_TRANSITION_OPEN, R.anim.n9_page_in, R.anim.n9_page_out" $S && grep -q "OVERRIDE_TRANSITION_CLOSE, R.anim.n9_page_back_in, R.anim.n9_page_back_out" $S'
unlock
sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity >/dev/null 2>&1; sleep 2
d=$(record_slide sp1 tap_text "Home screen")
check "Etesian settings: a section comes in from the right ($d)" '[ "$d" -lt -20 ]'
sleep 0.5
d=$(record_slide sp2 sh_ input keyevent BACK)
check "and Back sends it to the right ($d)" '[ "$d" -gt 20 ]'
# Toolbar buttons: a set is a sub page inside the same screen.
sh_ am start -W --activity-clear-task -n $LAUNCHER/.ToolbarSettingsActivity >/dev/null 2>&1; sleep 2
record_slide sp5 tap_text "All apps" >/dev/null; d=$(title_dir /tmp/claude-1000/sp5.mp4)
check "Toolbar buttons: a set comes in from the right ($d)" '[ "$d" -lt -20 ]'
sleep 0.5
record_slide sp6 sh_ input keyevent BACK >/dev/null; d=$(title_dir /tmp/claude-1000/sp6.mp4)
check "and Back brings the list from the left ($d)" '[ "$d" -gt 20 ]'
sh_ am force-stop $LAUNCHER; home
# Stock Settings follows the system default, which goes the same way.
sh_ am start -W --activity-clear-task -a android.settings.SETTINGS >/dev/null 2>&1; sleep 2
d=$(record_slide sp3 tap_text "Display")
check "Settings: a sub page comes in from the right ($d)" '[ "$d" -lt -20 ]'
sleep 0.5
d=$(record_slide sp4 sh_ input keyevent BACK)
check "and Back sends it to the right ($d)" '[ "$d" -gt 20 ]'
sh_ am force-stop com.android.settings; home

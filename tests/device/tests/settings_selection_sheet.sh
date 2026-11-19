# 2026-11-18 (user): every choice in Etesian settings is the N9 selection sheet, never Android's radio-button dialog.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "list and multi-choice settings go to N9Sheet" 'grep -q "override fun onDisplayPreferenceDialog" $L/SettingsActivity.kt && grep -q "N9Sheet.choose(" $L/SettingsActivity.kt && grep -q "N9Sheet.chooseMany(" $L/SettingsActivity.kt'
check "no radio-button or Android item dialogs left in settings" '! grep -q "setSingleChoiceItems\|setMultiChoiceItems\|AlertDialog" $L/SettingsActivity.kt'
xml() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
backup=/data/local/tmp/sel_prefs.xml; sh_ cp $P $backup
# The Back button's side is a system setting (the toolbar library reads it), not in the prefs file: put it back too.
back_side=$(sh_ settings get secure etesian_toolbar_back_side)
unlock
# One choice from each kind of page: Breeze gestures, the toolbar, the lock screen, Events (multi-choice).
for entry in "sidesense:Double tap:Open applications" "toolbar:Back button:Right" "gestures:From the left side:Applications"; do
    page=${entry%%:*}; rest=${entry#*:}; title=${rest%%:*}; pick=${rest#*:}
    sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section $page >/dev/null; sleep 2.5
    tap_text "$title"; sleep 1.2
    check "$title: the N9 selection sheet" 'xml | grep -q "content-desc=\"Selection sheet\""'
    check "$title: no radio buttons" '! xml | grep -q "RadioButton\|CheckedTextView\|select_dialog"'
    [ "$page" = sidesense ] && shot selection_sheet
    tap_text "$pick"; sleep 1.2
    check "$title: picked \"$pick\", the sheet closed and the row says so" '! xml | grep -q "Selection sheet" && xml | grep -q "text=\"$pick\""'
done
sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section events >/dev/null; sleep 2.5
tap_text "Items"; sleep 1.2
check "multi-choice (Today items): the N9 sheet with Done" 'xml | grep -q "Selection sheet" && xml | grep -q "text=\"Done\""'
sh_ input keyevent BACK; sleep 0.5
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
sh_ settings put secure etesian_toolbar_back_side "${back_side/null/0}"
check "Back button side restored (${back_side/null/0})" '[ "$(sh_ settings get secure etesian_toolbar_back_side)" = "${back_side/null/0}" ]'
check "launcher did not crash" '! crashed $LAUNCHER'

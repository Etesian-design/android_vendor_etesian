# Etesian settings → Breeze: everything about Breeze set up in one page (place, theme, gestures, the menu's
# buttons and widgets, each moved or removed from an N9 sheet). Adding a button goes through the button pickers
# and comes back; every page can be left (the N9 toolbar's Back, the Back key), and they run in Etesian
# settings' own task, which Open applications shows as "Etesian settings".
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
backup=/data/local/tmp/breeze_set_prefs.xml; sh_ cp $P $backup
cfg=$(raw_sh settings get secure etesian_toolbar_config)
raw_sh settings put secure etesian_toolbar_config "'$(echo "$cfg" | python3 -c "import json,sys; d=json.load(sys.stdin) if sys.stdin else {}; d.pop('breeze',None); print(json.dumps(d))" 2>/dev/null || echo '{}')'"
breeze_labels() { raw_sh settings get secure etesian_toolbar_config | python3 -c "import json,sys; print(','.join(b.get('label','') for b in json.load(sys.stdin).get('breeze',[])))" 2>/dev/null; }
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
on_screen() { dump | grep -q "text=\"$1\""; }
resumed() { sh_ dumpsys activity activities | grep -m1 "ResumedActivity" | grep -o '[A-Za-z]*Activity' | tail -1; }
open_page() { sh_ am start -n $LAUNCHER/.SettingsActivity --activity-clear-task --es section sidesense >/dev/null; sleep 2.5; }
unlock; sh_ am force-stop $LAUNCHER; home
open_page
check "Breeze page opens" '[ "$(resumed)" = SettingsActivity ] && on_screen Place'
shot breeze_settings
# Scrolls down until [1] shows (the page is opened afresh each time, so from the top).
find_text() { open_page; for _ in 1 2 3 4 5 6; do on_screen "$1" && return 0; sh_ input swipe 540 1700 540 900 300; sleep 0.8; done; return 1; }
for t in Place Theme Gestures Tap "Flick inwards" "Menu buttons" Back Home "Open applications" "Add and arrange buttons" "Menu widgets" "Add widget"; do
  check "page has: $t" "find_text '$t'"
done
open_page
tap_text Home; sleep 1.5
check "a button's row: N9 sheet (Move up / Move down / Remove)" 'on_screen "Move up" && on_screen "Move down" && on_screen Remove'
tap_text "Move down"; sleep 1.5
check "Move down: Home after Open applications" '[ "$(breeze_labels)" = "Back,Open applications,Home" ]'
tap_text Home; sleep 1.5; tap_text Remove; sleep 1.5
check "Remove from the sheet" '[ "$(breeze_labels)" = "Back,Open applications" ]'
# The editor is left with Back and with the N9 toolbar's Back.
tap_text "Add and arrange buttons"; sleep 2.5
sh_ input keyevent BACK; sleep 1.5
check "Back leaves the editor (back to Breeze settings)" '[ "$(resumed)" = SettingsActivity ]'
tap_text "Add and arrange buttons"; sleep 2.5
check "the editor has the N9 toolbar" 'dump | grep -qi "content-desc=\"Back\""'
dump | grep -o 'content-desc="Back"[^>]*bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ' | { read a b c d; [ -n "$d" ] && sh_ input tap $(( (a+c)/2 )) $(( (b+d)/2 )); }; sleep 1.5
check "toolbar Back leaves it" '[ "$(resumed)" = SettingsActivity ]'
# Add widget (2026-11-18, user): an Etesian settings page, its title clear of the status bar, the bottom bar's Back
# (no close button of its own), the list ending above the bar.
open_page; tap_text "Add widget"; sleep 2.5
check "Add widget: its own page in Etesian settings" '[ "$(resumed)" = BreezeWidgetActivity ] && [ "$(task_type BreezeWidgetActivity)" = standard ]'
tb=$(raw_sh uiautomator dump /sdcard/w.xml >/dev/null 2>&1; command adb shell cat /sdcard/w.xml | tr -d '\r' | grep -o 'text="Add widget"[^>]*bounds="\[[0-9]*,[0-9]*\]' | grep -o ',[0-9]*\]' | tr -d ',]')
sb=$(raw_sh dumpsys window | grep -m1 -o "statusBars frame=\[0,0\]\[[0-9]*,[0-9]*\]" | grep -o '[0-9]*\]$' | tr -d ']')
check "title below the status bar ($tb >= ${sb:-?})" '[ -n "$tb" ] && [ -n "$sb" ] && [ "$tb" -ge "$sb" ]'
check "no close button" '! dump | grep -q "text=\"✕\""'
check "the dark bottom bar with Back" 'dump | grep -q "content-desc=\"Back\""'
shot breeze_add_widget
sh_ input keyevent BACK; sleep 1.5
check "Back leaves it" '[ "$(resumed)" = SettingsActivity ]'
# Open applications lists Etesian settings, by its own name.
home; sh_ am start -n $LAUNCHER/.HomeActivity --ei etesian.page 2 >/dev/null; sleep 2.5
check "Open applications shows Etesian settings" 'dump | grep -q "Etesian settings"'
shot breeze_settings_switcher
[ "$cfg" = null ] && raw_sh settings delete secure etesian_toolbar_config >/dev/null || raw_sh settings put secure etesian_toolbar_config "'$cfg'"
sh_ am force-stop $LAUNCHER; sh_ "cat $backup > $P"; sh_ rm $backup; home
check "launcher did not crash" '! crashed $LAUNCHER'

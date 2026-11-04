# Bug 2026-11-04 (user): swiping down from the top did not close the application. A thumb coming in
# from the top bezel is often first seen just under the 62 px status bar, where the gesture didn't
# start. A clear swipe down from that band now closes the app; a tap there still reaches the app.
unlock
open_settings() { sh_ am start -a android.settings.WIFI_SETTINGS >/dev/null; sleep 3; }
settings_tasks() { sh_ dumpsys activity recents | grep -c 'A=1000:com.android.settings[} ]'; }
drag_down() { # from y, slowly, as a thumb does
    sh_ "input motionevent DOWN 540 $1; for y in \$(seq $1 60 1900); do input motionevent MOVE 540 \$y; done; sleep 0.3; input motionevent UP 540 1900"; sleep 3; }
for y in 15 90; do
    open_settings
    drag_down $y
    check "swipe down from y=$y closes the application ($(top_activity))" '[ "$(task_type "$(top_activity)")" = home ] && [ "$(settings_tasks)" = 0 ]'
done
open_settings
sh_ input tap 540 90; sleep 2
check "a tap just under the status bar stays the app's" 'top_activity | grep -q com.android.settings'
id=$(sh_ content query --uri content://media/external/images/media --projection _id | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
if [ -n "$id" ]; then
    sh_ am start -a android.intent.action.VIEW -t image/jpeg -d content://media/external/images/media/$id org.lineageos.glimpse >/dev/null; sleep 3
    drag_down 15
    check "full screen Gallery photo: swipe down closes it" '[ "$(sh_ dumpsys activity recents | grep -c "A=.*:org.lineageos.glimpse")" = 0 ]'
fi
check "launcher did not crash" '! crashed $LAUNCHER'
home

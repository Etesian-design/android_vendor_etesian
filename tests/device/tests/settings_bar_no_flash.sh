# Bug 2026-10-25 #6: in Settings the bar popped in, flashed white on Back, and was dark on Display
# but white on Wallpaper. Every frame of the bar must stay dark through opening a page and Back.
unlock; home
sh_ am start -S -a android.settings.SETTINGS >/dev/null; sleep 2.5
max() { sort -n | tail -1; }
# x=1050: the bar's right end. Further left the main page (no bar, by design: settings_bar) has its list text there,
# white titles and grey subtitles, which read as a "flash"; the picker's settings icon ends before it.
tap_text "Display"; m1=$(burst 1050 2090 6 | max); sleep 1.5
check "bar stays dark opening Display (max $m1)" '[ "$m1" -lt 120 ]'
sh_ input keyevent BACK; m2=$(burst 1050 2090 6 | max); sleep 1.5
check "bar never flashes white on Back (max $m2)" '[ "$m2" -lt 120 ]'
tap_text "Wallpaper and style"; sleep 3; m3=$(burst 1050 2090 2 | max); wp=$(top_activity)
# The main page has no bar (reads 0), so first make sure the picker is what is showing.
check "Wallpaper and style opens the picker ($wp)" 'echo "$wp" | grep -q com.android.wallpaper'
check "Wallpaper's dark page gets the dark bar ($m3)" '[ "$m3" -lt 120 ]'
sh_ input keyevent BACK; sleep 1; home

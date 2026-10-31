# Bugs 2026-10-25 #14 #15 #16: exact Belle widgets next to the existing ones, a switches widget,
# and full-colour shortcut icons. Adds the switches row if needed, flips Bluetooth twice (back
# to how it was), and checks the shortcuts widget's icons are saturated.
unlock
wp=$(pref_switch home Widgets 1)  # the Widgets view is off by default: on for the test, restored at the end
widgets_pane
seek_widget 'content-desc="Bluetooth o[nf]*"' || add_widget "Switches (Belle classic)"
seek_widget 'content-desc="Bluetooth o[nf]*"'
state() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'content-desc="Bluetooth o[nf]*"' | head -1; }
before=$(state)
check "switches widget shows Bluetooth state ($before)" '[ -n "$before" ]'
b=$(sh_ cat /sdcard/u.xml | grep -o 'content-desc="Bluetooth o[nf]*"[^>]*bounds="[^"]*"' | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ')
set -- $b; x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
sh_ input tap $x $y; sleep 4; after=$(state)
check "tapping it flips Bluetooth ($before -> $after)" '[ -n "$after" ] && [ "$after" != "$before" ]'
sh_ input tap $x $y; sleep 4
check "and back ($(state))" '[ "$(state)" = "$before" ]'
# Shortcuts widget: the Browser icon is colourful (it was desaturated to 20%).
sat=$(adb exec-out screencap -p > /tmp/hd-sat.png; python3 - <<'PY'
from PIL import Image
im=Image.open('/tmp/hd-sat.png').convert('HSV'); w,h=im.size
print(max(im.getpixel((x,y))[1] for x in range(0,w,6) for y in range(0,h,6)))
PY
)
check "widgets show full-colour icons (max saturation $sat)" '[ "$sat" -gt 150 ]'
home
pref_switch home Widgets "$wp" >/dev/null; home

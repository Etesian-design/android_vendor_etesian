# Task: home "scrolling wallpaper" through the real flow — Settings → Wallpapers → Home screen → "One
# wallpaper, scrolling with the views" opens the picker; the picked picture is switched on (it was left
# off before: nothing showed) and pans across the views. Restores the previous settings and files.
PY=${PY:-python3}
P=com.bompotis.etesian.launcher; D=/data/data/$P/files/backgrounds
unlock
old_mode=$(sh_ settings get secure etesian_home_wallpaper); old_on=$(sh_ settings get secure etesian_home_scroll_bg)
# Any picture already there is set aside, so the picker opens.
had=$(sh_ "ls $D/home_scroll-original.jpg 2>/dev/null")
[ -n "$had" ] && sh_ "mv $D/home_scroll-original.jpg $D/hdtest-keep.jpg; mv $D/home_scroll.png $D/hdtest-keep.png"
sh_ settings put secure etesian_home_wallpaper 0
# A left-to-right blue→red picture to pick.
f=$(mktemp -p "$PWD" pano.XXXX.png)
$PY -c "
from PIL import Image
im=Image.new('RGB',(2400,1600)); px=im.load()
for x in range(2400):
  c=(int(255*x/2399),40,int(255*(1-x/2399)))
  for y in range(1600): px[x,y]=c
im.save('$f')"
sh_ mkdir -p /sdcard/Pictures; adb push "$f" /sdcard/Pictures/hdtest-pano.png >/dev/null; rm -f "$f"
sh_ am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/hdtest-pano.png >/dev/null; sleep 2
sh_ am force-stop $P; sleep 1; home
sh_ am start -n $P/.SettingsActivity --es section wallpapers >/dev/null; sleep 2.5
tap_text "Home screen"; sleep 1.5; tap_text "One wallpaper, scrolling with the views"; sleep 3
check "choosing it opens the picture picker" 'top_activity | grep -q documentsui'
tap_text "hdtest-pano.png"; sleep 5
check "back in settings after picking" 'top_activity | grep -q "$P/.SettingsActivity"'
check "the scrolling wallpaper is switched on" '[ "$(sh_ settings get secure etesian_home_scroll_bg)" = 1 ]'
hue() { adb exec-out screencap -p > /tmp/hd-pa.png; $PY -c "
from PIL import ImageStat, Image; r,g,b=ImageStat.Stat(Image.open('/tmp/hd-pa.png').convert('RGB')).mean; print(int(r-b))"; }  # whole screen: one pixel can sit under content
# Start on the leftmost view: Widgets when the user has it on, else Events (the views wrap around).
first=$([ "$(pref_switch home Widgets)" = 1 ] && echo widgets || echo events)
home; for _ in 1 2 3 4; do [ "$(home_pane)" = $first ] && break; sh_ input swipe 200 1200 900 1200 250; sleep 1.2; done
a=$(hue); sh_ input swipe 900 1200 200 1200 250; sleep 1.5; b=$(hue); sh_ input swipe 900 1200 200 1200 250; sleep 1.5; c=$(hue)
check "the picture shows and pans with the views ($a < $b < $c)" '[ $a -lt $b ] && [ $b -lt $c ]'
check "launcher did not crash" '! crashed $P'
sh_ settings put secure etesian_home_wallpaper ${old_mode/null/0}
[ "$old_on" = null ] && sh_ settings delete secure etesian_home_scroll_bg >/dev/null || sh_ settings put secure etesian_home_scroll_bg "$old_on"
sh_ "rm -f $D/home_scroll-original.jpg $D/home_scroll.png"
[ -n "$had" ] && sh_ "mv $D/hdtest-keep.jpg $D/home_scroll-original.jpg; mv $D/hdtest-keep.png $D/home_scroll.png"
sh_ rm -f /sdcard/Pictures/hdtest-pano.png
sh_ content delete --uri content://media/external/images/media --where "_display_name=\'hdtest-pano.png\'" >/dev/null 2>&1
sh_ settings put secure etesian_bg_version $(date +%s)
home

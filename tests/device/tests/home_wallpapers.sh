# Bug 2026-10-25 #22: home wallpapers per view. Puts a red test picture in as the Applications
# view's wallpaper (as the settings would), and checks Applications shows it while Open
# applications stays black; then restores the previous settings.
PY=${PY:-python3}
P=com.bompotis.etesian.launcher; D=/data/data/$P/files/backgrounds
uid=$(sh_ stat -c %u /data/data/$P)
old_mode=$(sh_ settings get secure etesian_home_wallpaper); old_on=$(sh_ settings get secure etesian_page_apps_bg)
f=$(mktemp -p "$PWD" wall.XXXX.png)
$PY -c "from PIL import Image; Image.new('RGB',(1080,2160),(200,30,30)).save('$f')"
sh_ mkdir -p $D; adb push "$f" $D/page_apps.png >/dev/null; adb push "$f" /data/local/tmp/w.png >/dev/null; rm -f "$f"
sh_ "cp /data/local/tmp/w.png $D/page_apps-original.jpg; chown -R $uid:$uid $D; restorecon -R $D"
sh_ settings put secure etesian_page_apps_bg 1; sh_ settings put secure etesian_home_wallpaper 2
sh_ settings put secure etesian_bg_version $(date +%s)
unlock; home
red() { adb exec-out screencap -p > /tmp/hd-w.png; $PY -c "
from PIL import Image
im=Image.open('/tmp/hd-w.png').convert('RGB'); r,g,b=im.getpixel((540,$1)); print(1 if r>150 and g<80 else 0)"; }
for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 900 1200 200 1200 250; sleep 1.2; done
# A gap between icon rows (the wallpaper shows there).
check "Applications shows its wallpaper" '[ "$(red 120)" = 1 ] || [ "$(red 2150)" = 1 ]'
sh_ input swipe 900 1200 200 1200 250; sleep 1.5
check "Open applications stays black" '[ "$(home_pane)" = switcher ] && [ "$(red 2150)" = 0 ]'
sh_ settings put secure etesian_home_wallpaper ${old_mode/null/0}; sh_ settings put secure etesian_page_apps_bg ${old_on/null/0}
sh_ settings put secure etesian_bg_version $(date +%s)
home

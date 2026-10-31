# Etesian settings: main page has no bottom bar, sub pages the dark one (bug 2026-10-25).
P=com.bompotis.etesian.launcher
PY=${PY:-/home/memphisx/android/.venv-tools/bin/python}
# mean brightness of the bottom 3% of the right quarter (list text stays left of it) (bar ~ 20-50, no bar = 0)
bottom() { adb exec-out screencap -p > /tmp/sbar.png; $PY -c "
from PIL import Image; im=Image.open('/tmp/sbar.png').convert('L'); w,h=im.size
c=im.crop((w*3//4,int(h*.975),w,h)); print(sum(c.tobytes())//(c.size[0]*c.size[1]))"; }
unlock
sh_ "am force-stop $P"; sh_ "am start -W -n $P/.SettingsActivity" >/dev/null; sleep 2
b=$(bottom); check "main page: no bar ($b)" "[ $b -lt 6 ]"
tap_text "System"; sleep 2
b=$(bottom); check "sub page: dark bar ($b)" "[ $b -ge 10 ] && [ $b -lt 90 ]"
sh_ "input keyevent BACK"; sleep 2
b=$(bottom); check "back on main: no bar ($b)" "[ $b -lt 6 ]"
home

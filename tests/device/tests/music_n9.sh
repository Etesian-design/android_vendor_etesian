# Task: N9 Music (Twelve) — generated covers for albums without art (name in heavy caps, then repeated in a
# dark shade, on black), art square (never squircled), the item playing now tinted magenta #C4008F.
# Adds two silent tagged tracks and plays one with media volume at 0; removes them and restores the volume.
unlock
D=/home/memphisx/android/lineage/vendor/etesian/tests/device/data
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
T=org.lineageos.twelve
vol=$(sh_ "cmd media_session volume --stream 3 --get" | grep -o 'volume is [0-9]*' | grep -o '[0-9]*$')
sh_ "cmd media_session volume --stream 3 --set 0" >/dev/null
sh_ mkdir -p /sdcard/Music/hdtest; adb push $D/hdtest-sunrise.mp3 $D/hdtest-noalbum.mp3 /sdcard/Music/hdtest/ >/dev/null
for f in hdtest-sunrise hdtest-noalbum; do sh_ am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/hdtest/$f.mp3 >/dev/null; done; sleep 4
sh_ am force-stop $T; sh_ monkey -p $T -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
rgb() { adb exec-out screencap -p > /tmp/hd-mu.png; python3 -c "
from PIL import Image; im=Image.open('/tmp/hd-mu.png').convert('RGB').crop(($1)); c=im.getcolors(1<<20); print(len([1 for n,p in c if max(p)>150 and min(p)<60]), len([1 for n,p in c if 30<max(p)<110 and min(p)<25]))"; }
# Home mosaic: the first album (no art) is a generated cover: bright and dark text colours on black.
read bright dark <<< "$(rgb 0,70,350,400)"
check "generated cover on the home mosaic (bright $bright, dark $dark colours)" '[ $bright -gt 3 ] && [ $dark -gt 3 ]'
top_activity | grep -q $T && tap_text "Songs"; sleep 2
top_activity | grep -q $T && tap_text "Hdtest Sunrise"; sleep 3
read bright dark <<< "$(rgb 0,150,1080,1100)"
check "generated cover in Now playing" '[ $bright -gt 3 ] && [ $dark -gt 3 ]'
sh_ input keyevent BACK; sleep 2
check "playing song tinted magenta" 'adb exec-out screencap -p > /tmp/hd-mu.png; python3 -c "
from PIL import Image; im=Image.open(\"/tmp/hd-mu.png\").convert(\"RGB\"); import sys
sys.exit(0 if any(abs(r-0xC4)<20 and g<40 and abs(b-0x8F)<25 for n,(r,g,b) in im.getcolors(1<<20)) else 1)"'
S=/home/memphisx/android/lineage/packages/apps/Twelve/app/src/main/java/org/lineageos/twelve
check "unknown album is purple; art is not squircled in lists (source)" '[ -f $S/ext/N9Cover.kt ] && grep -q "0xFF8E10C4" $S/ext/N9Cover.kt && grep -q "clipToOutline = false" $S/fragments/N9LibraryListFragment.kt'
check "music did not crash" '! crashed org.lineageos.twelve'
sh_ input keyevent MEDIA_PAUSE; sleep 1; sh_ am force-stop $T
sh_ rm -rf /sdcard/Music/hdtest
sh_ content delete --uri content://media/external/audio/media --where "title\ like\ \'Hdtest%\'" >/dev/null 2>&1
[ -n "$vol" ] && sh_ "cmd media_session volume --stream 3 --set $vol" >/dev/null
home

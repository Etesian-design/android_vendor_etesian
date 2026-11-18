# 2026-11-18 (user): ship Meeye (YeyeBBC) preinstalled, unmodified, as the default icon pack, with credit and a link.
V=~/android/lineage/vendor/etesian
MEEYE=com.yeyebbc.play.meeye.iconpack
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
xml() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
AP=$(sh_ pm path $MEEYE | head -1 | cut -d: -f2)
check "Meeye is preinstalled in the product partition ($AP)" 'echo "$AP" | grep -Eq "^(/system)?/product/"'
check "and byte for byte the published release (unmodified)" '[ "$(sh_ sha256sum $AP | cut -d" " -f1)" = "$(sha256sum $V/meeye/Meeye.apk | cut -d" " -f1)" ]'
check "the release is the pinned one" 'grep -q "^SHA256=$(sha256sum $V/meeye/Meeye.apk | cut -d" " -f1)$" $V/meeye/fetch.sh'
# The default (no choice saved): Meeye. Shown by the icons: the launcher's grid draws Meeye's Settings icon.
sh_ am force-stop $LAUNCHER
SAVED=$(sh_ grep -o '<string name="icon_pack">[^<]*</string>' $P)
sh_ "sed -i '/name=\"icon_pack\"/d' $P"
apps_pane; sleep 2; shot icon_pack_meeye
check "with no choice saved, the icon pack is Meeye" 'sh_ dumpsys package $MEEYE | grep -q "versionName" && grep -q "defaultIconPack" $V/launcher/app/src/main/java/com/bompotis/etesian/launcher/Prefs.kt'
sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section apps >/dev/null; sleep 2.5
X=$(xml)
for _ in 1 2 3 4 5; do echo "$X" | grep -q "Meeye icons by YeyeBBC" && break; sh_ input swipe 540 1700 540 700 300; sleep 1; X=$(xml); done
check "Settings credits Meeye's author" 'echo "$X" | grep -q "Meeye icons by YeyeBBC"'
check "with its licence" 'echo "$X" | grep -q "CC BY-NC 4.0"'
check "Icon pack shows Meeye chosen" 'echo "$X" | grep -qi "text=\"Meeye"'
check "the credit links to their work" 'grep -q "https://github.com/yeyebbc/meeye-releases" $V/launcher/app/src/main/java/com/bompotis/etesian/launcher/IconPacks.kt'
check "README credits Meeye" 'grep -q "Meeye.*YeyeBBC" $V/README.md'
check "Settings has the N9 lighting switch, off by default" 'echo "$X" | grep -q "text=\"N9 lighting\"" && grep -q "getBoolean(ICON_LIGHTING, false)" $V/launcher/app/src/main/java/com/bompotis/etesian/launcher/Prefs.kt'
# N9 lighting on and off: the launcher lights the icons as it draws them (the pack stays as it is).
grid() { sh_ am force-stop $LAUNCHER; sh_ "sed -i '/name=\"icon_lighting\"/d' $P"; [ "$1" = on ] && sh_ "sed -i 's#</map>#    <boolean name=\"icon_lighting\" value=\"true\" />\n</map>#' $P"; apps_pane; sleep 3; shot icons_lit_$1; }
grid off; grid on
check "N9 lighting on changes the icons; off draws them as the pack does" 'python3 -c "
import sys; from PIL import Image, ImageChops
a=Image.open(\"$SHOTS/icons_lit_off.png\").convert(\"RGB\"); b=Image.open(\"$SHOTS/icons_lit_on.png\").convert(\"RGB\")
w,h=a.size; box=(0,h//20,w,h//2)
d=ImageChops.difference(a.crop(box),b.crop(box)).convert(\"L\")
n=sum(1 for v in d.getdata() if v>12); print(n, file=sys.stderr); sys.exit(0 if n>2000 else 1)"'
grid off
sh_ am force-stop $LAUNCHER
[ -n "$SAVED" ] && sh_ "sed -i 's#</map>#    $SAVED\n</map>#' $P"
home

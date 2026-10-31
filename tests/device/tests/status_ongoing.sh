# Unified ongoing statuses (approved "Recording" state, item 5): casting and screen sharing are
# shown like a recording — the one red status-bar dot (not Android's red pill with a timer) and a
# status-menu row "Sharing the screen · <app> · tap to stop" in the recording row's form; tapping
# the row stops it. The test starts a real screen capture by the launcher (no consent dialog,
# nothing is recorded or saved) and always stops it.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
desc() { dump | grep -q "content-desc=\"$1\""; }
tap_desc() { local b; b=$(dump | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1); set -- $(echo "$b" | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
projecting() { sh_ dumpsys media_projection | grep -q "Media Projection: .*$LAUNCHER\|$LAUNCHER" ; }
# Width of the red mark in the status bar (0 = none).
red_w() { adb exec-out screencap -p > /tmp/hd-ongo.png; /home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open('/tmp/hd-ongo.png').convert('RGB'); w,h=im.size
xs=[x for y in range(0,80) for x in range(0,w) if (lambda p: p[0]>200 and p[1]<90 and p[2]<90)(im.getpixel((x,y)))]
print(max(xs)-min(xs)+1 if len(xs)>20 else 0)"; }
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
unlock; home
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind unproject >/dev/null; sleep 1
check "Etesian mode (dots replace the chips)" '[ "$(sh_ settings get secure etesian_gestures)" = 1 ]'
check "no screen capture before" '[ "$(red_w)" = 0 ]'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind project >/dev/null; sleep 3
check "a real screen capture is active" 'projecting'
w=$(red_w)
shot ongoing_dot
check "status bar: the red dot ($w px wide), not a red pill" '[ "$w" -gt 0 ] && [ "$w" -lt 40 ]'
menu
check "status menu row: Sharing the screen" 'desc "Sharing the screen"'
check "row line: <app> · tap to stop" 'dump | grep -q "text=\"[^\"]* · tap to stop\""'
check "the same row form as Recording the screen (lit squircle, 2 lines)" 'dump | grep -q "text=\"Sharing the screen\""'
shot ongoing_row
tap_desc "Sharing the screen"; sleep 2
check "tapping the row stops the capture" '! projecting'
sh_ input keyevent BACK; sleep 1; home; sleep 1
check "the dot is gone" '[ "$(red_w)" = 0 ]'
# While SystemUI's own recorder runs, only its row shows (no second "Sharing the screen").
sh_ cmd media_session volume --stream 5 --get >/dev/null 2>&1
start=$(sh_ date +%s)
nv=$(sh_ cmd media_session volume --stream 5 --get 2>/dev/null | grep -o "volume is [0-9]*" | grep -o "[0-9]*$"); sh_ cmd media_session volume --stream 5 --set 0 >/dev/null 2>&1
sh_ settings put secure etesian_screen_record "start:$(date +%s%N)"; sleep 5
menu
check "recording: one row, Recording the screen" 'desc "Recording the screen" && ! desc "Sharing the screen"'
sh_ input keyevent BACK
sh_ settings put secure etesian_screen_record "stop:$(date +%s%N)"; sleep 3
check "nothing left recording or capturing" '[ "$(sh_ settings get secure etesian_screen_recording)" = 0 ] && ! projecting'
sh_ content delete --uri content://media/external/video/media --where "date_added\>=$start" >/dev/null 2>&1
sh_ "find /sdcard/Movies /sdcard/DCIM -name '*.mp4' -newermt @$start -delete" 2>/dev/null
[ -n "$nv" ] && sh_ cmd media_session volume --stream 5 --set $nv >/dev/null 2>&1
sh_ cmd notification cancel_all >/dev/null 2>&1
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind unproject >/dev/null
check "systemui did not crash" '! crashed com.android.systemui'
check "launcher did not crash" '! crashed $LAUNCHER'
home

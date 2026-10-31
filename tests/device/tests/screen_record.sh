# Android extras → status menu strip, 6th tile "Screen recorder" (mock 1a) and the red status-bar
# dot (mock "Recording dot"): tap starts a whole-screen recording without audio, the dot shows,
# tapping the dot asks "Stop recording?" and stops it. Silent: notification/system streams are
# muted for the run; the recording made is deleted afterwards and never left running.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
desc() { dump | grep -q "content-desc=\"$1\""; }
tap_desc() { local b; b=$(dump | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1); set -- $(echo "$b" | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
rec() { [ "$(sh_ settings get secure etesian_screen_recording)" -gt 0 ] 2>/dev/null; }
red_dot() { adb exec-out screencap -p > /tmp/hd-rec.png; /home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open('/tmp/hd-rec.png').convert('RGB'); w,h=im.size
pts=[(x,y) for y in range(0,80) for x in range(0,w) if (lambda p: p[0]>200 and p[1]<90 and p[2]<90)(im.getpixel((x,y)))]
print('%d %d' % (sum(p[0] for p in pts)//len(pts), sum(p[1] for p in pts)//len(pts)) if len(pts)>20 else '')"; }
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
old=$(sh_ settings get secure etesian_extra_status_menu)
nv=$(sh_ cmd media_session volume --stream 5 --get 2>/dev/null | grep -o "volume is [0-9]*" | grep -o "[0-9]*$")
sv=$(sh_ cmd media_session volume --stream 1 --get 2>/dev/null | grep -o "volume is [0-9]*" | grep -o "[0-9]*$")
sh_ cmd media_session volume --stream 5 --set 0 >/dev/null 2>&1; sh_ cmd media_session volume --stream 1 --set 0 >/dev/null 2>&1
sh_ cp $P /data/local/tmp/hl_prefs_rec.bak 2>/dev/null; sh_ rm -f $P
start=$(sh_ date +%s)
unlock; home
sh_ settings put secure etesian_extra_status_menu 1; sh_ am force-stop $LAUNCHER; sleep 1
menu
check "strip has the Screen recorder tile (6 tiles)" 'desc "Screen recorder"'
shot status_strip6
check "not recording before" '! rec'
sh_ logcat -c; tap_desc "Screen recorder"; sleep 5
check "tap starts a recording" 'rec'
check "no audio (audio source NONE)" 'sh_ logcat -d | grep -q "recording with audio source NONE"'
xy=$(red_dot)
shot recording_dot
check "the red dot is in the status bar" '[ -n "$xy" ]'
menu
check "status menu row: Recording the screen" 'desc "Recording the screen"'
shot recording_row
sh_ input keyevent BACK; sleep 1
if [ -n "$xy" ]; then sh_ input tap $xy; sleep 1.5; fi
check "tapping the dot asks to stop" 'dump | grep -qi "stop recording"'
shot recording_stop_dialog
b=$(dump | grep -o 'text="Stop recording"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | tail -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
[ -n "$b" ] && sh_ input tap $b; sleep 3
check "stopped" '! rec'
# Never leave one running.
rec && { sh_ settings put secure etesian_screen_record "stop:$(date +%s)"; sleep 3; }
check "nothing left recording" '! rec'
# Delete what the test recorded.
sh_ content delete --uri content://media/external/video/media --where "date_added\>=$start" >/dev/null 2>&1
sh_ "find /sdcard/Movies /sdcard/DCIM -name '*.mp4' -newermt @$start -delete" 2>/dev/null
check "test recording deleted" '[ -z "$(sh_ "find /sdcard/Movies /sdcard/DCIM -name \"*.mp4\" -newermt @$start" 2>/dev/null)" ]'
sh_ cmd notification cancel_all >/dev/null 2>&1
# Stop the launcher first: a running one writes its in-memory settings over the restored file.
sh_ am force-stop $LAUNCHER
sh_ "cp /data/local/tmp/hl_prefs_rec.bak $P && chown \$(stat -c %u:%g /data/data/$LAUNCHER) $P && chmod 660 $P && restorecon $P" 2>/dev/null
[ "$old" = null ] && sh_ settings delete secure etesian_extra_status_menu >/dev/null || sh_ settings put secure etesian_extra_status_menu "$old"
[ -n "$nv" ] && sh_ cmd media_session volume --stream 5 --set $nv >/dev/null 2>&1
[ -n "$sv" ] && sh_ cmd media_session volume --stream 1 --set $sv >/dev/null 2>&1
sh_ am force-stop $LAUNCHER
check "systemui did not crash" '! crashed com.android.systemui'
home

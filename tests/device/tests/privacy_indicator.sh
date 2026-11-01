# Settings → privacy indicators (mock 12): while the camera is in use a green squircle camera
# glyph in the status bar and "Camera in use · <app> · tap to stop" at the top of the status menu.
# Silent: the camera app only shows its preview (no photo is taken).
check "privacy glyph code" 'grep -q etesian_extra_privacy /home/memphisx/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/events/PrivacyDotViewController.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
old=$(sh_ settings get secure etesian_extra_privacy)
unlock; home
sh_ settings put secure etesian_extra_privacy 1
sh_ am start -a android.media.action.STILL_IMAGE_CAMERA >/dev/null 2>&1; sleep 6
cam=$(top_activity | cut -d/ -f1)
# The camera is full screen: pull the status bar in, then the glyph is a real status bar icon.
sh_ input swipe 540 2 540 200 150; sleep 1.2
shot privacy_bar
check "status bar glyph (camera slot shown)" 'sh_ dumpsys activity service com.android.systemui/.SystemUIService StatusBarIconControllerImpl | grep "(camera)" | grep -q "visible=true"'
check "status bar glyph is green" '/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image
im=Image.open(\"$SHOTS/privacy_bar.png\").convert(\"RGB\").crop((0,0,1080,62))
import sys; sys.exit(0 if any(g>150 and r<140 and b<140 for r,g,b in im.getdata()) else 1)"'
sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5
check "status menu privacy row" 'dump | grep -q "text=\"Camera in use\""'
shot privacy_menu
sh_ input keyevent BACK; sleep 1
sh_ am force-stop "$cam"; sleep 3
sh_ settings put secure etesian_extra_privacy 0
sh_ am start -a android.media.action.STILL_IMAGE_CAMERA >/dev/null 2>&1; sleep 5
sh_ input swipe 540 2 540 200 150; sleep 1.2
check "off: no status bar glyph" '! sh_ dumpsys activity service com.android.systemui/.SystemUIService StatusBarIconControllerImpl | grep "(camera)" | grep -q "visible=true"'
sh_ am force-stop "$cam"; sleep 2
[ "$old" = null ] && sh_ settings delete secure etesian_extra_privacy >/dev/null || sh_ settings put secure etesian_extra_privacy "$old"
check "systemui did not crash" '! crashed com.android.systemui'
home

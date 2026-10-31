# Android extras → "Copied" bubble (story Phone v2, state copied): copying shows the notification
# bubble "Copied · Text copied to clipboard" instead of the clipboard overlay. Off by default.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bubble() { sh_ dumpsys window windows | grep -q "EtesianBubble"; }
# The bubble's lit green check tile sits at the top left, just under the status bar.
green() { /home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open('$SHOTS/$1.png').convert('RGB')
px=[im.getpixel((x,y)) for x in range(40,220,4) for y in range(80,260,4)]
import sys; sys.exit(0 if sum(1 for r,g,b in px if g>140 and g>r+40 and g>b+40)>150 else 1)"; }
copy() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind copy --es text "'$1'" >/dev/null; }
old=$(sh_ settings get secure etesian_extra_copied)
unlock; home
sh_ settings put secure etesian_extra_copied 0; copy "Off test"; sleep 1
shot copied_off
check "off: no Copied bubble" '! bubble && ! green copied_off'
sh_ input keyevent BACK; sleep 2; home
sh_ settings put secure etesian_extra_copied 1; copy "On test"; sleep 0.8
shot copied_bubble
check "on: the Copied bubble with the green check" 'bubble && green copied_bubble'
# Solid, like the mock: over the app grid (the Calendar icon sits behind the bubble's right part)
# its background stays one dark colour.
check "on: the bubble is solid (nothing shows through)" '/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open(\"$SHOTS/copied_bubble.png\").convert(\"RGB\"); w,h=im.size
px=[im.getpixel((x,y)) for x in range(w-330,w-60,6) for y in range(205,235,3)]
import sys; sys.exit(0 if all(max(p)<45 for p in px) else 1)"'
check "on: no stock clipboard overlay" '! dump | grep -q "clipboard_overlay\|clipboard_preview"'
check "the clip itself is not shown" '! dump | grep -q "On test"'
sleep 6
check "bubble goes away by itself" '! bubble'
[ "$old" = null ] && sh_ settings delete secure etesian_extra_copied >/dev/null || sh_ settings put secure etesian_extra_copied $old
check "SystemUI did not crash" '! crashed com.android.systemui'
home

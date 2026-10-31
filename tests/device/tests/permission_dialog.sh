# N9 permission dialog (mock 15): the app's squircle icon, a light centred question and stacked N9
# buttons (Allow blue, the rest dark). Shown by revoking the camera app's Camera permission (no picture is taken) and opening it;
# nothing is granted or recorded (Back denies). PermissionController is installed as an update of
# the APEX copy (same key); "pm uninstall-system-updates com.android.permissioncontroller" reverts.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
APP=org.lineageos.aperture
check "PermissionController update carries the N9 dialog" 'sh_ pm path com.android.permissioncontroller | grep -q /data/app/'
unlock; home
sh_ pm revoke $APP android.permission.CAMERA 2>/dev/null; sh_ pm clear-permission-flags $APP android.permission.CAMERA user-set user-fixed 2>/dev/null
sh_ monkey -p $APP -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
check "permission dialog shown" 'top_activity | grep -q permissioncontroller'
check "N9 buttons stacked: Allow above the rest" 'b=$(dump); a=$(echo "$b" | grep -o "text=\"[^\"]*[Aa]llow[^\"]*\"[^>]*bounds=\"\[[0-9]*,[0-9]*" | head -2 | grep -o "[0-9]*$" | tr "\n" " "); set -- $a; [ -n "$2" ] && [ "$1" -lt "$2" ]'
check "first button reads just Allow (mock 15)" 'dump | grep -q "text=\"Allow\""'
shot permission_dialog
check "Allow is N9 blue" '/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image
im=Image.open(\"$SHOTS/permission_dialog.png\").convert(\"RGB\")
import sys; sys.exit(0 if sum(1 for r,g,b in im.getdata() if b>200 and 100<g<190 and r<110)>3000 else 1)"'
sh_ input keyevent BACK; sleep 1; sh_ am force-stop $APP
sh_ pm grant $APP android.permission.CAMERA 2>/dev/null
check "permissioncontroller did not crash" '! crashed com.android.permissioncontroller'
home

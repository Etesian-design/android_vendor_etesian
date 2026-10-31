# Every signature|privileged permission the launcher asks for is in its privapp allowlist (one
# missing boot-loops the phone, 2026-10-25), both in the source tree and on the device.
V=$(cd "$(dirname "$0")/../.." 2>/dev/null; pwd); V=${V%/tests}
M=$V/launcher/app/src/main/AndroidManifest.xml; A=$V/permissions/privapp_allowlist_com.bompotis.etesian.launcher.xml
for p in $(grep -o 'uses-permission android:name="[^"]*"' "$M" | cut -d'"' -f2); do
  sh_ dumpsys package permission $p | grep -q 'prot=.*privileged' || continue
  check "$p allowlisted in the tree" 'grep -q "\"$p\"" "$A"'
  check "$p allowlisted on the device" 'sh_ cat /system_ext/etc/permissions/privapp_allowlist_com.bompotis.etesian.launcher.xml | grep -q "\"$p\""'
done

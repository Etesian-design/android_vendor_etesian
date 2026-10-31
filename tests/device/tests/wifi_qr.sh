# Android extras → Share network QR (story Phone v2, state wifi-qr): "Share network" in Etesian
# settings → a dim sheet with the QR code, "SSID · password" and Close. Scanning is the camera's:
# no Scan row, and the status menu's Wi-Fi long-press never shows an invented sheet.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
lp() { set -- $(bounds "text=\"$1\""); [ -n "$4" ] && sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; }
old=$(sh_ settings get secure etesian_extra_wifi_qr)
check "READ_WIFI_CREDENTIAL granted (allowlisted)" 'sh_ dumpsys package $LAUNCHER | grep -q "READ_WIFI_CREDENTIAL: granted=true"'
unlock; home
sh_ settings put secure etesian_extra_wifi_qr 0; menu; lp "Internet connection"; sleep 1.5
check "off: long-press opens the stock Internet panel, no sheet" '! dump | grep -q "content-desc=\"Wi-Fi actions\""'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1; home
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity >/dev/null; sleep 2
check "off: no Share network row in Etesian settings" '! dump | grep -q "text=\"Share network\""'
sh_ settings put secure etesian_extra_wifi_qr 1; home
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity >/dev/null; sleep 2
check "on: Share network row, no Scan row" 'dump | grep -q "text=\"Share network\"" && ! dump | grep -q "text=\"Scan network QR code\""'
tap_text "Share network"; sleep 2
shot wifi_share_raw
connected=$(sh_ dumpsys wifi | grep -m1 "mWifiInfo SSID" | grep -vc "<unknown ssid>")
check "Share network sheet with the QR code" 'dump | grep -q "text=\"Share network\"" && dump | grep -q "content-desc=\"QR code\""'
check "network and password line (connected: $connected)" '[ "$connected" = 0 ] || dump | grep -q "content-desc=\"Network and password\""'
check "the QR code is dark on white" '/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image; im=Image.open(\"$SHOTS/wifi_share_raw.png\").convert(\"L\"); w,h=im.size
px=[im.getpixel((x,y)) for x in range(w//2-150,w//2+150,3) for y in range(h//2-150,h//2+150,3)]
import sys; sys.exit(0 if sum(p>230 for p in px)>len(px)*0.3 and sum(p<40 for p in px)>len(px)*0.2 else 1)"'
tap_text Close; sleep 1.5
check "Close dismisses the sheet" '! top_activity | grep -q WifiShareActivity'
home; menu; lp "Internet connection"; sleep 1.5
check "on: status menu long-press shows no Wi-Fi sheet" '! dump | grep -q "content-desc=\"Wi-Fi actions\""'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1
[ "$old" = null ] && sh_ settings delete secure etesian_extra_wifi_qr >/dev/null || sh_ settings put secure etesian_extra_wifi_qr $old
check "launcher did not crash" '! crashed $LAUNCHER'
# The raw shot holds the real password and its QR: keep only a blurred copy.
/home/memphisx/android/.venv-tools/bin/python -c "
from PIL import Image, ImageFilter; im=Image.open('$SHOTS/wifi_share_raw.png'); w,h=im.size
box=(0,int(h*0.33),w,int(h*0.63)); im.paste(im.crop(box).filter(ImageFilter.GaussianBlur(18)),box); im.save('$SHOTS/wifi_share.png')"
rm -f $SHOTS/wifi_share_raw.png
home

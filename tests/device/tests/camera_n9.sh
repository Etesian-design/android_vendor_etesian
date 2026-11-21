# 2026-11-20 (user, N9 walkthrough, n9-ref/camera/SPEC.md): the N9 camera. All picture, no toolbar. Top left the
# strip pill (face, scene, megapixels) that opens "Image capture settings"; top right the flash (photo: a strip of
# Auto / On / Off / Red-eye); bottom left the photo/video switch, bottom centre the shutter, bottom right the gallery.
# A tap on the picture focuses and never shoots; the shutter shoots.
A() { command adb "$@"; }
# The live preview keeps the window busy: uiautomator sometimes dumps only android:id/content (or fails); retry until
# Aperture's own views are in it.
dump() { local d _; for _ in 1 2 3 4 5 6 7 8; do A shell rm -f /sdcard/u.xml; A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  d=$(A shell cat /sdcard/u.xml 2>/dev/null); echo "$d" | grep -q 'org.lineageos.aperture:id/' && break; sleep 1; done; echo "$d"; }
b() { echo "$u" | grep -o "resource-id=\"org.lineageos.aperture:id/$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   '; }
size=$(A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); SW=${size%x*}; SH=${size#*x}
unlock
photos() { A shell ls /sdcard/DCIM/Camera 2>/dev/null | grep -c . ; }
wait_photo() { local _; for _ in $(seq 12); do sleep 1; [ "$(photos)" -gt "$1" ] && break; done; photos; }
sh_ am force-stop org.lineageos.aperture
sh_ am start -n org.lineageos.aperture/.CameraLauncher >/dev/null 2>&1 || sh_ monkey -p org.lineageos.aperture -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4; u=$(dump); shot camera_n9

read -r vl vt vr vb _ <<< "$(b viewFinder) 0 0 0 0"; check "the picture fills the screen ($vt..$vb)" '[ $vb -ge $((SH * 95 / 100)) ] && [ $vt -le $((SH / 20)) ]'
check "no toolbar (no Back button drawn)" '! echo "$u" | grep -q "content-desc=\"Back\""'
read -r pl pt pr pb _ <<< "$(b n9InfoPill) 9999 9999 0 0"; check "the strip pill is top left ($pl,$pt)" '[ $pl -lt $((SW / 5)) ] && [ $pt -lt $((SH / 8)) ]'
read -r fl ft fr fb _ <<< "$(b flashButton) 0 9999 0 0"; check "the flash is top right ($fr,$ft)" '[ $fr -gt $((SW * 4 / 5)) ] && [ $ft -lt $((SH / 8)) ]'
read -r ml mt mr mb _ <<< "$(b n9ModeSwitch) 9999 0 0 0"; check "photo/video is bottom left ($ml,$mt)" '[ $ml -lt $((SW / 5)) ] && [ $mt -gt $((SH * 3 / 4)) ]'
read -r sl st sr sb _ <<< "$(b shutterButton) 0 0 0 0"; soff=$(( (sl + sr) / 2 - SW / 2 ))
check "the shutter is bottom centre (off centre $soff, top $st)" '[ $soff -gt -40 ] && [ $soff -lt 40 ] && [ $st -gt $((SH * 3 / 4)) ]'
check "the shutter is the largest control" '[ $((sr - sl)) -gt $((fr - fl)) ]'
read -r gl gt gr gb _ <<< "$(b galleryButtonCardView) 0 0 0 0"; check "the gallery is bottom right ($gr,$gt)" '[ $gr -gt $((SW * 4 / 5)) ] && [ $gt -gt $((SH * 3 / 4)) ]'
check "no lens selector, mode tabs or bottom bars" '! echo "$u" | grep -q "id/\(lensSelectorLayout\|cameraModeSelectorLayout\|primaryBarLayout\|secondaryBottomBarLayout\)\""'
check "photo is the current mode" 'echo "$u" | grep -o "resource-id=\"org.lineageos.aperture:id/n9ModePhoto\"[^>]*" | grep -q "selected=\"true\""'

# The flash strip: four choices (Auto, On, Off, Red-eye; red-eye only where the camera has the AE mode).
A shell input tap $(( (fl + fr) / 2 )) $(( (ft + fb) / 2 )); sleep 1; u=$(dump); shot camera_n9_flash
n=$(echo "$u" | grep -o 'resource-id="org.lineageos.aperture:id/n9FlashStrip".*' | grep -o 'content-desc="\(Automatic\|On\|Off\|Red-eye reduction\)"' | wc -l)
check "the flash strip opens with its choices ($n of 4)" '[ "$n" -ge 3 ]'
echo "$u" | grep -q 'content-desc="Red-eye reduction"' || echo "  note: no red-eye (the camera lacks CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)"
A shell input keyevent BACK; sleep 1

# A tap on the picture focuses; it does not take a photo. The brackets stay.
n0=$(photos); A shell input tap $((SW / 2)) $((SH / 2)); sleep 4; n1=$(photos); u=$(dump)
check "a tap on the picture takes no photo ($n0 -> $n1)" '[ "$n1" -eq "$n0" ]'
check "the focus brackets are shown" 'echo "$u" | grep -q "resource-id=\"org.lineageos.aperture:id/viewFinderFocus\""'

# The shutter takes one. The first photo after the camera starts can take a while (the Pro1X: over 4 s).
A shell input tap $(( (sl + sr) / 2 )) $(( (st + sb) / 2 )); n2=$(wait_photo "$n1")
check "the shutter takes a photo ($n1 -> $n2)" '[ "$n2" -gt "$n1" ]'
newest=$(A shell ls -t /sdcard/DCIM/Camera | head -1 | tr -d '\r'); [ "$n2" -gt "$n1" ] && sh_ rm -f "/sdcard/DCIM/Camera/$newest"

# The pill opens the settings page, with the dark toolbar's Back.
sleep 1; A shell input tap $(( (pl + pr) / 2 )) $(( (pt + pb) / 2 )); sleep 1.5; u=$(dump); shot camera_n9_settings
check "the pill opens Image capture settings" 'echo "$u" | grep -q "text=\"Image capture settings\""'
check "the settings page has Self-timer" 'echo "$u" | grep -q "text=\"Self-timer\""'
A shell input keyevent BACK; sleep 1; u=$(dump)
check "Back returns to the viewfinder" '! echo "$u" | grep -q "text=\"Image capture settings\"" && echo "$u" | grep -q "id/shutterButton\""'

sh_ am force-stop org.lineageos.aperture
check "the camera did not crash" '! crashed org.lineageos.aperture'
home

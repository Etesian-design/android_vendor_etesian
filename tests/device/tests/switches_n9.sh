# User 2026-11-19: "we need to adopt the flip switches in everything we have on the Lineage port of ours. We are still
# using the android in a lot of places." One framework hook (CompoundButton -> EtesianSwitchLook) gives every switch the
# N9's, whichever library made it (framework Switch, AppCompat SwitchCompat, Material MaterialSwitch/SwitchMaterial).
# Measured on the user's N9: a 42 x 26dp track with squircle-half ends, off flat #2D2E2D, on blue (#4195DE..#107DD6),
# a white squircle knob 18-19dp, inset 3dp. For each app: find a switch, read its pixels along the middle row.
unlock
D=$(sh_ wm density | grep -o '[0-9]*' | tail -1); dp() { echo $(( $1 * D / 160 )); }
sh_ settings put secure etesian_gestures 1
# switches <shot>: one line per switch on screen: "<checked> <l> <t> <r> <b>"
switches() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep 'class="android.widget.Switch"' \
  | sed -E 's/.*checked="([a-z]*)".*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4 \5/'; }
# look <png> <checked> <l> <t> <r> <b>: "<track px wide> <track colour ok 0/1> <white knob px wide>"
look() { python3 -c "
from PIL import Image
im=Image.open('$1').convert('RGB'); on='$2'=='true'; l,t,r,b=$3,$4,$5,$6; y=(t+b)//2
row=[im.getpixel((x,y)) for x in range(l,r)]
def trk(c): return (c[2]>190 and c[0]<90 and 110<c[1]<170) if on else (all(abs(v-46)<=12 for v in c))
xs=[i for i,c in enumerate(row) if trk(c)]; ws=[i for i,c in enumerate(row) if min(c)>235]
w=(max(xs+ws)-min(xs+ws)+1) if xs else 0
print(w, 1 if len(xs)>=$(dp 10) else 0, len(ws))"; }
app() {  # app <name> <package> <am start args...>
  local name=$1 pkg=$2; shift 2
  sh_ am force-stop $pkg; sh_ am start -W "$@" >/dev/null 2>&1; sleep 2.5
  local s; s=$(switches); shot "switch_$name"
  check "$name: has switches to look at ($(echo "$s" | grep -c .))" '[ -n "$s" ]'
  [ -n "$s" ] || return
  set -- $(echo "$s" | head -1); local c=$1 l=$2 t=$3 r=$4 b=$5
  set -- $(look "$SHOTS/switch_$name.png" $c $l $t $r $b); local w=$1 col=$2 k=$3
  check "$name: the N9 track colour ($( [ "$c" = true ] && echo on || echo off): $( [ $col = 1 ] && echo yes || echo no))" '[ $col = 1 ]'
  check "$name: the N9 size, 42dp wide ($w px; $(dp 38)-$(dp 47))" '[ $w -ge $(dp 38) ] && [ $w -le $(dp 47) ]'
  check "$name: a white knob 18-19dp ($k px)" '[ $k -ge $(dp 14) ] && [ $k -le $(dp 21) ]'
  check "$name did not crash" '! crashed $pkg'
}
app wifi com.android.settings -a android.settings.WIFI_SETTINGS
app display com.android.settings -a android.settings.DISPLAY_SETTINGS
app clock com.android.deskclock -n com.android.deskclock/.settings.SettingsActivity
app messaging com.android.messaging -n com.android.messaging/.ui.appsettings.ApplicationSettingsActivity
app location com.android.settings -a android.settings.LOCATION_SOURCE_SETTINGS
app browser org.lineageos.jelly -n org.lineageos.jelly/.SettingsActivity

# Jetpack Compose switches (Settings' Spa pages, SystemUI's Compose): EtesianSwitch (SettingsLib Spa and SystemUI's
# PlatformComposeCore) draws the same N9 switch in Etesian mode, Material 3's otherwise. A Compose switch row is one
# node across the row, so the switch is found by its track colour in the row's right part.
# clook <png> <checked> <l> <t> <r> <b>: "<track px wide> <track colour ok 0/1> <white px inside the track>"
clook() { python3 -c "
from PIL import Image
im=Image.open('$1').convert('RGB'); on='$2'=='true'; l,t,r,b=$3,$4,$5,$6; y=(t+b)//2
l=max(l, r-$(dp 110))
def trk(c): return (c[2]>190 and c[0]<90 and 110<c[1]<170) if on else (all(abs(v-46)<=12 for v in c))
best=(0,0,0)
for yy in range(y-$(dp 3), y+$(dp 3)+1):
    row=[im.getpixel((x,yy)) for x in range(l,r)]
    xs=[i for i,c in enumerate(row) if trk(c)]
    if not xs: continue
    a,z=min(xs),max(xs); k=sum(1 for c in row[a:z+1] if min(c)>235)
    if z-a+1>best[0]: best=(z-a+1, 1 if len(xs)>=$(dp 10) else 0, k)
print(*best)"; }
# Compose rows: a checkable node (class View), one per switch row.
cswitches() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep 'checkable="true"' \
  | sed -E 's/.*checked="([a-z]*)".*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4 \5/'; }
capp() {  # capp <name> <package> <am start args...>
  local name=$1 pkg=$2; shift 2
  sh_ am force-stop $pkg; sh_ am start -W "$@" >/dev/null 2>&1; sleep 3
  # Some pages open on their app list (the package is not taken): the Phone app's page has the switch.
  [ -n "$(cswitches)" ] || { tap_text Phone; sleep 2.5; }
  local s; s=$(cswitches); shot "switch_$name"
  check "$name (Compose): has switches to look at ($(echo "$s" | grep -c .))" '[ -n "$s" ]'
  [ -n "$s" ] || return
  set -- $(echo "$s" | head -1); local c=$1 l=$2 t=$3 r=$4 b=$5
  set -- $(clook "$SHOTS/switch_$name.png" $c $l $t $r $b); local w=$1 col=$2 k=$3
  check "$name (Compose): the N9 track colour ($( [ "$c" = true ] && echo on || echo off): $( [ "$col" = 1 ] && echo yes || echo no))" '[ "$col" = 1 ]'
  check "$name (Compose): the N9 size, 42dp wide ($w px; $(dp 38)-$(dp 47))" '[ $w -ge $(dp 38) ] && [ $w -le $(dp 47) ]'
  check "$name (Compose): a white knob 18-19dp ($k px)" '[ $k -ge $(dp 14) ] && [ $k -le $(dp 21) ]'
  check "$name did not crash" '! crashed $pkg'
}
capp overlay com.android.settings -a android.settings.action.MANAGE_OVERLAY_PERMISSION -d package:com.android.dialer
capp write_settings com.android.settings -a android.settings.action.MANAGE_WRITE_SETTINGS -d package:com.android.dialer

# ThemePicker (Wallpaper and style → Home screen → Colours): its Compose "Dark theme" switch. (WallpaperPicker2's
# preview sheet switch is patched the same way, but only shows for wallpapers with effects, which this ROM has none of.)
tap_node() { set -- $(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' | grep "text=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | tr -cs '0-9' ' ')
  [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
W=com.android.wallpaper
sh_ am force-stop $W; sh_ am start -W -a android.intent.action.SET_WALLPAPER -p $W >/dev/null 2>&1; sleep 3.5
sh_ input swipe 900 700 200 700 300; sleep 2   # the Home screen page
sh_ input swipe 540 1700 540 900 300; sleep 1.5; tap_node Colours; sleep 3
s=$(cswitches | head -1); shot switch_themepicker
check "themepicker (Compose): the Dark theme switch is there ($s)" '[ -n "$s" ]'
if [ -n "$s" ]; then
  set -- $s; set -- $(clook "$SHOTS/switch_themepicker.png" $1 $2 $3 $4 $5); tw=$1 tc=$2 tk=$3
  check "themepicker (Compose): the N9 track colour ($tc)" '[ "$tc" = 1 ]'
  check "themepicker (Compose): the N9 size, 42dp wide ($tw px)" '[ $tw -ge $(dp 38) ] && [ $tw -le $(dp 47) ]'
  check "themepicker (Compose): a white knob 18-19dp ($tk px)" '[ $tk -ge $(dp 14) ] && [ $tk -le $(dp 21) ]'
fi
check "themepicker did not crash" '! crashed $W'
sh_ am force-stop $W

# Off in plain Android mode: the apps' own switches come back.
sh_ settings put secure etesian_gestures 0
sh_ am force-stop com.android.settings; sh_ am start -W -a android.settings.WIFI_SETTINGS >/dev/null 2>&1; sleep 2.5
s=$(switches); shot switch_plain; set -- $(echo "$s" | head -1) x 0 0 1 1
set -- $(look "$SHOTS/switch_plain.png" $1 $2 $3 $4 $5); pw=$1
check "outside Etesian mode switches are Android's again (track $pw px, not the N9's)" '[ $pw -lt $(dp 38) ] || [ $pw -gt $(dp 47) ]'
sh_ am force-stop com.android.settings; sh_ am start -W -a android.settings.action.MANAGE_OVERLAY_PERMISSION -d package:com.android.dialer >/dev/null 2>&1; sleep 3
[ -n "$(cswitches)" ] || { tap_text Phone; sleep 2.5; }
s=$(cswitches); shot switch_plain_compose; set -- $(echo "$s" | head -1) x 0 0 1 1
pc=$1; check "outside Etesian mode the Compose page has its switch ($pc)" '[ "$pc" != x ]'
set -- $(clook "$SHOTS/switch_plain_compose.png" $1 $2 $3 $4 $5); pw=$1 pk=$3
check "outside Etesian mode Compose switches are Material's again (N9 track $pw px, knob $pk px)" '[ $pw -lt $(dp 38) ] || [ $pw -gt $(dp 47) ] || [ $pk -lt $(dp 14) ]'
sh_ settings put secure etesian_gestures 1
home

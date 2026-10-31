# Android extras → Emergency information on the lock screen (design A2, stories medical-a2 / medical-card).
# Off by default; on: owner block left of the clock (never overlapping it, also with a larger font);
# tap opens the full-screen card over the lock screen (still locked); its contacts and the only
# Emergency call button exist (nothing is called); Close returns to the lock screen.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
relock() { sh_ input keyevent SLEEP; sleep 2; sh_ input keyevent WAKEUP; sleep 2.5; }
PY=/home/memphisx/android/.venv-tools/bin/python
# Left edge of the clock: leftmost bright pixel in the clock rows of a lock-screen shot without the block.
clock_left() { $PY -c "
from PIL import Image; im=Image.open('$1').convert('L'); w,h=im.size
xs=[x for y in range($2,$3,2) for x in range(w//4,w) if im.getpixel((x,y))>200]
print(min(xs) if xs else w)"; }
keys="etesian_extra_emergency etesian_emergency_name etesian_emergency_blood etesian_emergency_allergies etesian_emergency_medication etesian_emergency_notes etesian_emergency_donor etesian_emergency_contacts etesian_emergency_show"
declare -A eold; for k in $keys; do eold[$k]=$(sh_ settings get secure $k); done
fs=$(sh_ settings get system font_scale)
sh_ settings put secure etesian_extra_emergency 0
sh_ settings put secure etesian_emergency_name "'Anna Virtanen'"; sh_ settings put secure etesian_emergency_blood "A+"
sh_ settings put secure etesian_emergency_allergies Penicillin; sh_ settings put secure etesian_emergency_medication None
sh_ settings put secure etesian_emergency_notes "'Asthma, inhaler in the bag'"; sh_ settings put secure etesian_emergency_donor Yes
sh_ "settings put secure etesian_emergency_contacts 'Mikko Laine | Partner | +358401234567
Sara Koski | Sister | +358409876543'"; sh_ settings delete secure etesian_emergency_show >/dev/null
relock
check "off: no emergency block on the lock screen" '! dump | grep -q "content-desc=\"Emergency information\""'
shot emergency_off
sh_ settings put secure etesian_extra_emergency 1; sleep 1.5; relock
shot emergency_lock
check "on: block on the lock screen" 'dump | grep -q "content-desc=\"Emergency information\""'
check "owner and default essentials (name, blood, allergies)" 'dump | grep -q "Anna Virtanen" && dump | grep -q "Blood A+ · Penicillin" && dump | grep -q "text=\"Emergency info ›\""'
check "no Emergency call button on the lock screen" '! dump | grep -q "text=\"Emergency call\""'
set -- $(bounds 'content-desc="Emergency information"'); bl=$1; bt=$2; br=$3; bb=$4
cl=$(clock_left $SHOTS/emergency_off.png $bt $bb)
check "left of the clock, no overlap (block right $br < clock left $cl)" '[ -n "$br" ] && [ $br -lt $cl ]'
check "on the left edge, vertically on the clock rows" '[ $bl -lt 120 ] && [ $bt -lt 400 ]'
sh_ settings put system font_scale 1.6; sleep 1; relock
shot emergency_bigfont
set -- $(bounds 'content-desc="Emergency information"'); br2=$3; bt2=$2; bb2=$4
sh_ settings put secure etesian_extra_emergency 0; sleep 1; relock; shot emergency_bigfont_off
cl2=$(clock_left $SHOTS/emergency_bigfont_off.png ${bt2:-150} ${bb2:-300})
check "larger font: text grows, still no overlap ($br2 < $cl2)" '[ -n "$br2" ] && [ $br2 -lt $cl2 ] && [ $(( bb2 - bt2 )) -gt $(( bb - bt )) ]'
sh_ settings put system font_scale $fs; sh_ settings put secure etesian_extra_emergency 1; sleep 1; relock
set -- $(bounds 'content-desc="Emergency information"'); sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 2.5
shot emergency_card
check "tap opens the emergency card" 'top_activity | grep -q EmergencyCardActivity && dump | grep -q "text=\"Emergency information\""'
check "still locked under the card" 'sh_ dumpsys activity activities | grep -q "isKeyguardShowing=true"'
check "card: owner and facts" 'dump | grep -q "text=\"Anna Virtanen\"" && dump | grep -q "text=\"Penicillin\"" && dump | grep -q "text=\"Asthma, inhaler in the bag\""'
check "card: contacts with call buttons" 'dump | grep -q "content-desc=\"Call Mikko Laine\"" && dump | grep -q "content-desc=\"Call Sara Koski\""'
check "card: the Emergency call button and Close" 'dump | grep -q "text=\"Emergency call\"" && dump | grep -q "text=\"Close\""'
set -- $(bounds 'content-desc="Call icon"'); i1=$1; i2=$2; i3=$3; i4=$4; D=$(sh_ wm density | tail -1 | grep -o '[0-9]*$')
check "card: call icon at the mock's size (27dp, $(( i3 - i1 ))px)" '[ -n "$i3" ] && [ $(( i3 - i1 )) -ge $(( 26 * D / 160 )) ]'
check "card: call icon is a white outline, not a filled grey glyph" '$PY -c "
from PIL import Image; im=Image.open(\"$SHOTS/emergency_card.png\").convert(\"L\").crop(($i1,$i2,$i3,$i4))
px=list(im.getdata()); lit=sum(p>200 for p in px)
import sys; sys.exit(0 if max(px)>230 and 0<lit<0.35*len(px) else 1)"'
check "card: no toolbar under the card (bottom edge stays dark)" '$PY -c "
from PIL import Image; im=Image.open(\"$SHOTS/emergency_card.png\").convert(\"L\"); w,h=im.size
px=[im.getpixel((x,y)) for x in range(0,w,8) for y in range(h-120,h,4)]
import sys; sys.exit(0 if sum(px)/len(px)<60 else 1)"'
tap_text Close; sleep 2
check "Close returns to the lock screen" '! top_activity | grep -q EmergencyCardActivity && dump | grep -q "content-desc=\"Emergency information\""'
for k in $keys; do [ "${eold[$k]}" = null ] && sh_ settings delete secure $k >/dev/null || sh_ settings put secure $k "'${eold[$k]}'"; done
sh_ settings put system font_scale $fs
check "launcher and SystemUI did not crash" '! crashed $LAUNCHER && ! crashed com.android.systemui'
unlock; home

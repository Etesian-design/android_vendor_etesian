# 2026-11-19 (user): "a standby screen like the Nokia N9 ... the screen won't turn off completely when locked but show
# the clock instead along with any notifications icons and every minute just change position to avoid screen burn
# in", customisable like Billboard (their N9's setup: right-aligned full date, IP, battery, a battery bar, the song or
# "No media"). In Etesian mode the doze screen is the launcher's StandbyDream; plain Android keeps SystemUI's.
unlock
L=com.bompotis.etesian.launcher
set -- $(A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' '); SW=$1; SH=$2
sh_ settings put secure etesian_gestures 1
on0=$(sh_ settings get secure etesian_standby_on); aod0=$(sh_ settings get secure doze_always_on); cfg0=$(sh_ settings get secure etesian_standby)
# The user's Billboard lines (the settings page's "Billboard" preset).
sh_ "settings put secure etesian_standby '{\"color\":-1,\"size\":15,\"align\":\"RIGHT\",\"clock\":true,\"battery\":true,\"icons\":false,\"lines\":[{\"empty\":\"\",\"parts\":[{\"i\":\"date_full\"}]},{\"empty\":\"\",\"parts\":[{\"i\":\"ip\"}]},{\"empty\":\"\",\"parts\":[{\"t\":\"Battery: \"},{\"i\":\"battery\"},{\"t\":\"%\"}]},{\"empty\":\"\",\"parts\":[{\"i\":\"battery_bar\"}]},{\"empty\":\"No media\",\"parts\":[{\"i\":\"song\"}]}]}'"
sh_ settings put secure etesian_standby_on 1
sh_ settings put secure doze_always_on 1
check "the doze screen is Etesian's standby" 'sh_ dumpsys dreams | grep -q "getDozeComponent()=ComponentInfo{$L/$L.standby.StandbyDream}"'

# lit <png>: "<lit ‰ of the screen> <left> <top> <right> <bottom>" of the lit pixels (thin text: well under 1%).
lit() { python3 -c "
from PIL import Image
im=Image.open('$1').convert('L'); w,h=im.size; px=im.load(); xs=[]; ys=[]; n=0
for y in range(0,h,3):
  for x in range(0,w,3):
    if px[x,y]>60: n+=1; xs.append(x); ys.append(y)
t=(w//3)*(h//3)
print(n*1000//t, min(xs,default=0), min(ys,default=0), max(xs,default=0), max(ys,default=0))"; }

# Lock just after a minute starts, so the next move comes in under a minute.
while [ $(( $(date +%s) % 60 )) -gt 5 ]; do sleep 1; done
command adb logcat -c
sh_ input keyevent SLEEP; sleep 6
check "locked, the display dozes instead of going dark" 'sh_ dumpsys power | grep -q "mWakefulness=Dozing" && sh_ dumpsys display | grep -q "mState=DOZE"'
check "the standby screen started" 'command adb logcat -d -s EtesianStandby:I | grep -q "standby on"'
A exec-out screencap -p > "$SHOTS/standby_1.png"
# Named values: inside check, $1.. are check's own arguments.
read p1 l1 t1 r1 b1 <<<"$(lit "$SHOTS/standby_1.png")"; a="$l1 $t1 $r1 $b1"
check "the screen is black but for the block ($p1‰ lit)" '[ "${p1:-0}" -ge 3 ] && [ "$p1" -le 80 ]'
check "the block is tall enough for the clock and five lines ($a)" '[ $(( b1 - t1 )) -ge $(( SH / 6 )) ]'
sleep $(( 66 - $(date +%s) % 60 ))
A exec-out screencap -p > "$SHOTS/standby_2.png"
read p2 l2 t2 r2 b2 <<<"$(lit "$SHOTS/standby_2.png")"; b="$l2 $t2 $r2 $b2"
check "a minute later the block has moved ($a -> $b)" '[ -n "$b2" ] && [ "$a" != "$b" ]'
check "and it is still inside the screen" '[ "$l2" -ge 0 ] && [ "$r2" -le $SW ] && [ "$b2" -le $SH ]'
# 2026-11-22 (user): after a while the clock stopped at an old time. In deep Doze (the phone still, long enough) only
# alarms allowed while idle go off: the launcher is on the power-save allowlist, so its minute still comes.
check "the launcher is on the power-save allowlist" 'sh_ dumpsys deviceidle whitelist | grep -q "$L"'
sh_ dumpsys deviceidle force-idle >/dev/null
check "the phone is in deep Doze" 'sh_ dumpsys deviceidle get deep | grep -q IDLE'
sleep $(( 66 - $(date +%s) % 60 ))
A exec-out screencap -p > "$SHOTS/standby_3.png"
read p3 l3 t3 r3 b3 <<<"$(lit "$SHOTS/standby_3.png")"; c="$l3 $t3 $r3 $b3"
check "in deep Doze the block still moves every minute ($b -> $c)" '[ -n "$b3" ] && [ "$b" != "$c" ]'
sh_ dumpsys deviceidle unforce >/dev/null
# No turning with the phone: the rotation sensor is off while asleep, so no rotation suggestion either.
check "the rotation sensor is off on the standby screen" '! sh_ dumpsys window displays | grep -q "mOrientationListener.*mEnabled=true\|OrientationListener.*enabled=true"'
check "the standby screen did not crash" '! crashed $L'
sh_ input keyevent WAKEUP; sleep 1; unlock

# Plain Android: SystemUI's always-on display again.
sh_ settings put secure etesian_gestures 0
check "outside Etesian mode the doze screen is SystemUI's" 'sh_ dumpsys dreams | grep -q "getDozeComponent()=ComponentInfo{com.android.systemui/com.android.systemui.doze.DozeService}"'
sh_ settings put secure etesian_gestures 1

# Put the user's settings back.
[ "$on0" = null ] && sh_ settings delete secure etesian_standby_on >/dev/null || sh_ settings put secure etesian_standby_on "$on0"
[ "$aod0" = null ] && sh_ settings put secure doze_always_on 0 || sh_ settings put secure doze_always_on "$aod0"
[ "$cfg0" = null ] && sh_ settings delete secure etesian_standby >/dev/null || sh_ "settings put secure etesian_standby '$cfg0'"
home

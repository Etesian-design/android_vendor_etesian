# In the Events view a new notification does not pop up as a bubble: it slides down into its
# section from under the section's label and separator (never drawing over them). On the other
# home views and in apps the bubble stays. Silent: the test channel has no sound.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
PY=/home/memphisx/android/.venv-tools/bin/python
bubble_shown() { sh_ dumpsys window windows | grep -q "EtesianBubble"; }
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind ${1:-message} >/dev/null; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; sleep 1; }
apps_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 1; }
old=$(sh_ settings get secure etesian_extra_bubbles)
peeks_on
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
sh_ settings put secure etesian_extra_bubbles 1
post cancel; unlock
events_pane
# Slow the animations 10x so the phone's own frame grabs catch the slide; put back below.
oscale=$(sh_ settings get global animator_duration_scale); sh_ settings put global animator_duration_scale 10
check "Events view: flag set for SystemUI" '[ "$(sh_ settings get secure etesian_events_showing)" = 1 ]'
# Frames on the phone itself while the notification arrives (adb pulls are too slow).
sh_ rm -rf /sdcard/slide; sh_ mkdir -p /sdcard/slide
sh_ "for i in 0 1 2 3 4 5 6 7 8 9 10 11; do screencap -p /sdcard/slide/\$i.png; done" &
sleep 0.3; post; wait
[ "$oscale" = null ] && sh_ settings delete global animator_duration_scale >/dev/null || sh_ settings put global animator_duration_scale $oscale
sleep 2
check "Events view: no bubble" '! bubble_shown'
shot slide_final
check "the notification is in its section" 'dump | grep -q "Are we still on for lunch?"'
# The Notifications header and the row under it.
hb=$(dump | grep -o 'text="Notifications"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
rm -rf "$SHOTS/slide"; adb pull /sdcard/slide "$SHOTS/slide" >/dev/null 2>&1
res=$($PY -c "
import glob
from PIL import Image, ImageChops
hb='$hb'.split(); ht,hbot=int(hb[1]),int(hb[3])
fin=Image.open('$SHOTS/slide_final.png').convert('L')
row=(0,hbot+4,1080,hbot+180); head=(0,ht,1080,hbot)
def d(a,b,box): return sum(1 for v in ImageChops.difference(a.crop(box),b.crop(box)).tobytes() if v>40)
fr=[Image.open(f).convert('L') for f in sorted(glob.glob('$SHOTS/slide/*.png'),key=lambda f:int(f.split('/')[-1][:-4]))]
mid=sum(1 for f in fr if 2000<d(f,fin,row)<d(fr[0],fin,row)-2000)
# Once the header is in place (rows below move down first), the sliding row never draws over it.
idx=next((i for i,f in enumerate(fr) if d(f,fin,head)<400),len(fr))
over=max([d(f,fin,head) for f in fr[idx:]] or [99999])
print(mid,over)")
mid=${res% *}; over=${res#* }
check "the row was seen part way in ($mid frames)" '[ "${mid:-0}" -ge 1 ]'
check "the header was never drawn over (max $over changed px)" '[ "${over:-99999}" -lt 400 ]'
post cancel; sleep 0.5
apps_pane
check "Applications view: flag cleared" '[ "$(sh_ settings get secure etesian_events_showing)" = 0 ]'
# Android holds back pop-ups that come in a quick burst: let the last one settle first.
sleep 3; post; sleep 2
check "Applications view: bubble as before" 'bubble_shown'
sh_ input swipe 540 230 540 20 200; sleep 1; post cancel
[ "$old" = null ] && sh_ settings delete secure etesian_extra_bubbles >/dev/null || sh_ settings put secure etesian_extra_bubbles "$old"
sh_ rm -rf /sdcard/slide
check "launcher did not crash" '! crashed $LAUNCHER'
check "systemui did not crash" '! crashed com.android.systemui'
home
peeks_off

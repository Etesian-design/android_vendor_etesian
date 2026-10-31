# Bug 2026-10-27 (user): dismissing the status menu slid it DOWN (the system close animation; on
# targetSdk 37 Back never reached onBackPressed, so the menu's own slide-up close() never ran).
# N9: it leaves the way it came, up. Also Back in Toolbar settings returned to the list.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "no onBackPressed left (never called on targetSdk 37)" '! grep -q "override fun onBackPressed" $L/*.kt'
unlock; home
sh_ input tap 540 30; sleep 1.5
sh_ "screenrecord --size 360x720 --time-limit 2 /sdcard/dm.mp4" & sleep 0.5; sh_ input keyevent BACK; wait
adb pull /sdcard/dm.mp4 /tmp/claude-1000/dm.mp4 >/dev/null 2>&1
# The magenta Volume bar is the menu's marker: its height on screen per frame (until it is gone).
read down gone <<<"$(/home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio, numpy as np
ys=[]
for x in iio.imiter('/tmp/claude-1000/dm.mp4'):
  m=(x[:,:,0]>150)&(x[:,:,2]>150)&(x[:,:,1]<60); r=np.flatnonzero(m.sum(axis=1)>20)
  ys.append(int(r.mean()) if len(r) else -1)
seen=[y for y in ys if y>=0]
print(sum(1 for a,b in zip(seen,seen[1:]) if b>a+2), int(ys[-1]==-1))")"
check "Back: the menu never moves down ($down frames down)" '[ "$down" = 0 ]'
check "and it is gone" '[ "$gone" = 1 ]'
# The dim lifts the same way (user 2026-10-27: it slid down). Over a white page, the lower edge
# of the dimmed area may only move up, frame by frame. (Incognito: nothing lands in the browser history.)
sh_ "am start -n org.lineageos.jelly/.MainActivity --ez extra_incognito true -a android.intent.action.VIEW -d 'data:text/html,<body style=background:white></body>'" >/dev/null; sleep 3
sh_ input tap 540 30; sleep 1.5
sh_ "screenrecord --size 360x720 --time-limit 2 /sdcard/dm2.mp4" & sleep 0.5; sh_ input keyevent BACK; wait
adb pull /sdcard/dm2.mp4 /tmp/claude-1000/dm2.mp4 >/dev/null 2>&1
read downs moved <<<"$(/home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio, numpy as np
e=[]
for x in iio.imiter('/tmp/claude-1000/dm2.mp4'):
  c=x[:int(x.shape[0]*.9),150:210].mean(axis=(1,2)); d=np.flatnonzero(c<150); e.append(int(d.max()) if len(d) else 0)
print(sum(1 for a,b in zip(e,e[1:]) if b>a+3), int(e[0]-e[-1]>200))")"
check "the dim lifts upwards ($downs frames its edge moved down)" '[ "$downs" = 0 ] && [ "$moved" = 1 ]'
sh_ am force-stop org.lineageos.jelly
check "home in front again" '! top_activity | grep -q StatusMenuActivity'
# Toolbar settings: Back from a set returns to the list, not out of the screen.
sh_ am start -n $LAUNCHER/.ToolbarSettingsActivity >/dev/null; sleep 2
tap_text "All apps"; sleep 1.5; sh_ input keyevent BACK; sleep 1.5
check "Toolbar settings: Back from a set returns to the list" 'top_activity | grep -q ToolbarSettingsActivity && sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1 && sh_ cat /sdcard/u.xml | grep -q "text=\"Toolbar buttons\""'
sh_ input keyevent BACK; sleep 1
check "launcher did not crash" '! crashed $LAUNCHER'
home

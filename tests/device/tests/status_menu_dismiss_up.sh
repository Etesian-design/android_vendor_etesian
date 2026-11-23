# Bug 2026-10-27 (user): dismissing the status menu slid it DOWN (the system close animation; on
# targetSdk 37 Back never reached onBackPressed, so the menu's own slide-up close() never ran).
# N9: it leaves the way it came, up. Also Back in Toolbar settings returned to the list.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "no onBackPressed left (never called on targetSdk 37)" '! grep -q "override fun onBackPressed" $L/*.kt'
unlock; home
# Wait for the menu to be up (a freshly started launcher takes a while on a slower phone), then a moment more.
sh_ input tap 540 30; for _ in $(seq 20); do top_activity | grep -q StatusMenuActivity && break; sleep 0.3; done; sleep 1
sh_ "screenrecord --size 360x720 --time-limit 2 /sdcard/dm.mp4" & sleep 0.5; sh_ input keyevent BACK; wait
adb pull /sdcard/dm.mp4 /tmp/claude-1000/dm.mp4 >/dev/null 2>&1
# The blue Brightness fill is the menu's marker (the Volume bar is empty when the tests silence the phone): the
# rows holding one unbroken run of blue at least a quarter of the width (app icons behind are far smaller), per frame.
read down gone <<<"$(/home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio, numpy as np
ys=[]
for x in iio.imiter('/tmp/claude-1000/dm.mp4'):
  m=(x[:,:,2]>170)&(x[:,:,0]<90)&(x[:,:,1]>90)&(x[:,:,1]<190); w=m.shape[1]
  d=np.diff(np.pad(m.astype(np.int8),((0,0),(1,1))),axis=1); rows=[]
  for i in range(m.shape[0]):
    st=np.flatnonzero(d[i]==1); en=np.flatnonzero(d[i]==-1)
    if len(st) and (en-st).max()>=w//4: rows.append(i)
  ys.append(max(rows) if rows else -1)  # the lowest blue band: Brightness (the selected Silent segment is blue too)
# Only the menu's own bar: from its first frame until it has left the screen.
s=next((i for i,y in enumerate(ys) if y>=0), len(ys)); e=next((i for i in range(s,len(ys)) if ys[i]<0), len(ys))
seen=ys[s:e]
print(sum(1 for a,b in zip(seen,seen[1:]) if b>a+2), int(e<len(ys) and s<len(ys)))")"
check "Back: the menu never moves down ($down frames down)" '[ "$down" = 0 ]'
check "and it is gone" '[ "$gone" = 1 ]'
# The dim lifts the same way (user 2026-10-27: it slid down). Over a white page, the lower edge
# of the dimmed area may only move up, frame by frame. (Incognito: nothing lands in the browser history.)
sh_ "am start -n org.lineageos.jelly/.MainActivity --ez extra_incognito true -a android.intent.action.VIEW -d 'data:text/html,<body style=background:white></body>'" >/dev/null; sleep 3
# Wait for the menu to be up (a freshly started launcher takes a while on a slower phone), then a moment more.
sh_ input tap 540 30; for _ in $(seq 20); do top_activity | grep -q StatusMenuActivity && break; sleep 0.3; done; sleep 1
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
sh_ am start -n $LAUNCHER/.ToolbarSettingsActivity --activity-clear-task >/dev/null; sleep 2
tap_text "All apps"; sleep 1.5; sh_ input keyevent BACK; sleep 1.5
check "Toolbar settings: Back from a set returns to the list" 'top_activity | grep -q ToolbarSettingsActivity && sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1 && sh_ cat /sdcard/u.xml | grep -q "text=\"Toolbar buttons\""'
sh_ input keyevent BACK; sleep 1
check "launcher did not crash" '! crashed $LAUNCHER'
home

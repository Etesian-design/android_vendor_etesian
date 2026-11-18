# Bug 2026-11-04 (user): swipe up from the bottom had a setting (minimise, back, close, nothing) and
# needed a hold to show the apps. N9, the only behaviour: the first row of applications comes up at
# the bottom and the application drops onto it and stays, until a tap on an icon opens it or a tap
# on the application goes back to it.
unlock
# 2026-11-18 (user): the row comes up only when the finger holds still for half a second on the way up; a plain
# swipe up sends the application away, as from the sides.
peek_up() { sh_ "input motionevent DOWN 540 2155; for y in \$(seq 2155 -50 1500); do input motionevent MOVE 540 \$y; done; sleep 0.8; input motionevent UP 540 1500"; sleep 1.5; }
swipe_up() { sh_ "input motionevent DOWN 540 2155; for y in \$(seq 2155 -80 700); do input motionevent MOVE 540 \$y; done; input motionevent UP 540 700"; sleep 2; }
peek_early() { sh_ "input motionevent DOWN 540 2155; for y in \$(seq 2155 -50 1500); do input motionevent MOVE 540 \$y; done; sleep 0.25"; }
peek_shown() { sh_ dumpsys window windows | grep -q 'Window{.* EtesianPeek}'; }
check "no setting for the bottom swipe" '! grep -rq "BOTTOM_ACTION\|Swipe up from the bottom" ~/android/lineage/vendor/etesian/launcher/app/src/main/java'
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 3
swipe_up
check "a plain swipe up: no row, the app goes to Open applications" '! peek_shown && [ "$(home_pane)" = switcher ]'
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 3
peek_early
check "a quarter second held: no row yet" '! peek_shown'
sleep 0.6
check "half a second held: the row comes up" 'peek_shown'
sh_ "input motionevent UP 540 1500"; sleep 1.5
sh_ input tap 540 700; sleep 2
peek_up
check "the row of applications comes up" 'peek_shown'
sleep 3
check "and stays after the finger lifts" 'peek_shown'
sh_ input tap 540 700; sleep 2
check "a tap on the application goes back to it ($(top_activity))" 'top_activity | grep -q com.android.settings && ! peek_shown'
peek_up
# The first icon of the row (columns from the launcher's grid, default 4).
sh_ input tap 135 2100; sleep 3
check "a tap on an icon opens that application ($(top_activity))" '! top_activity | grep -q com.android.settings && ! peek_shown'
# 2026-11-18 (user): held, the application drops by itself to just above the row and falls into shadow behind it,
# so the row is in front. A light application (Messaging) shows the shadow.
mean_top() { python3 -c "
from PIL import Image, ImageStat; im=Image.open('$1').convert('L'); w,h=im.size
print(int(ImageStat.Stat(im.crop((0,h//5,w,h//2))).mean[0]))"; }
sh_ am start -a android.intent.action.SENDTO -d sms:5550100 >/dev/null; sleep 3
command adb exec-out screencap -p > "$SHOTS/peek_before.png"
peek_early; sleep 1.2
command adb exec-out screencap -p > "$SHOTS/peek_shadow.png"
B=$(mean_top "$SHOTS/peek_before.png"); A=$(mean_top "$SHOTS/peek_shadow.png")
check "held: the application falls into shadow behind the row ($B -> $A)" 'peek_shown && [ $A -lt $((B * 7 / 10)) ]'
sh_ "input motionevent UP 540 1500"; sleep 1.5
sh_ input tap 540 700; sleep 2
# With the on-screen keyboard up, a held bottom swipe brings no row (it only minimises).
F=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'resource-id="com.android.messaging:id/compose_message_text"[^>]*bounds="[^"]*"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
FY=$(echo "$F" | python3 -c "import sys,re;n=list(map(int,re.findall(r'\d+',sys.stdin.read())));print((n[1]+n[3])//2)")
sh_ input tap 540 $FY; sleep 1.5  # from the UI dump: mapped coordinates
check "keyboard up for the next check" 'sh_ dumpsys input_method | grep -q "mInputShown=true" && [ "$(sh_ settings get secure etesian_ime_shown)" = 1 ]'
peek_early; sleep 1.2
check "keyboard up: a held bottom swipe brings no row" '! peek_shown'
sh_ "input motionevent UP 540 1500"; sleep 2
command adb shell input keyevent BACK; sleep 1
check "keyboard down: the flag clears" '[ "$(sh_ settings get secure etesian_ime_shown)" = 0 ]'
# 2026-11-18 (user): closing from the top, the application darkens the further it is pulled down.
sh_ am start -a android.intent.action.SENDTO -d sms:5550100 >/dev/null; sleep 3
sh_ "input motionevent DOWN 540 4; for y in \$(seq 4 60 1300); do input motionevent MOVE 540 \$y; done; sleep 0.5"
command adb exec-out screencap -p > "$SHOTS/close_dark.png"
sh_ "for y in \$(seq 1300 -80 4); do input motionevent MOVE 540 \$y; done; input motionevent UP 540 4"; sleep 1.5
D=$(python3 -c "
from PIL import Image, ImageStat; im=Image.open('$SHOTS/close_dark.png').convert('L'); w,h=im.size
print(int(ImageStat.Stat(im.crop((0,int(h*0.7),w,int(h*0.85)))).mean[0]))")
L=$(python3 -c "
from PIL import Image, ImageStat; im=Image.open('$SHOTS/peek_before.png').convert('L'); w,h=im.size
print(int(ImageStat.Stat(im.crop((0,int(h*0.2),w,int(h*0.35)))).mean[0]))")
check "pulled down from the top, the application is darker ($L -> $D)" '[ $D -lt $((L * 6 / 10)) ]'
check "launcher did not crash" '! crashed $LAUNCHER'
home

# Shared helpers for the on-device regression tests (run.sh). Silent: no alarms, sounds or calls.
PASS=0; FAIL=0
LAUNCHER=com.bompotis.etesian.launcher
ok()   { PASS=$((PASS+1)); echo "  ok   $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }
sh_() { adb shell "$@" | tr -d '\r'; }
home() { sh_ input keyevent HOME >/dev/null; sleep 1.5; }
unlock() { sh_ input keyevent WAKEUP; sleep 1; sh_ wm dismiss-keyguard >/dev/null 2>&1; sleep 1; }
# The resumed activity, e.g. com.bompotis.etesian.launcher/.HomeActivity
top_activity() { sh_ dumpsys activity activities | grep -m1 topResumedActivity | grep -o '[a-z][a-zA-Z0-9_.]*/[a-zA-Z0-9_.$]*'; }
# Activity type (home, standard...) of the task holding activity $1
task_type() { sh_ dumpsys activity activities | awk -v a="$1" '/\* Task\{/{t=$0} index($0,a) && /ActivityRecord/ {print t; exit}' | grep -o 'type=[a-z]*' | head -1 | cut -d= -f2; }
# Taps the on-screen element with text $1, scrolling down to find it; returns 1 if not found.
tap_text() {
    local b
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
        b=$(sh_ cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1)
        [ -n "$b" ] && break
        sh_ input swipe 540 1700 540 900 300; sleep 0.8
    done
    [ -n "$b" ] || return 1
    set -- $(echo "$b" | grep -o '[0-9]\+')
    sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
crashed() { sh_ logcat -d -b crash | grep -q "Process: $1"; }
lock() { sh_ input keyevent POWER; sleep 2.5; }
# Swipe the lock screen away across its empty middle (the cards sit lower down).
swipe_unlock() { sh_ input keyevent WAKEUP; sleep 1.5; sh_ input swipe 950 1000 100 1000 250; sleep 2.5; }
# Which home pane is showing, from the launcher's view tree ("events", "apps", "switcher"...).
home_pane() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'content-desc="pane:[a-z]*"' | head -1 | cut -d: -f2 | tr -d '"'; }
# Brightness (0-255) of screen pixel x,y from a fresh screenshot.
pixel() { adb exec-out screencap -p > /tmp/hd-px.png; python3 -c "from PIL import Image; p=Image.open('/tmp/hd-px.png').convert('L').getpixel(($1,$2)); print(p)"; }
# Brightness (0-255) of pixel (x, y) in N quick raw frames taken on the device; one per line.
burst() { # x y n
  sh_ "w=\$(wm size | grep -o '[0-9]*x' | tr -d x); for i in \$(seq $3); do screencap > /data/local/tmp/b\$i; done
       for i in \$(seq $3); do dd if=/data/local/tmp/b\$i bs=1 skip=\$((16 + ($2 * w + $1) * 4)) count=3 2>/dev/null | od -An -tu1; done; rm -f /data/local/tmp/b*" |
    awk '{print int(($1+$2+$3)/3)}'
}
# Adds the launcher widget named [label] to the Widgets pane through its own picker.
widgets_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = widgets ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; }
# The Applications view, by its pane tag (not an app name: the number of widget screens between varies).
apps_pane() { home; for _ in 1 2 3 4 5 6; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; }
add_widget() {
  widgets_pane
  for _ in 1 2 3 4 5 6; do sh_ input swipe 540 1800 540 400 200; done; sleep 1
  # Empty page: Add widget is on the page. With widgets: it is in the edit bar, opened by a long-press.
  sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  sh_ cat /sdcard/u.xml | grep -q 'text="Add widget"' || { sh_ input swipe 540 400 540 400 1000; sleep 1.5; }
  tap_text "Add widget"; sleep 2
  # The picker opens with every app expanded: only open Etesian if it shows ▼ (folded).
  sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  sh_ cat /sdcard/u.xml | grep -o 'text="Etesian"\|text="[▼▲]"' | grep -A1 Etesian | grep -q '▼' && { tap_text "Etesian"; sleep 1.5; }
  for _ in $(seq 20); do
    sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
    sh_ cat /sdcard/u.xml | grep -q "text=\"$1\"" && { tap_text "$1"; sleep 2.5
      sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q 'text="Done"' && { tap_text "Done"; sleep 1; }  # leave edit mode
      return 0; }
    sh_ input swipe 540 1800 540 900 400; sleep 1
  done
  return 1
}
# Where tests save screenshots for review.
SHOTS=${SHOTS:-/tmp/claude-1000/-home-memphisx-android-lineage-vendor-etesian-launcher/7e690201-7d1a-48b0-948b-8e4d79eda838/scratchpad/shots}; mkdir -p "$SHOTS"
shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }
# Etesian settings switch: pref_switch <section> <title> [1|0]. Prints the state it had (1/0);
# with a value, sets it by tapping. Used to turn on what a test needs and restore it afterwards.
pref_switch() {
  local was=""; for _ in 1 2 3; do
    sh_ am start -W --activity-clear-task -n com.bompotis.etesian.launcher/.SettingsActivity --es section "$1" >/dev/null 2>&1; sleep 1.5
    local row s; for s in 0 1 2; do  # scrolls down when the switch is below the fold
      row=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' |
        awk -v t="text=\"$2\"" 'index($0,t){f=1} f&&/switchWidget/{print; exit}')
      [ -n "$row" ] && break; sh_ input swipe 540 1700 540 600 300; sleep 0.8; done
    local now=0; echo "$row" | grep -q 'checked="true"' && now=1; [ -z "$was" ] && was=$now
    [ -z "$3" ] || [ "$3" = "$now" ] && break
    local b; b=$(echo "$row" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   '); set -- "$1" "$2" "$3" $b
    [ -n "$7" ] && sh_ input tap $(( ($4 + $6) / 2 )) $(( ($5 + $7) / 2 )); sleep 1
  done
  echo $was; sh_ input keyevent BACK >/dev/null 2>&1; sleep 0.5
}
# Widgets pane: scroll to the top, then down until the dump matches [pattern] (widgets fill gaps anywhere).
seek_widget() { for _ in 1 2 3 4 5 6 7 8; do sh_ input swipe 540 600 540 1800 150; done; sleep 1
  for _ in 1 2 3 4 5 6 7 8 9 10; do sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -q "$1" && return 0
    sh_ input swipe 540 1500 540 900 300; sleep 0.8; done; return 1; }
# Which way the screen content moved in a recording: slide_dir <mp4> prints the summed horizontal
# shift in px of 360-wide frames (negative: content moved left, i.e. a page came in from the right).
slide_dir() { /home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio, numpy as np, sys
prev=None; tot=0
for x in iio.imiter(sys.argv[1]):
  p=x[150:600].astype(float).mean(axis=(0,2))
  if prev is not None and np.abs(p-prev).mean()>0.05:
    best=min(range(-60,61),key=lambda k:np.abs(p[60+k:300+k]-prev[60:300]).mean())
    tot+=best
  prev=p
print(tot)" "$1"; }
# Records while [command] runs: record_slide <name> <command...>; prints slide_dir of the recording.
# The animations run 5x slower meanwhile (scales restored after), so the recording has enough frames.
record_slide() { local n=$1; shift
  local ts as; ts=$(sh_ settings get global transition_animation_scale); as=$(sh_ settings get global animator_duration_scale)
  sh_ settings put global transition_animation_scale 5; sh_ settings put global animator_duration_scale 5
  sh_ "screenrecord --size 360x720 --time-limit 6 /sdcard/$n.mp4" & sleep 0.6; "$@"; wait
  sh_ settings put global transition_animation_scale "${ts/null/1}"; sh_ settings put global animator_duration_scale "${as/null/1}"
  adb pull /sdcard/$n.mp4 /tmp/claude-1000/$n.mp4 >/dev/null 2>&1; slide_dir /tmp/claude-1000/$n.mp4; }
# Etesian settings list: pref_list <section> <title> [option label]. Prints the label it showed;
# with an option, picks it. Used to set what a test needs and restore it afterwards.
pref_list() {
  sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity --es section "$1" >/dev/null 2>&1; sleep 1.5
  tap_text "$2" >/dev/null; sleep 1
  local was; was=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | tr '>' '\n' |
    grep 'checked="true"' | grep -o 'text="[^"]*"' | head -1 | cut -d'"' -f2)
  if [ -n "$3" ]; then tap_text "$3" >/dev/null; else sh_ input keyevent BACK; fi; sleep 1
  echo "$was"; sh_ input keyevent BACK >/dev/null 2>&1; sleep 0.5
}
# Replaces the launcher's widget list with the file [path] on the phone (launcher stopped first).
widgets_put() { local P=/data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ am force-stop $LAUNCHER; sh_ cp $1 $P
  local u; u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u $P; sh_ chmod 660 $P; sh_ restorecon $P >/dev/null 2>&1; }
# Which way the page title moved in a recording (for pages with little else on them): prints the
# change in px of the bright pixels' mean x in the title band after the pages swap (negative: moved left).
title_dir() { /home/memphisx/android/.venv-tools/bin/python -c "
import imageio.v3 as iio, numpy as np, sys
xs=[]
for x in iio.imiter(sys.argv[1]):
  m=x[15:40].mean(axis=2)>150; c=np.flatnonzero(m.any(axis=0))
  if len(c): xs.append(float(c.mean()))
d=[b-a for a,b in zip(xs,xs[1:])]
# From the swap of pages (the largest jump) on, only the new page's title moves.
k=max(range(len(d)),key=lambda i:abs(d[i])) if d else 0
print(int(sum(d[k+1:])))" "$1"; }
# The user's Widgets view may be full: widgets_only <Class> keeps their widgets aside and shows just that widget (placed by
# TestNotifyReceiver); widgets_restore puts theirs back. Call widgets_restore before the test ends.
widgets_prefs() { sh_ am force-stop $LAUNCHER; sh_ cp $1 /data/data/$LAUNCHER/shared_prefs/widgets.xml
  local u; u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u /data/data/$LAUNCHER/shared_prefs/widgets.xml
  sh_ chmod 660 /data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ restorecon /data/data/$LAUNCHER/shared_prefs/widgets.xml >/dev/null 2>&1; }
widgets_only() {
  sh_ "[ -f /data/local/tmp/wbak.xml ] || cp /data/data/$LAUNCHER/shared_prefs/widgets.xml /data/local/tmp/wbak.xml"
  sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
  widgets_prefs /data/local/tmp/wempty.xml; widgets_pane
  sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls "$1" >/dev/null; sleep 3; }
widgets_restore() { widgets_prefs /data/local/tmp/wbak.xml; sh_ rm -f /data/local/tmp/wbak.xml; }
set_zen() { adb shell cmd notification set_dnd $(echo "$1" | sed 's/^1$/priority/;s/^2$/on/;s/^3$/alarms/;s/^0$/off/') >/dev/null; }
# Bubbles and heads-ups are pop-ups, which Do Not Disturb hides: tests of them let pop-ups through
# for their duration. The runner's silent ringer and the test channel's lack of sound keep it quiet.
peeks_on() { _zen_peek=$(sh_ settings get global zen_mode); set_zen 0; }
peeks_off() { set_zen "${_zen_peek:-2}"; }

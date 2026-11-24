# Etesian settings previews (2026-11-24, owner): on every page with a preview, the preview stays at the top while the
# options scroll under it, and every switch on the page redraws it when flipped (and it comes back when flipped
# back). Switches the preview has nothing to show for are listed in NOT_DRAWN with the reason.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
PY=/home/memphisx/android/.venv-tools/bin/python
page() { sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section $1 >/dev/null; sleep 2.5; }
pbounds() { dump | grep -o "content-desc=\"Preview: $1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
pdiff() { $PY -c "
from PIL import Image, ImageChops
l,t,r,b=map(int,'$3'.split()); a=Image.open('$SHOTS/$1.png').convert('L').crop((l,t,r,b)); c=Image.open('$SHOTS/$2.png').convert('L').crop((l,t,r,b))
print(sum(1 for v in ImageChops.difference(a,c).tobytes() if v > 40))"; }
# The titles of the switch rows on screen now, one per line.
switches() { dump | $PY -c "
import sys, re, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
def walk(n, row=None):
    if n.get('clickable') == 'true' and n.get('checkable') != 'true': row = n
    if n.get('checkable') == 'true' and n.get('enabled') == 'true' and row is not None:
        # Only switches clear of the preview above and the bottom bar below (a tap there would land on the bar).
        y = sum(list(map(int, re.findall(r'\d+', n.get('bounds'))))[1::2]) // 2
        t = [x.get('text') for x in row.iter() if x.get('text')]
        if t and int(sys.argv[1]) + 40 < y < 1900: print(t[0])
    for c in n: walk(c, row)
walk(root)" "$1"; }
# The on/off state of the switch in the row titled $1 (true/false), from the screen.
state() { dump | $PY -c "
import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read()); want = sys.argv[1]
def walk(n, row=None):
    if n.get('clickable') == 'true' and n.get('checkable') != 'true': row = n
    if n.get('checkable') == 'true' and row is not None:
        t = [x.get('text') for x in row.iter() if x.get('text')]
        if t and t[0] == want: print(n.get('checked')); sys.exit()
    for c in n: walk(c, row)
walk(root)" "$1"; }
# Taps the row of the switch titled $1, near its left end: not its title text (a category header can carry the same
# words), and not the switch at the right edge (Breeze's handle takes touches along that edge).
tap_switch() { set -- $(dump | $PY -c "
import sys, re, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read()); want = sys.argv[1]
def walk(n, row=None):
    if n.get('clickable') == 'true' and n.get('checkable') != 'true': row = n
    if n.get('checkable') == 'true' and row is not None:
        t = [x.get('text') for x in row.iter() if x.get('text')]
        if t and t[0] == want:
            l, tp, r, b = map(int, re.findall(r'\d+', n.get('bounds'))); rl = int(re.findall(r'\d+', row.get('bounds'))[0])
            print(rl + 120, (tp + b) // 2); sys.exit()
    for c in n: walk(c, row)
walk(root)" "$1"); [ -n "$2" ] && sh_ input tap $1 $2; }
# Waits up to 4 s for the switch titled $1 to read $2.
until_state() { local i; for i in 1 2 3 4 5 6 7 8; do [ "$(state "$1")" = "$2" ] && return 0; sleep 0.5; done; return 1; }
# Switches whose effect is not on the part of the phone the preview draws.
NOT_DRAWN='^$'
unlock; home
BEFORE=$(sh_ settings list secure | grep '^etesian_extra_' | cut -d= -f1)

for sec in status events open apps lock emergency sharing dismissed priority; do
  page $sec
  bb=$(pbounds $sec)
  check "$sec: preview shown" '[ -n "$bb" ]'
  [ -z "$bb" ] && { sh_ input keyevent BACK; sleep 1; continue; }
  # Sticky: scroll the options; the preview has not moved.
  sh_ input swipe 540 1900 540 1300 300; sleep 1
  check "$sec: preview stays at the top while the options scroll" '[ "$(pbounds $sec)" = "$bb" ]'
  sh_ input swipe 540 1300 540 1900 300; sleep 1
  seen=""
  for pass in 1 2 3 4 5 6; do
    while IFS= read -r t; do
      [ -z "$t" ] && continue
      case "$seen" in *"|$t|"*) continue;; esac
      seen="$seen|$t|"
      echo "$t" | grep -qE "$NOT_DRAWN" && continue
      n=$(echo "$t" | tr -c 'A-Za-z0-9' _)
      s0=$(state "$t")
      shot pv_${sec}_${n}_a
      want=$([ "$s0" = true ] && echo false || echo true)
      tap_switch "$t"; until_state "$t" "$want"; sleep 0.6; shot pv_${sec}_${n}_b
      s1=$(state "$t")
      # Put it back by its state, not by tapping twice (a tap that did nothing must not flip it the second time).
      [ "$(state "$t")" != "$s0" ] && { tap_switch "$t"; until_state "$t" "$s0"; sleep 0.6; }
      shot pv_${sec}_${n}_c
      check "$sec: \"$t\" switches ($s0 -> $s1)" '[ -n "$s0" ] && [ "$s0" != "$s1" ]'
      check "$sec: \"$t\" is back as it was" '[ "$(state "$t")" = "$s0" ]'
      check "$sec: \"$t\" redraws the preview" '[ "$(pdiff pv_${sec}_${n}_a pv_${sec}_${n}_b "$bb")" -ge 30 ]'
      check "$sec: \"$t\" back, the preview as before" '[ "$(pdiff pv_${sec}_${n}_a pv_${sec}_${n}_c "$bb")" -lt 30 ]'
    done <<< "$(switches "$(echo $bb | cut -d" " -f4)")"
    sh_ input swipe 540 1900 540 1400 400; sleep 2   # the list settles before its rows are read and tapped
  done
  # A run that found nothing to flip proves nothing.
  check "$sec: switches were tried ($(echo "$seen" | grep -o '|[^|]*|' | wc -l))" '[ "$(echo "$seen" | grep -o "|[^|]*|" | wc -l)" -ge 1 ]'
  sh_ input keyevent BACK; sleep 1
done
# Settings that were unset before stay unset (a switch flipped and back writes an explicit 0).
for k in $(sh_ settings list secure | grep '^etesian_extra_' | cut -d= -f1); do
  echo "$BEFORE" | grep -qx "$k" || sh_ settings delete secure "$k" >/dev/null
done
check "launcher did not crash" '! crashed $LAUNCHER'
home

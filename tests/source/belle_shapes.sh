#!/bin/bash
# Host check (no phone). Regression 2026-10-31: the Belle Classic clock, switches, toggle caps and data meter were rounded
# rectangles and the small analog clock had no second hand. They follow the 701's own art now (tools/belle_art.py): squircles
# stretched to their box, the warm charcoal dial, the #4791DC second hand.
cd "$(dirname "$0")/../../launcher" || exit 1
fail=0
bad() { echo "FAIL $1"; fail=1; }
d=app/src/main/res/drawable

# The drawables are what the generator writes (nobody edited them by hand).
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
cp -r app "$tmp/" 2>/dev/null; mkdir -p "$tmp/tools" && cp tools/belle_art.py "$tmp/tools/"
python3 "$tmp/tools/belle_art.py" >/dev/null
for f in bc_square_dial bc_date_box bc_hand_hour_l bc_hand_minute_l bc_hand_second bc_toggle_on bc_toggle_off bc_sw_on bc_sw_off bc_power_on bc_power_off; do
  cmp -s "$d/$f.xml" "$tmp/app/src/main/res/drawable/$f.xml" || bad "$f.xml differs from tools/belle_art.py"
  grep -q '<corners' "$d/$f.xml" && bad "$f.xml is a rounded rectangle"
done
grep -q '#FF41413C' $d/bc_square_dial.xml || bad "the dial is not the 701's warm charcoal"
grep -q '#FF4791DC' $d/bc_hand_second.xml || bad "the second hand is not #4791DC"
grep -q 'hand_second' app/src/main/res/layout/bc_analog.xml || bad "the small analog clock has no second hand"
grep -q 'drawCircle' app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt && grep -A12 'private fun ring' app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt | grep -q drawCircle && bad "the data meter is a disc"
grep -q 'android:startY="74" android:endX="0" android:endY="46"' $d/bc_toggle_on.xml || bad "the on cap's blue glow is not brightest at the bottom"
grep -A30 'private fun ring' app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt | grep -q 'squircle(9f \* u, 9f \* u, 62f \* u' || bad "the data meter's inner squircle is not 62 of 80"
grep -q 'strokeLineCap="round"' $d/bc_glyph_wifi.xml || bad "the Wi-Fi glyph's arcs are not round-ended strokes"
grep -q 'CAP_ON = 0xFF3C8FE6' app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt || bad "a lit cap glyph is not Belle blue"
# Regression: the dot was drawn as a near-zero arc, which Android renders as a circle off to the right of the arcs
python3 - $d/bc_glyph_wifi.xml <<'PY' || bad "the Wi-Fi glyph's dot is not centred under its arcs"
import re, sys
s = open(sys.argv[1]).read()
dot = re.search(r'M([\d.]+),([\d.]+)a([\d.]+),[\d.]+ 0,1 1,0 ([\d.]+)', s)
assert dot and float(dot.group(1)) == 12 and float(dot.group(4)) > 1, 'dot'
xs = [float(a) for a, _ in re.findall(r'[MA ]([\d.]+),([\d.]+)A', s)] + [float(x) for x in re.findall(r'1,([\d.]+),[\d.]+(?=M|")', s)]
assert abs((min(xs) + max(xs)) / 2 - 12) < 0.05, 'arcs'
PY

[ $fail = 0 ] && echo "PASS Belle shapes follow the 701 art"

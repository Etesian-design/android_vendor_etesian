# User 2026-11-22: "Sliders are still not the sliders we have in storybook ... AND ESPECIALLY ETESIAN SETTINGS". The
# Storybook slider (HdSlider, Controls / Form controls / Slider): a 4dp dark groove (#3A3A3A), blue up to the value,
# a 30dp top-lit white squircle knob. Not Android's round dot, not Material 3's thick pill track with a bar handle.
# Etesian settings use a SeekBar (AppCompat in AndroidX preferences); Android's Settings a Material Slider
# (SettingsLib SliderPreference). For each: find a slider, measure groove, fill and knob in a screenshot.
unlock
D=$(raw_sh wm density | grep -o '[0-9]*' | tail -1); dp() { echo $(( $1 * D / 160 )); }
# sliders: one line per slider on screen, in the phone's own pixels (like the screenshot): "<l> <t> <r> <b>"
sliders() { raw_sh uiautomator dump /sdcard/u.xml >/dev/null 2>&1; raw_sh cat /sdcard/u.xml | tr '>' '\n' \
  | grep -E 'class="android.widget.SeekBar"|resource-id="[a-z.]*:id/slider(_frame)?"' \
  | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'; }
# measure <png> <l> <t> <r> <b>: "<blue fill px tall> <grey groove px tall> <knob px wide> <knob px tall>"
measure() { python3 -c "
from PIL import Image
im=Image.open('$1').convert('RGB'); l,t,r,b=$2,$3,$4,$5
blue=lambda c: c[2]>170 and c[0]<110 and c[2]-c[0]>80
grey=lambda c: all(abs(v-58)<=16 for v in c)
white=lambda c: min(c)>215
def tall(x, f):
    best=n=0
    for y in range(t,b):
        n=n+1 if f(im.getpixel((x,y))) else 0; best=max(best,n)
    return best
cols=range(l,r)
bx=[x for x in cols if tall(x,blue)>0]; gx=[x for x in cols if tall(x,grey)>0]
fill=sorted(tall(x,blue) for x in bx)[len(bx)//2] if bx else 0
groove=sorted(tall(x,grey) for x in gx)[len(gx)//2] if gx else 0
# the knob: the widest run of white columns that are white for at least 12 px
wc=[x for x in cols if tall(x,white)>=12]
runs=[];
for x in wc:
    if runs and x==runs[-1][1]+1: runs[-1][1]=x
    else: runs.append([x,x])
a,z=max(runs,key=lambda q:q[1]-q[0]) if runs else (0,-1)
kt=max((tall(x,white) for x in range(a,z+1)), default=0)
print(fill, groove, z-a+1, kt)"; }
look() {  # look <name> <package>: the first slider on screen
  local name=$1 pkg=$2 s; s=$(sliders); A exec-out screencap -p > "$SHOTS/slider_$name.png"  # full size: the dump's bounds and wm density are the real pixels
  check "$name: has a slider to look at ($(echo "$s" | grep -c .))" '[ -n "$s" ]'
  [ -n "$s" ] || return
  set -- $(echo "$s" | head -1)
  set -- $(measure "$SHOTS/slider_$name.png" $1 $2 $3 $4); local f=$1 g=$2 kw=$3 kh=$4
  check "$name: a thin blue fill, 4dp ($f px; $(dp 3)-$(dp 6))" '[ $f -ge $(dp 3) ] && [ $f -le $(dp 6) ]'
  check "$name: a thin dark groove, 4dp ($g px; $(dp 3)-$(dp 6))" '[ $g -ge $(dp 3) ] && [ $g -le $(dp 6) ]'
  check "$name: a white squircle knob about 30dp wide ($kw px; $(dp 22)-$(dp 34))" '[ $kw -ge $(dp 22) ] && [ $kw -le $(dp 34) ]'
  check "$name: and as tall, not a bar handle ($kh px; $(dp 22)-$(dp 34))" '[ $kh -ge $(dp 22) ] && [ $kh -le $(dp 34) ]'
  check "$name did not crash" '! crashed $pkg'
}
# Etesian settings: Gestures, Single hand mode's Size, set half way so there is a fill and a groove to measure (the
# user's own size is put back at the end).
size0=$(sh_ settings get secure etesian_single_hand_size); sh_ settings put secure etesian_single_hand_size 70
sh_ am start -S -W -n $LAUNCHER/.SettingsActivity --es section gestures >/dev/null; sleep 2.5
tap_text "Single hand mode" >/dev/null 2>&1 || true
look etesian_settings $LAUNCHER
if [ "$size0" = null ]; then sh_ settings delete secure etesian_single_hand_size >/dev/null; else sh_ settings put secure etesian_single_hand_size $size0; fi
# Etesian standby settings (a plain SeekBar built in code).
sh_ am start -S -W -n $LAUNCHER/.standby.StandbySettingsActivity >/dev/null; sleep 2.5
for _ in 1 2 3 4; do [ -n "$(sliders)" ] && break; sh_ input swipe 540 1700 540 900 300; sleep 0.8; done
look standby_settings $LAUNCHER
# Android's Settings: Sound and vibration's volumes.
sh_ am force-stop com.android.settings; sh_ am start -W -a android.settings.SOUND_SETTINGS >/dev/null; sleep 2.5
look settings_sound com.android.settings
sh_ am force-stop com.android.settings
home

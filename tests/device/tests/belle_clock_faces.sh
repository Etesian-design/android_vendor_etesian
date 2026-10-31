# Bug 2026-10-29 #5: Belle clocks did not match the screenshots: AnalogClock only scales its dial
# down, so the squircle/gear faces stayed small inside a big empty widget with the day and date
# outside the face; the LCD clock was a big mostly empty dark box; the Belle clock and analog clock
# sat on dark panels; the 2x1 flip clock's date ran over the digits.
R=~/android/lineage/vendor/etesian/launcher/app/src/main/res
J=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "dial and hands share one large size (scaled down to fit)" 'grep -q "android:width=\"400dp\"" $R/drawable/bc_square_dial.xml && grep -q "400dp" $R/drawable/bc_hand_hour_l.xml && grep -q "400dp" $R/drawable/bc_hand_second.xml'
check "faces sized to the widget, day/date inside" 'grep -q "fun fitFace" $J/BelleClassic2.kt && grep -q "fitFace(awm, id, v, BelleClassic2.SQUIRCLE, day = false)" $J/BelleClassic.kt'
check "LCD frame hugs its digits" 'grep -q "android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:layout_gravity=\"center\"" $R/layout/bc_lcdclock.xml'
check "Belle clocks have no panel" '! grep -q belle_panel $R/layout/belle_clock.xml && ! grep -q belle_panel $R/layout/belle_analog.xml'
check "flip clock digits size to the tile" 'grep -q autoSizeTextType $R/layout/bc_flipclock.xml'
check "Storybook clocks match (transparent m-clock/m-analog, gear day/date)" '! grep -q "bw-panel bw-mclock" ~/Git/etesian-design/src/stories/belle/BelleWidget.vue && grep -q "bw-gear .bw-dayname" ~/Git/etesian-design/src/stories/belle/BelleWidget.vue'
# Device: place a large clock alone on an emptied Widgets view; its face must be most of the screen width.
unlock
wp=$(pref_switch home Widgets 1)
sh_ cp /data/data/$LAUNCHER/shared_prefs/widgets.xml /data/local/tmp/wbak.xml
prefs_put() { sh_ am force-stop $LAUNCHER; sh_ cp $1 /data/data/$LAUNCHER/shared_prefs/widgets.xml
  u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u /data/data/$LAUNCHER/shared_prefs/widgets.xml
  sh_ chmod 660 /data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ restorecon /data/data/$LAUNCHER/shared_prefs/widgets.xml >/dev/null 2>&1; }
sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
prefs_put /data/local/tmp/wempty.xml
widgets_pane
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls BelleBigClockWidget >/dev/null; sleep 3
adb exec-out screencap -p > /tmp/claude-1000/bcf.png
w=$(python3 - <<'PY'
from PIL import Image
im=Image.open('/tmp/claude-1000/bcf.png').convert('L'); W,H=im.size
best=0
for y in range(200,H-200,8):
    xs=[x for x in range(W) if im.getpixel((x,y))>20]
    if xs: best=max(best, xs[-1]-xs[0])
print(best)
PY
)
check "large clock face fills the widget ($w px of 1080)" '[ "$w" -gt 700 ]'
# Restore the user's widgets (the launcher must not be running while the prefs change).
prefs_put /data/local/tmp/wbak.xml
home
pref_switch home Widgets "$wp" >/dev/null; home

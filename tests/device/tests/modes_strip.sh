# Settings → Do Not Disturb and Modes as profiles (mocks 09, 10): a "Mode" strip under
# Profile (Off, Do Not Disturb, the enabled modes); the moon glyph while on; long-press a mode
# opens its page. Silent: DND only mutes. The previous zen mode is restored.
check "Modes.kt exists" '[ -f /home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/Modes.kt ]'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
strip_bounds() { dump | grep -o 'content-desc="Mode: [^"]*"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
old=$(sh_ settings get secure etesian_extra_modes); zen=$(sh_ settings get global zen_mode)
unlock; home
sh_ settings put secure etesian_extra_modes 0; menu
check "off: no Mode strip" '! dump | grep -q "content-desc=\"Mode: "'
sh_ input keyevent BACK; sleep 1
sh_ settings put secure etesian_extra_modes 1; menu
check "on: Mode strip" 'dump | grep -q "content-desc=\"Mode: Off\""'
shot modes_strip
set -- $(strip_bounds)
# Do Not Disturb is the second segment: tap about a third of the way along.
sh_ input tap $(( $1 + ($3-$1)*60/100 )) $(( ($2+$4)/2 )); sleep 1.5
check "tap Do Not Disturb turns it on" '[ "$(sh_ settings get global zen_mode)" != 0 ]'
check "strip shows it" 'dump | grep -q "content-desc=\"Mode: Do Not Disturb\""'
shot modes_dnd_on
check "status bar: the N9 moon in the zen slot" 'sh_ dumpsys activity service com.android.systemui/.SystemUIService StatusBarIconControllerImpl | grep -A2 "(zen)" | grep -q "visible=true"'
check "stale Do Not Disturb (Etesian) mode removed" '! sh_ dumpsys notification | grep -q "Do Not Disturb (Etesian)"'
set -- $(strip_bounds)
sh_ input swipe $(( $1 + ($3-$1)*60/100 )) $(( ($2+$4)/2 )) $(( $1 + ($3-$1)*60/100 )) $(( ($2+$4)/2 )) 900; sleep 2
check "long-press opens the mode page" 'top_activity | grep -q ModeSettingsActivity'
check "mode page rows" 'dump | grep -q "text=\"Schedule\"" && dump | grep -q "text=\"People\""'
check "mode page rows drill in with a chevron (mock 09b)" '[ $(dump | grep -o "content-desc=\"Open\"" | wc -l) -ge 3 ]'
shot modes_page
sh_ input keyevent BACK; sleep 1; menu
set -- $(strip_bounds); sh_ input tap $(( $1 + 20 )) $(( ($2+$4)/2 )); sleep 1.5
check "Off turns it off" '[ "$(sh_ settings get global zen_mode)" = 0 ]'
sh_ input keyevent BACK; sleep 1
[ "$zen" != 0 ] && sh_ cmd notification set_dnd on >/dev/null
[ "$old" = null ] && sh_ settings delete secure etesian_extra_modes >/dev/null || sh_ settings put secure etesian_extra_modes "$old"
check "launcher did not crash" '! crashed $LAUNCHER'
home

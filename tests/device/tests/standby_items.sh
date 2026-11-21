# User 2026-11-20: every standby (Billboard) item shows a value, and the profile item says the N9's words: Silent,
# Beep (Android's vibrate), Ringing. The launcher's test receiver logs each item's value and each configured line
# (EtesianStandbyTest), with a config holding every item. Then the settings page: + adds a line, the line's sheet
# removes it.
unlock
old=$(sh_ settings get secure etesian_standby); ring=$(sh_ settings get global mode_ringer | sed 's/0/SILENT/;s/1/VIBRATE/;s/2/NORMAL/')
ITEMS="time date_compact date_full song_artist song_title song network connection ip battery battery_bar battery_state profile alarm uptime ram load storage"
parts=$(for i in $ITEMS; do printf '{"empty":"","parts":[{"t":"%s: "},{"i":"%s"}]},' $i $i; done)
sh_ "settings put secure etesian_standby '{\"lines\":[${parts%,}]}'"
dump() { command adb logcat -c; sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind standby >/dev/null; sleep 2
  command adb logcat -d -s EtesianStandbyTest:I | sed -E 's/.*EtesianStandbyTest: //' | tr -d '\r'; }
out=$(dump); echo "$out" | sed 's/^/       /'
check "the receiver logged every item and line" 'echo "$out" | grep -q "^done$" && [ $(echo "$out" | grep -c "^item ") = 18 ] && [ $(echo "$out" | grep -c "^line ") = 18 ]'
# Song and alarm are empty with nothing playing / no alarm set; the network name with no SIM.
for i in $ITEMS; do
  case $i in song*|alarm|network) continue;; esac
  v=$(echo "$out" | grep "^item $i=" | cut -d= -f2-)
  check "item $i has a value (\"$v\")" '[ -n "$v" ]'
done
check "a line with a value reads as the words then the value (time)" 'echo "$out" | grep -q "^line 0=time: [0-9]"'
for m in SILENT:Silent VIBRATE:Beep NORMAL:Ringing; do
  sh_ cmd audio set-ringer-mode ${m%%:*} >/dev/null 2>&1; sleep 1
  p=$(dump | grep "^item profile=" | cut -d= -f2-)
  check "profile with the ringer ${m%%:*} says ${m#*:} ($p)" '[ "$p" = "${m#*:}" ]'
done
sh_ cmd audio set-ringer-mode ${ring:-SILENT} >/dev/null 2>&1

# The settings page: + adds a line (the item picker), the line's sheet removes it.
sh_ "settings put secure etesian_standby '{\"lines\":[{\"empty\":\"\",\"parts\":[{\"i\":\"time\"}]}]}'"
xmlw() { sh_ "uiautomator dump --windows /sdcard/u.xml" >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tapd() { set -- $(xmlw | tr '>' '\n' | grep "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | tr -cs '0-9' ' ')
  [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
lines() { xmlw | grep -o 'content-desc="Line [0-9]*"' | sort -u | wc -l; }
sh_ am start -S -W -n $LAUNCHER/.standby.StandbySettingsActivity >/dev/null; sleep 2.5; shot standby_settings
check "the standby settings open ($(top_activity))" 'top_activity | grep -q "standby.StandbySettingsActivity"'
n0=$(lines)
check "one line to start with ($n0)" '[ "$n0" = 1 ]'
tapd "Add a line"; sleep 1.2; tap_text "Battery %"; sleep 1.5; shot standby_added
n1=$(lines); cfg=$(sh_ settings get secure etesian_standby)
check "+ and an item add a line ($n1 lines; saved: $(echo "$cfg" | grep -o '"i":"battery"'))" '[ "$n1" = 2 ] && echo "$cfg" | grep -q "\"i\":\"battery\""'
tapd "Line 2"; sleep 1.2; tap_text "Remove the line"; sleep 1.5
n2=$(lines); cfg=$(sh_ settings get secure etesian_standby)
check "the line's sheet removes it ($n2 lines)" '[ "$n2" = 1 ] && ! echo "$cfg" | grep -q "\"i\":\"battery\""'
check "launcher did not crash" '! crashed $LAUNCHER'
if [ "$old" = null ]; then sh_ settings delete secure etesian_standby >/dev/null; else sh_ "settings put secure etesian_standby '$old'"; fi
home

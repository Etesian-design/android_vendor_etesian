# N9 volume-key tones (ngfd: profile_switch / volume_max / volume_min), checked silently (2026-10-25).
unlock
for t in profile_ringer_level volume_max volume_min; do
  check "tone $t installed" "sh_ ls /product/media/audio/ui/n9/$t.wav"
done
old=$(sh_ settings get secure etesian_volume_tones); ring=$(sh_ "cmd media_session volume --stream 2 --get" | grep -o 'volume is [0-9]*' | grep -o '[0-9]*')
sh_ settings put secure etesian_volume_tones 0     # decide and log, but play nothing
sh_ logcat -c
sh_ input keyevent VOLUME_DOWN; sleep 1; sh_ input keyevent VOLUME_UP; sleep 1
# With nothing playing the keys may drive media (Android default) rather than the ringer: then check the
# media end stop instead (music at max, nothing playing, tones off: silent).
if sh_ logcat -d -s N9VolumeTones | grep -q 'tone ringer\|tone silent'; then
  check "ringer step picks the ringer tone" "sh_ logcat -d -s N9VolumeTones | grep -q 'tone ringer.*(off)\|tone silent.*(off)'"
else
  music=$(sh_ "cmd media_session volume --stream 3 --get" | grep -o 'volume is [0-9]*' | grep -o '[0-9]*')
  max=$(sh_ "cmd media_session volume --stream 3 --get" | grep -o 'range \[[0-9]*\.\.[0-9]*' | grep -o '[0-9]*$')
  # Total silence locks the media volume; alarms-only lets it move and still keeps rings and
  # notifications quiet (nothing plays here, tones are off). The runner's mode is put back after.
  zen=$(sh_ settings get global zen_mode); set_zen 3
  sh_ "cmd media_session volume --stream 3 --set $max" >/dev/null; sleep 0.5; sh_ logcat -c
  sh_ input keyevent VOLUME_UP; sleep 1
  check "media at max: Up again picks the max tone" "sh_ logcat -d -s N9VolumeTones | grep -q 'tone max.*(off)'"
  sh_ "cmd media_session volume --stream 3 --set $music" >/dev/null; set_zen "$zen"
fi
check "nothing played while off" "! sh_ logcat -d -s N9VolumeTones | grep 'tone' | grep -vq '(off)'"
[ -n "$ring" ] && sh_ "cmd media_session volume --stream 2 --set $ring" >/dev/null
[ "$old" = null ] && sh_ settings delete secure etesian_volume_tones || sh_ settings put secure etesian_volume_tones "$old"
sh_ input keyevent BACK

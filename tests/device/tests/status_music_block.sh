# User 2026-10-27: the status menu's music widget could not be moved like the other blocks, and it
# showed with nothing playing. Now: hidden unless something plays (or is paused), except in edit
# mode, where it has a drag handle and moves like any block. The user's layout is put back.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
desc() { dump | grep -q "content-desc=\"$1\""; }
gy() { dump | grep -o "content-desc=\"Drag $1\"[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ' | awk '{print int($1+($3-$1)/2), int($2+($4-$2)/2)}'; }
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
sh_ cp $P /data/local/tmp/hl_music.bak
om=$(sh_ settings get secure etesian_extra_music); os=$(sh_ settings get secure etesian_extra_status_menu)
sh_ settings put secure etesian_extra_music 1; sh_ settings put secure etesian_extra_status_menu 1
unlock; home
sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5
if sh_ dumpsys media_session | grep -q "state=PLAYING\|state=PAUSED"; then echo "  (music is playing: skipping the hidden check)"
else check "nothing playing: no music widget" '! desc "Now playing"'; fi
b=$(dump | grep -o 'text="Bluetooth"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b
sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; sleep 1.2
check "edit mode: the music widget shows" 'desc "Now playing"'
check "edit mode: it has a drag handle" 'desc "Drag Music"'
set -- $(gy Music); mx=$1; my=$2; set -- $(gy Volume); vy=$2
sh_ input swipe $mx $my $mx $(( vy + 40 )) 1200; sleep 1.5
set -- $(gy Music); nmy=$2; set -- $(gy Volume); nvy=$2
check "drag moves Music below Volume" '[ -n "$nmy" ] && [ "$nmy" -gt "$nvy" ]'
check "the new order is saved" 'sh_ cat $P | grep "status_blocks" | grep -q "volume,[a-z:,]*music\|volume,music"'
sh_ input keyevent BACK; sleep 0.8; sh_ input keyevent BACK; sleep 1
check "launcher did not crash" '! crashed $LAUNCHER'
sh_ am force-stop $LAUNCHER; sh_ "cat /data/local/tmp/hl_music.bak > $P"
sh_ settings put secure etesian_extra_music $om; sh_ settings put secure etesian_extra_status_menu $os
home

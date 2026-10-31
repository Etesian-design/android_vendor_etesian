#!/bin/bash
# Status menu Profile strip: Ringing is the ringer-volume slider; the Volume bar is media volume. 2026-10-26
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/StatusMenuActivity.kt
check "status menu source exists" 'test -f "$SRC"'
check "Volume bar drives media volume" "grep -q 'getStreamVolume(AudioManager.STREAM_MUSIC)' \"\$SRC\""
check "Ringing fill follows the ringer level" "grep -q 'fun ringEnd' \"\$SRC\""
check "dragging Ringing sets the ringer volume" "grep -q 'setStreamVolume(AudioManager.STREAM_RING' \"\$SRC\""
check "Volume bar no longer disabled by the profile" "! grep -q 'bar.isEnabled = audio.ringerMode' \"\$SRC\""

# The startup sound is in part1, which plays once the phone has booted. Three things kept it silent:
# 1. stock bootanimation re-checked playSoundsAllowed() per part, which says no once booted;
# 2. it only made its player if system_server's "audio" service was up (SELinux denies the lookup);
# 3. audioserver rejects tracks from bootanim's uid (graphics, 1003): it has no package.
# Patches: frameworks_base (bootanimation), frameworks_av/0001-audioflinger-...
A=/home/memphisx/android/lineage/frameworks/base/cmds/bootanimation/audioplay.cpp
F=/home/memphisx/android/lineage/frameworks/av/services/audioflinger/AudioFlinger.cpp
P=/home/memphisx/android/lineage/vendor/etesian/patches
check "sound decided once at boot start" 'grep -q "soundsAllowed = playSoundsAllowed();" $A'
check "part playback uses that decision" 'grep -q "part.audioData && soundsAllowed)" $A'
check "player made early, bounded wait for audioserver" 'grep -q "i < 200 && defaultServiceManager()->checkService(" $A'
check "half-made player cleared before retrying, silent if still none" 'grep -A6 "if (!engineCreated) {" $A | grep -q "audioplay::destroy();" && grep -A6 "if (!engineCreated) {" $A | grep -q "return;"'
check "audioserver accepts the graphics uid (bootanimation)" 'grep -q "} else if (callingUid == AID_GRAPHICS) {" $F'
check "patches kept for repo syncs" 'grep -q "cmds/bootanimation/audioplay.cpp" $P/frameworks_base/*.patch && [ -s $P/frameworks_av/0001-audioflinger-bootanimation-startup-sound.patch ]'
# On the phone, when the log still reaches back to the boot animation: the clip played with a player.
L=$(sh_ logcat -d -b all | grep -E "audioplay|BootAnimationStartTiming|AudioFlinger.*createTrack")
if echo "$L" | grep -q BootAnimationStartTiming; then
    check "phone: last boot played the startup sound" 'echo "$L" | grep -q "playing clip for part1" && ! echo "$L" | grep -q -E "without a player|Realize failed|createTrack"'
fi

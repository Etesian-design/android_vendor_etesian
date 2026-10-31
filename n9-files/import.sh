#!/bin/bash
# "Bring your own N9 files": copies the N9's font and tones from a copy of an N9's
# filesystem into vendor/harmattan-n9, which the build then picks up. Nokia's files are
# never part of this repository; each user supplies them from their own N9.
#
#   import.sh /path/to/n9-root      (a directory holding the N9's usr/share/...)
set -e
SRC="${1:?usage: import.sh /path/to/n9-root}"
DEST="$(cd "$(dirname "$0")/../.." && pwd)/harmattan-n9"
# Keep an earlier boot animation in case this run can't make one (no ffmpeg).
KEEP=$(mktemp); [ -f "$DEST/bootanimation.zip" ] && cp "$DEST/bootanimation.zip" "$KEEP" || rm -f "$KEEP"
rm -rf "$DEST"; mkdir -p "$DEST/fonts" "$DEST/ringtones" "$DEST/notifications" "$DEST/alarms"

cp "$SRC/usr/share/fonts/nokia/Nokia Pure/Proportional/"NokiaPureText_{Rg,Lt,Bd}.ttf "$DEST/fonts/"

TONES="$SRC/usr/share/sounds/ring-tones"
for f in "$TONES"/*; do
    name=$(basename "$f")
    case "$name" in
        "No sound.wav") continue ;;
        Message*|Email*|Calendar*|"Chat alert"*) dir=notifications ;;
        Clock*) dir=alarms ;;
        *) dir=ringtones ;;
    esac
    # Make cannot copy files with spaces in their names.
    cp "$f" "$DEST/$dir/${name// /_}"
done

# The N9's startup sequence as an Android boot animation: a slowly pulsing NOKIA logo for
# as long as Android takes to boot, then (once boot has completed) the handshake animation
# with its sound, played to the end before home appears. Needs ffmpeg
# (set FFMPEG=/path/to/ffmpeg if it isn't on PATH).
FFMPEG="${FFMPEG:-ffmpeg}"
MPI="$SRC/usr/share/MProgressIndicator/themes"
if [ -f "$MPI/opengl/MainAnimation_LowNoise.mp4" ] && command -v "$FFMPEG" >/dev/null; then
    W=$(mktemp -d)
    mkdir -p "$W/part0" "$W/part1"
    # The N9 stores it rotated for its landscape-native panel; turn it upright and centre it.
    VF="transpose=clock,scale=1080:-2,pad=1080:2160:(ow-iw)/2:(oh-ih)/2:black"
    # One 2.5 s pulse (brightness 30%..100%), looped until boot completes.
    PULSE="format=rgb24,geq=r='r(X,Y)*(0.65-0.35*cos(2*PI*T/2.5))':g='g(X,Y)*(0.65-0.35*cos(2*PI*T/2.5))':b='b(X,Y)*(0.65-0.35*cos(2*PI*T/2.5))'"
    "$FFMPEG" -v error -loop 1 -framerate 24 -t 2.5 -i "$MPI/custom/nokia-logo.jpg" \
        -vf "$VF,$PULSE" -q:v 3 "$W/part0/%05d.jpg"
    "$FFMPEG" -v error -i "$MPI/opengl/MainAnimation_LowNoise.mp4" -vf "$VF" -q:v 3 "$W/part1/%05d.jpg"
    "$FFMPEG" -v error -i "$MPI/opengl/MainAnimation_LowNoise.mp4" -vn -ac 2 -ar 48000 -c:a pcm_s16le "$W/part1/audio.wav"
    # p 0: loop until boot completes; c 1: then play once, to the end.
    printf "1080 2160 24\np 0 0 part0\nc 1 0 part1\n" > "$W/desc.txt"
    (cd "$W" && zip -0 -qr "$DEST/bootanimation.zip" desc.txt part0 part1)
    rm -rf "$W"
elif [ -f "$KEEP" ]; then
    cp "$KEEP" "$DEST/bootanimation.zip"
    echo "No ffmpeg: kept the N9 boot animation made earlier"
else
    echo "No N9 startup animation or no ffmpeg: keeping LineageOS's boot animation"
fi
rm -f "$KEEP"

# UI sounds: touch and keyboard feedback, camera, charger and battery. Android plays WAV
# directly, so they are copied as they are into /product/media/audio/ui/n9/.
mkdir -p "$DEST/ui"
FB="$SRC/usr/share/themes/base/meegotouch"
for p in static_press static_release popup close close_multiple; do
    cp "$FB/feedbacks/priority2_$p/audio.wav" "$DEST/ui/$p.wav"
done
cp "$FB/meego-im-uiserver/feedbacks/priority2_vkb_popup_press/audio.wav" "$DEST/ui/vkb_popup_press.wav"
for t in camera_shutter camera_af camera_video_record_start camera_video_record_stop \
         accessory_connected battery_low profile_ringer_level volume_max volume_min; do
    cp "$SRC/usr/share/sounds/ui-tones/snd_$t.wav" "$DEST/ui/$t.wav"
done

# Vibrations: the N9 keeps them as Immersion .ivt files; ivt2pattern.py turns them into the
# on/off timings this phone's vibrator plays. The ring vibration follows the Nokia tune, and
# notifications use the N9's message vibration.
VIBRA="$SRC/usr/share/sounds/vibra"
if [ -f "$VIBRA/Nokia tune.ivt" ]; then
    mkdir -p "$DEST/vibra" "$DEST/sounds-overlay-values"
    python3 -I "$(dirname "$0")/ivt2pattern.py" "$VIBRA/Nokia tune.ivt" > "$DEST/vibra/ring_vibration.txt"
    # Each N9 ringtone has its own vibration, named like the tone.
    mkdir -p "$DEST/vibra/tones"
    for f in "$DEST"/ringtones/*; do
        tone=$(basename "${f%.*}")
        ivt="$VIBRA/${tone//_/ }.ivt"
        [ -f "$ivt" ] && python3 -I "$(dirname "$0")/ivt2pattern.py" "$ivt" > "$DEST/vibra/tones/$tone.txt"
    done
    MSG=$(python3 -I "$(dirname "$0")/ivt2pattern.py" "$VIBRA/tct_message.ivt")
    { echo '<?xml version="1.0" encoding="utf-8"?>'
      echo '<resources>'
      echo '    <integer-array name="config_defaultNotificationVibePattern">'
      for t in ${MSG//,/ }; do echo "        <item>$t</item>"; done
      echo '    </integer-array>'
      echo '</resources>'; } > "$DEST/sounds-overlay-values/vibration.xml"
fi

# Status menu icons.
mkdir -p "$DEST/icons"
for i in icon-m-common-wlan icon-m-common-cellular icon-m-bluetooth icon-m-status-menu-flight-mode \
         icon-m-camera-torch-on icon-m-image-edit-rotate-right icon-m-status-menu-settings \
         icon-m-status-menu-profile-loud icon-m-status-menu-profile-beep icon-m-status-menu-profile-silent \
         icon-l-rss; do
    cp "$SRC/usr/share/themes/blanco/meegotouch/icons/$i.png" "$DEST/icons/" 2>/dev/null || true
done

# App icons for the "N9 icons" pack: the ones iconpack/mapping.txt names.
mkdir -p "$DEST/app-icons"
grep -v '^\s*\(#\|$\)' "$(dirname "$0")/iconpack/mapping.txt" | while read -r _ icon; do
    cp "$SRC/usr/share/themes/blanco/meegotouch/icons/$icon.png" "$DEST/app-icons/" 2>/dev/null || true
done
"$(dirname "$0")/make-iconpack.sh" "$DEST/app-icons"

cp "$(dirname "$0")/n9.mk" "$DEST/n9.mk"
# Add Nokia Pure Text to LineageOS's font list (the only one Android reads). Idempotent;
# run this again after a repo sync.
TREE="$(dirname "$DEST")/.."
LIST="$TREE/packages/overlays/Lineage/fonts/etc/fonts_customization.xml"
if ! grep -q 'name="nokia-pure-text"' "$LIST"; then
    { sed '/<\/fonts-modification>/d' "$LIST"
      sed -n '/<family/,/<\/family>/p' "$(dirname "$0")/fonts_customization_n9.xml"
      echo '</fonts-modification>'; } > "$LIST.new" && mv "$LIST.new" "$LIST"
fi
# etesian-text (the Etesian UI font, Fira Sans by default) becomes Nokia Pure Text.
sed -i '/name="etesian-text"/,/<\/family>/{s/FiraSans-Light/NokiaPureText_Lt/;s/FiraSans-Regular/NokiaPureText_Rg/;s/FiraSans-Medium/NokiaPureText_Bd/;s/FiraSans-Bold/NokiaPureText_Bd/}' "$LIST"
cp -r "$(dirname "$0")/overlay" "$DEST/overlay"
cp -r "$(dirname "$0")/sounds-overlay" "$DEST/sounds-overlay"
if [ -d "$DEST/sounds-overlay-values" ]; then
    mkdir -p "$DEST/sounds-overlay/res/values"
    mv "$DEST/sounds-overlay-values/vibration.xml" "$DEST/sounds-overlay/res/values/"
    rmdir "$DEST/sounds-overlay-values"
fi
cp "$(dirname "$0")/Android.bp.in" "$DEST/Android.bp"
echo "Imported N9 files into $DEST"

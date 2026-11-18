#!/bin/bash
# Builds the boot animations: bootanimation.zip (1080x2160, the Pro1-X) and bootanimation-1440x3120.zip (the OnePlus
# 7 Pro), each with the startup sound in part1 (it plays as the phone finishes booting).
#   pack.sh N9_ZIP [PYTHON]   N9_ZIP: the developer's own N9 bootanimation.zip (vendor/harmattan-n9/bootanimation.zip)
set -euo pipefail
N9=$(realpath "$1"); PY=${2:-python3}
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir "$T/n9"; (cd "$T/n9" && unzip -q "$N9")
"$PY" "$HERE/../sounds/startup.py" "$T/audio.wav"
for size in 1080x2160 1440x3120; do
  out="$T/$size"
  AXES=none OUT_SIZE=$size "$PY" "$HERE/make.py" "$T/n9" "$out" "$HERE/../fonts/FiraSans-Medium.ttf" > /dev/null
  sed "1s/.*/${size/x/ } 24/" "$T/n9/desc.txt" > "$out/desc.txt"
  cp "$T/audio.wav" "$out/part1/audio.wav"
  name=bootanimation.zip; [ $size = 1080x2160 ] || name=bootanimation-$size.zip
  rm -f "$HERE/$name"
  # Stored, not compressed: the boot animation maps its files straight out of the zip.
  (cd "$out" && zip -q -0 -r "$HERE/$name" desc.txt part0 part1)
  echo "$name: $(unzip -l "$HERE/$name" | tail -1)"
done

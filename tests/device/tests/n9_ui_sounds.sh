# Bug 2026-10-25 #17: the N9 UI sounds (never played here: no audible tests). Keyboard keys use
# the same click as buttons (priority2_static_press), edit mode has its popup "scratch" and
# closing an app its close sound, when the user's N9 files are installed.
V=$(cd "$(dirname "$0")/../.." 2>/dev/null; pwd); V=${V%/tests}
A=$V/n9-files/sounds-overlay/res/xml/audio_assets.xml
check "keyboard keys use the N9 button click" '! grep "FX_KEYPRESS" "$A" | grep -v INVALID | grep -vq "n9/static_press.wav"'
check "launcher plays popup.wav on entering edit mode" 'grep -q "\"popup.wav\"" "$V/launcher/app/src/main/java/com/bompotis/etesian/launcher/N9Sounds.kt"'
if sh_ ls /product/media/audio/ui/n9/popup.wav 2>/dev/null | grep -q popup; then
  for f in static_press popup close close_multiple; do
    check "$f.wav installed" 'sh_ ls /product/media/audio/ui/n9/$f.wav | grep -q $f'
  done
else echo "  skip device files (N9 files not installed)"; fi

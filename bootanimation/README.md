# Etesian boot animation

Same timing and motion as the N9 boot animation, with ETESIAN in place of the NOKIA wordmark.

- `make.py SRC OUT Archivo.ttf`: SRC is the unpacked N9 `bootanimation.zip` (a developer's own copy, imported
  from their N9 into the local `vendor/harmattan-n9` by `n9-files/import.sh`; never committed or shipped). Nothing from it is copied: every background is redrawn from fitted brightness
  curves (oval glow, round ripples, their top-lit edge), and the wordmark is set in Archivo
  (SIL OFL 1.1, `OFL.txt`; first draft) or Fira Sans at the N9 wordmark's per-frame size, brightness, glow and shadow.
- Font: Fira Sans Medium (the ROM's own UI font, `vendor/etesian/fonts`), chosen 2026-10-28 (`AXES=none make.py n9 out ../fonts/FiraSans-Medium.ttf`).
- `pack.sh N9_ZIP`: builds both results, each with the startup sound (`sounds/startup.py`) as `part1/audio.wav`, which
  Android plays as the phone finishes booting.
- `bootanimation.zip` (1080x2160, the Pro1-X) and `bootanimation-1440x3120.zip` (the OnePlus 7 Pro): picked by
  etesian.mk from `TARGET_SCREEN_WIDTH`. `OUT_SIZE=WxH make.py …` draws any size: scaled to the width, the background
  continued above and below.

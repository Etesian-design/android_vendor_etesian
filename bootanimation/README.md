# Etesian boot animation

Same timing and motion as the N9 boot animation, with ETESIAN in place of the NOKIA wordmark.

- `make.py SRC OUT Archivo.ttf`: SRC is the unpacked N9 `bootanimation.zip` (from the optional
  vendor/harmattan-n9). Nothing from it is copied: every background is redrawn from fitted brightness
  curves (oval glow, round ripples, their top-lit edge), and the wordmark is set in Archivo
  (SIL OFL 1.1, `OFL.txt`; first draft) or Fira Sans at the N9 wordmark's per-frame size, brightness, glow and shadow.
- Font: Fira Sans Medium (the ROM's own UI font, `vendor/etesian/fonts`), chosen 2026-10-28 (`AXES=none make.py n9 out ../fonts/FiraSans-Medium.ttf`).
- `bootanimation.zip`: the built result, used by etesian.mk. Silent for now; the startup sound comes with the sounds group.

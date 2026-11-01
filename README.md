# Etesian for LineageOS

An N9-inspired experience for the F(x)tec Pro1-X on LineageOS 24: the Etesian launcher (Events, Applications, Open
applications and an optional Widgets view), lock screen, status menu, edge gestures, Belle-style widgets, N9-style
apps and settings. Everything the N9 did not have is optional and starts switched off.

Etesian is an independent project, not affiliated with Nokia, and ships no Nokia files. The fonts are Fira Sans,
Ubuntu Sans and Archivo under their own licences (see `fonts/` and `bootanimation/`).

## Build

1. Sync LineageOS 24 for the Pro1-X (`breakfast pro1x`).
2. Check this repository out at `vendor/etesian`.
3. Include it from `vendor/extra/product.mk`:

   ```
   $(call inherit-product, vendor/etesian/etesian.mk)
   ```

4. Apply the changes to the LineageOS projects: `vendor/etesian/patches/apply.sh` (again after every `repo sync`).
5. Build: `brunch pro1x`, or `m bacon` after `breakfast pro1x`.

### Your own N9 files (optional)

If you own an N9, `n9-files/import.sh /path/to/n9-root` copies its font and tones from your phone's files into a
separate local folder, and the build picks them up. They are never part of this repository.

## Layout

| Folder | What it holds |
|---|---|
| `launcher/` | The Etesian launcher (home views, settings, status menu, widgets) |
| `patches/` | Changes to LineageOS projects: SystemUI, Settings and the apps |
| `overlay/`, `theme/`, `fonts/`, `sounds/` | Framework overlays, colours, free fonts and sounds |
| `bootanimation/` | The boot animation and its generator |
| `toolbar/`, `fastscroll/`, `feeds/`, `store/`, `mail/` | Shared parts and apps |
| `tests/` | Source checks and on-device tests (`tests/device/run.sh`) |
| `docs/` | Design notes |

## Licence

Apache License 2.0 (see LICENSE), the same as AOSP and LineageOS.

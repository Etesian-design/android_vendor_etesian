<p align="center">
  <img src=".github/readme/phone.png" width="760" alt="Etesian on a phone: the Events view, the Applications grid and Open applications">
</p>

<h1 align="center">Etesian</h1>

<p align="center">
  <b>A calm, gesture-first phone, for any phone running LineageOS.</b>
</p>

<p align="center">
  <a href="https://etesian-design.com/phone">Try it in your browser</a> ·
  <a href="https://etesian-design.com">Website</a> ·
  <a href="https://language.etesian-design.com">Design language</a> ·
  <a href="https://github.com/etesian-design/plasma">KDE Plasma</a>
</p>

<p align="center">
  <img alt="Licence: Apache 2.0" src="https://img.shields.io/badge/licence-Apache%202.0-4591FF">
  <img alt="LineageOS 24" src="https://img.shields.io/badge/LineageOS-24-167C80">
  <img alt="Reference device: F(x)tec Pro1-X" src="https://img.shields.io/badge/reference%20device-F(x)tec%20Pro1--X-555">
  <img alt="Status: in development" src="https://img.shields.io/badge/status-in%20development-EF5500">
</p>

---

This repository is the Etesian Android ROM: a LineageOS vendor tree that adds the Etesian launcher, look, sounds and apps
to a LineageOS build for any phone.

No home button, no back button, just swipes from the edge of the screen. Its influence is the Nokia N9's Swipe UI,
re-envisioned for today. Everything new, from messages and calls to calendar entries and news, in one calm
list. Apps with big, plain type and nothing on screen that isn't your content.

<p align="center">
  <img src=".github/readme/swipe.webp" width="320" alt="Swiping between Events, Applications and Open applications">
</p>

<p align="center"><sub>Screenshots and a screen recording straight from the reference phone, a test device with
mock contacts, messages and calendar entries.</sub></p>

The Swipe UI shipped on a single phone and then disappeared. **Etesian re-envisions it** on top of LineageOS (it is
not a copy of the N9), so it can run on any phone LineageOS supports. It's developed and tested on the F(x)tec Pro1-X; another phone on the same LineageOS version
needs only its own build.

## What you get

- **Three home views.** Events, Applications and your open apps, side by side. Swipe between them.
- **Edge swipes.** Swipe in from any side to go home. Swipe down from the top to close an app.
- **Events.** Notifications, today's calendar and your RSS feeds in one list, with the weather.
- **Lock screen and status menu.** As on the N9: the time, your notifications, and a swipe to unlock.
- **The apps.** Phone, Messaging, Mail, Calendar, Music, Gallery and Settings, restyled to match.
- **Widgets.** An optional fourth view with Belle-style clocks, mail, feeds and toggles.

The first time you switch it on, it feels like picking up an N9: three views, edge swipes, nothing else in the way.
Everything we've added since (widgets, extra gestures, newer conveniences) waits in Settings for you to turn it on, so
you choose how much of today you want on top.

## Status

Etesian is in development and there are **no downloads yet**. You can build it yourself, or
[try the interface in your browser](https://etesian-design.com/phone).

## Build

You need a LineageOS 24 build environment for your phone: follow your device's build guide on the
[LineageOS wiki](https://wiki.lineageos.org/devices/).

```sh
git clone https://github.com/etesian-design/android_vendor_etesian vendor/etesian
echo '$(call inherit-product, vendor/etesian/etesian.mk)' >> vendor/extra/product.mk
vendor/etesian/patches/apply.sh     # run again after every repo sync
brunch <your device>             # e.g. pro1x, the reference device
```

<details>
<summary>What's in this repository</summary>

| Folder | What it holds |
|---|---|
| `launcher/` | The home: its views, widgets, status menu and settings |
| `patches/` | Changes to LineageOS's own projects (SystemUI, Settings, the apps) |
| `overlay/`, `theme/`, `fonts/`, `sounds/`, `bootanimation/` | The look and sound |
| `mail/`, `feeds/`, `store/`, `toolbar/`, `fastscroll/` | Apps and shared parts |
| `tests/` | Tests that run on the phone (`tests/device/run.sh`) |
| `docs/` | Design notes |

If you own an N9, `n9-files/import.sh` (and `n9-files/make-iconpack.sh` for its app icons) can use its own font, tones and icons in your local build. They're copied from your phone to a
local folder, `vendor/harmattan-n9`, outside this repository and never committed. `etesian.mk` includes that folder only
if it exists, so it is optional, and published builds never contain it.
</details>

## Related projects

The [design language](https://github.com/etesian-design/design) everything here is built from, Etesian for
[KDE Plasma](https://github.com/etesian-design/plasma) and [the website](https://github.com/etesian-design/website).

## Licence

Apache 2.0, like AOSP and LineageOS. Fonts: Fira Sans, Ubuntu Sans and Archivo, under their own licences.

Etesian is an independent project. It isn't affiliated with Nokia and contains no Nokia files.

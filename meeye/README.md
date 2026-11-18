# Meeye icons

[Meeye](https://github.com/yeyebbc/meeye-releases) is a Harmattan-style icon pack by **YeyeBBC**. Its icons are
licensed CC BY-NC 4.0 (the release's LICENSE file says CC BY-NC-ND 4.0), and its CandyBar dashboard is Apache 2.0.

Etesian preinstalls it, unmodified and signed by its author, as the launcher's default icon pack, and credits it
in Settings → Appearance, with a link to their work. Its icons are shown exactly as drawn. Etesian is free and
non-commercial.

`fetch.sh` downloads the pinned release and checks its SHA-256; `etesian.mk` includes it only when it is present.

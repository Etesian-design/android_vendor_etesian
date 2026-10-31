# N9 (Etesian "blanco") look for Android apps

Reference graphics (Nokia's, for looking at only — never copy them into any repo):
`/home/memphisx/android/n9-files/extracted/usr/share/themes/blanco/meegotouch/images/`
(`callui/`, `contacts/`, `messaging-ui/`, `clock/`, `splash/`, `theme/basement/`) and icons in
`.../meegotouch/icons/`. Splash screens in `splash/` are pictures of each app's main view
(stored rotated 90°).

## Overall
Source: morrick.me/archives/8742 ("Nokia N9 / MeeGo Etesian") screenshots + blanco theme graphics.

### Dark vs light (corrects earlier versions of this doc)
- Etesian is NOT uniformly light. Settings, camera settings, status menu, Music, Clock, home
  views (Events/Applications/Open apps) and in-call are **pure black `#000000`** with white text.
  The article calls the N9 a dark system ("dark mode, whoa!" in 2011; AMOLED).
- Light apps exist: Mail/Messaging/Contacts/Calendar-type content apps use a light grey body
  (`#DDE1E6`-ish blue-grey list background, `#E0E0E0` header panel, white reading view) with a
  **solid coloured title bar** (Mail: blue `#1A6FC9`→`#0E6ACB`, white light-weight title).
- Rule for our ROM: system/utility apps (Settings, Camera, Clock, Music, Dialer-in-call,
  launcher) = black. Content/communication apps (Mail, Messages, Contacts, Calendar, Notes) =
  light with coloured title bar. Either way pin the palette; no Material You dynamic colour.

### Colours
| Role | Dark apps | Light apps |
|---|---|---|
| Background | `#000000` | `#E3E6EA` lists / `#FFFFFF` reading |
| Primary text | `#FFFFFF` | `#191919` / `#2B2B2B` |
| Secondary text (subtitle) | `#A0A0A0`–`#8C8C8C` | `#5A5A5A`; dates `#6E6E6E` |
| Separator / group-header line | `#4A4A4A`, 1dp | `#C8C8C8`, 1dp |
| Icon tile (unselected) | `#2E2E2E`–`#333` squircle | `#D0D0D0` |
| Selection / active / switch on / slider fill | blue `#2A86D8` (switch `#1E7FD6`, slider `#1B85E0`) | same |
| Links / meta (light apps) | `#1A73C8` | |
| Primary positive (Save, Call) | orange `#F28C00` pill in dialogs/editors; green `#62AE2E` for call | |
| Destructive | red `#D42D2A` | |
Status menu volume bar is magenta `#CC00CC`; profile segments blue `#4A90F0` (selected),
dark blue `#1E3A5F` (below), `#3A3A3A` (above). (`#00A5E5` cyan is the status-bar
notification glyph, not the general accent; prefer `#2A86D8` for selections.)

### Typography (Nokia Pure Text; regular/bold/light only)
- Page title (dark apps): ~28sp **light (300)**, white, left-aligned 16dp, directly on black,
  followed by a full-width 1dp grey line (Settings) or nothing (camera settings).
- Coloured title bar (light apps): ~26sp light white text, bar ~64dp tall.
- List row title: **18–20sp bold**. Subtitle: 16sp regular grey. Section/toggle rows without
  subtitle (e.g. "Flight mode", "Brightness") are also bold 18–20sp.
- Group header label: 13–14sp **bold**, grey `#8C8C8C`.
- Grid caption under icons: 14–15sp regular, light grey `#C8C8C8`.
- Mail list: sender 18sp bold, subject 16sp regular dark, preview 16sp grey, date 13sp grey.

## Components
- **Headers**: see Typography. No elevation, no shadow, no Up arrow in the header — back
  navigation lives in the bottom toolbar.
- **List rows (icon tile + title + subtitle)**: 80dp tall, 16dp side padding. Left icon tile
  ~60dp squircle (radius ≈ 28%), fill `#2E2E2E` (or accent blue `#1E7FD6` when the row is
  an active/configured setting, see camera "White balance"), white monochrome glyph ~32dp.
  Text starts 80dp from left edge (16dp gap). Title bold white, subtitle grey under it. No
  separators between rows inside a group. Navigational rows end with a white `>` chevron
  (~20dp, bold) at the right edge; switch rows end with a switch.
- **Group headers**: label at left ("Scene", "Self-timer"), 13sp bold grey, then a 1dp
  `#4A4A4A` line filling the remaining width, vertically centred on the label, 16dp gap
  after the text. ~40dp tall. Settings also uses plain full-width 1dp lines between groups.
- **Icon choice grid** (camera "Scene"): 3 columns, ~60dp squircle tiles, selected tile blue
  `#2A86D8`, others `#333`; caption under each, 14sp grey-white.
- **Segmented choice list** (single choice, e.g. Self-timer): centred rounded box ~66% width
  (320dp of 480), corner radius ~10dp, rows ~50dp, 1dp `#3A3A3A` dividers, each row has a
  subtle vertical gradient `#3A3A3A`→`#222222`, text 18sp bold white centred. Selected row
  fill `#2A86D8` flat blue, bold white. No radio circles.
- **Bottom toolbar**: ~70dp, full width, separated from content by a 1dp dark line; dark
  apps: background `#0A0A0A`→`#000` gradient; light apps: light grey metallic gradient
  `#F0F0F0`→`#C8C8C8`. Back button at far left: ~50dp squircle tile (`#3A3A3A` dark / white
  with soft shadow light) with a bold left arrow `←`. Other actions are evenly spaced
  monochrome glyphs (no labels); overflow menu `≡` at far right.
- **Pill buttons** (dialogs/editors): full rounded (radius = height/2), ~48dp tall. Cancel:
  grey `#5A5A5A`→`#3C3C3C` gradient, white bold text. Save/positive: orange `#F7A21B`→`#E07B00`
  gradient, white bold. Side by side, equal width, 16dp gap.
- **Switch**: rounded-rect track ~64×40dp, radius ~10dp. On = blue `#1E7FD6` with white
  squircle thumb at right; off = dark grey `#3A3A3A` track, thumb left. Optional small `i`
  info tile (dark grey squircle) before it.
- **Slider**: thin 6dp rounded track `#3A3A3A`, blue `#1B85E0` fill to the left, large
  white squircle thumb ~40dp with soft shadow. Min/max glyphs at both ends (brightness suns).
- **Status menu** (pulled from status bar, black overlay on top of app grid): "Profile" and
  "Volume" labels 17sp regular white; flat rectangular bars ~24dp tall, square corners,
  profile segmented Silent/Beep/Ringing (labels bold 13sp inside), volume magenta fill.
  Then icon-tile rows (Wi-Fi/Bluetooth/Availability/Transfers) with bold title + grey status.
- **Dialogs/sheets**: black full-width panel (or dimmed overlay), light-weight 26sp title,
  content, then the two pill buttons at the bottom. Query dialogs on light apps keep the dark
  dialog style.
- Shape language: squircles everywhere (icons, avatars, back button, tiles, switch thumb,
  slider thumb); subtle shadows only, no Material elevation.

## Rules for changing a LineageOS app
- Only edit files inside the one app directory you were given. It is a git repo; leave the
  changes uncommitted (they are exported as a patch afterwards).
- Prefer resource changes (themes/styles/colors/drawables/dimens/layouts). Small, targeted
  Java/Kotlin changes are fine where theming can't reach (e.g. code that forces dynamic colour).
- Recreate N9 shapes as XML drawables (shape/layer-list/gradient). Do not add Nokia images,
  sounds or fonts to the repo.
- Do not run any build (`m`, `mm`, gradle, soong) — builds are serialized by the lead. Keep
  the code compiling by inspection: check every resource/attr you reference exists, keep
  qualifiers (night/land/sw*) consistent, update all overriding variants (values-night,
  values-v31 etc.) so the light N9 look applies in dark mode too.
- Finish with a short report: files changed, what each change does, anything uncertain that the
  lead should verify at build time.

## Gaps in our implementation
(launcher at `vendor/etesian/launcher`; colours in `Util.kt` `object N9`)
1. Accent is `#00A5E5` (cyan) everywhere; N9 selection/switch/slider blue is `#2A86D8`/`#1E7FD6`.
2. `N9.CARD` `#1C1C1C` rounded cards (Events cardBackground radius 14, Switcher prompt
   radius 6, Switcher thumbnails radius 14/18): N9 home views use flat rows on black, not cards.
3. Events: section titles are 15sp bold grey plain text; should be group headers with a
   thin `#4A4A4A` line to the right of the label. "Low priority (n) ▲/▼" uses text arrows.
4. Events rows: title 16sp bold / body 14sp / time 12sp; N9 rows are 18–20sp bold title and
   16sp subtitle. Icon tiles are drawn `#3A3A3A`; N9 uses `#2E2E2E`, ~60dp.
5. Events header: day/temp 30sp light is fine; no full-width 1dp rule under it.
6. Applications: search field is a rounded `#262626` pill (radius 22) — N9 search is a
   rectangular-ish field; icon captions 11–12.5sp are smaller than N9's ~14sp grey `#C8C8C8`;
   folder badge colour `#3A3A3A`. Pull hint 14sp OK.
7. Switcher: "Close all" is an outlined pill (1dp grey stroke) — N9 has no outlined buttons;
   use filled grey pill. Empty-state 20sp light grey is right.
8. StatusMenuActivity: background `0xF2101010` near-black + `0x99000000` scrim (N9 is opaque
   `#000` over the dimmed grid); icon tiles 48dp radius 10 `#3A3A3A` (N9: ~60dp squircle
   `#2E2E2E`); title 17sp bold/sub 13sp (N9: ~20sp/16sp); an on/off dot at row end does not
   exist in N9 (state is the subtitle text only, icon dimmed when off is acceptable).
9. Status menu Settings button is an outlined pill with icon; N9 has none there (Settings
   is an app). If kept, make it a filled grey pill.
10. Brightness bar in status menu uses `#00A5E5` FillBar flat style; N9 Settings brightness
    is a thin blue track with big white squircle thumb (the flat style only fits Profile/Volume).
11. Section labels in status menu 15sp; N9 "Profile"/"Volume" ~17sp regular.
12. Dividers `0x33FFFFFF` (~#333) are close; N9 uses `#4A4A4A` thin lines, only between groups.
13. No bottom toolbar/back button pattern anywhere (Settings activity of launcher should use
    the black toolbar + squircle back button).
14. `res/values/colors.xml`/`themes.xml` still hold template purple/teal colours; palette is
    hardcoded in Kotlin instead of resources.

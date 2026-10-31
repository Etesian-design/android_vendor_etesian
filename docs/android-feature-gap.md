# Android 16 feature gap: what Etesian hides, and how the N9 would do it

Date: 2026-10-25. This is a read-only survey of `patches/*`, the frameworks_base patch (SystemUI, WM, IMMS), the launcher (`launcher/app/src/main/java/com/bompotis/etesian/launcher/`) and the design docs (`~/Git/etesian-design/docs/01-principles.md`, `11-patterns.md`, `20-phone.md`).

Status key:
- **Avail**: works as in stock.
- **Hidden**: still in the build, but only reachable through Settings, a long-press or an intent.
- **Removed**: our patch disables it or takes over its entry point.
- **Never adapted**: stock UI still appears and looks like Material, not N9.

Unverified rows are marked "(?)" and should be checked on the phone.

## What our changes take over

- **Top edge.** Swiping down from the top closes the app (`EdgeGestures.kt`, `GestureService.kt`). Tapping the status bar opens `StatusMenuActivity`, which has the profile and ringer volume, Internet, Mobile data, Bluetooth, Flight mode, Flashlight, Auto-rotate, brightness and a Settings shortcut. **Result: the stock notification shade and Quick Settings can no longer be reached.** That takes everything that lives only there with it.
- **Back and edges.** `EdgeBackGestureHandler` gives every edge to the launcher. Back is a toolbar button or a configurable gesture (`Prefs.backGesture`). Stock predictive back, the assistant corner swipe and one-handed mode lose their gestures.
- **Navigation bar.** `EtesianBottomBar` replaces it with the N9 toolbar (Back plus the app's items), or the side strip. IMMS removes the strip under the keyboard.
- **Lock screen.** These are removed:
  - quick affordances (`KeyguardQuickAffordanceViewBinder`);
  - smartspace, now a date line only (`SmartspaceSection`);
  - swipe-to-dismiss on rows: a swipe opens the notification instead (`NotificationSwipeHelper`).

  The notification list is now `EtesianLockList`/`EtesianLockCard` ("no app header, no buttons"). The bouncer is restyled (`EtesianBouncer`).
- **Volume dialog.** A full-width N9 bar at the top with N9 tones. The stock expand-to-panel (per-stream sliders, live caption, spatial audio) is not restyled (?).
- **Recents.** Replaced by Open apps (`SwitcherPage`), which has Split view, Pop-up, Screenshot and App info for the centred card, plus edit mode. App pairs, "select text/image from recents" and the overview share chip are gone.
- **Notifications on home.** Events (`EventsService`/`EventsPage`) lists notifications, lets you dismiss them (long-press for dismiss mode) and files them under sections. Inline reply, actions, snooze, conversation priority and bubbles are not shown.

## Gap table

| # | Android 16 feature | Status | The N9 way (design rule) | Pri | Effort |
|---|---|---|---|---|---|
| 1 | Notification actions and **inline reply** (RemoteInput) | Removed (shade gone; Events and lock cards have no buttons) | The N9 had no buttons on Events items ("content is the interface"). Long-press an Events row: a bottom sheet (`n9-toolbar-menu.png` style) lists the notification's actions. For "Reply", a one-line text field rises with the keyboard, like Messages' quick reply. Do not put buttons on the rows. | High | M |
| 2 | **Heads-up** banners and their actions | Avail (?) but stock look (Never adapted) | N9 NotificationBanner: drops from the top over the status bar. Tap opens it, swipe up dismisses it, and there are no buttons (`11-patterns` Feedback). Reply goes through #1 by long-press. | High | M |
| 3 | **Quick Settings tiles** (Do Not Disturb, hotspot, cast, NFC, location, battery saver, dark theme, screen record, QR scanner, third-party `TileService`) | Removed (QS cannot be reached) | Status menu, N9 style: keep the few big rows (`20-phone` §1). Add one collapsible "More" grid of Belle 1×1 lit squircle switches (`belle-ref4/7`). Host third-party `TileService`s there. Long-press a switch opens its settings page, as Wi-Fi does now. Choose the switches in edit mode (long-press), not with a pencil button. | High | M–L |
| 4 | **Do Not Disturb, modes, bedtime** | Hidden (Settings only) | N9 **profiles**: the status menu already has a profile strip. Map Silent, Beep and Ringing onto ringer modes, and add Do Not Disturb and the user's Modes as more profile entries. Show the active mode as a status bar glyph. | High | S–M |
| 5 | **Media controls** (MediaSession, output switcher) | Lock screen: stock carousel (?); Status menu: none | The N9 lock screen showed the music: album art, title and ⏮ ⏯ ⏭ in the lock screen card style (`11-patterns` Lock screen). Add a music row to the status menu with an output picker (speaker or Bluetooth) as a sheet. Events gets a "Now playing" item. One source: `MediaSessionManager`. | High | M |
| 6 | **Notification history**, snooze | Hidden (history in Settings); snooze Removed | Snooze is an item in the Events long-press sheet (#1). History lives under the Events toolbar ≡ menu as "Earlier", a sub page with a plain header. | Med | S |
| 7 | **Conversations, priority, bubbles** | Removed (no shade); bubbles (?) | The N9 had none of these. Priority conversations pin to the top of the Events Notifications section with the contact squircle. Bubbles stay off: floating chat heads break "calm". Pop-up in Open apps already covers this. | Low | S |
| 8 | **Predictive back** | Removed | It conflicts with the edge = minimise rule. Keep it off, and make the toolbar Back trigger `OnBackInvokedCallback` so apps' in-app back animations still run. | Med | S |
| 9 | **Assistant** (corner swipe, long-press power) | Removed (gesture) | Long-press on the power key (Pro1-X) or the keyboard Fn key. No on-screen gesture: "swipe up and hold" is taken by the app-grid peek. | Low | S |
| 10 | **One-handed mode** | Removed | Not needed: N9 actions are already at the bottom (principle 8). Keep it removed. | Low | – |
| 11 | **Split screen, pop-up** | Avail (Open apps action row) | Done. Optionally: dragging one card onto another in edit mode makes a split pair. | Low | M |
| 12 | **App pairs, recents select-text, share from recents** | Removed | Add "Share" to the Open apps action row, sending the screenshot. Skip app pairs. | Low | S |
| 13 | **Screenshot overlay**: preview, edit (Markup), long screenshot | Never adapted (?) | N9: no overlay. Shutter sound plus an InfoBanner "Screenshot saved" with a thumbnail squircle; tap opens Gallery (Glimpse) on it, where editing lives in its toolbar. Long screenshot goes in Glimpse as "Extend". | Med | M |
| 14 | **Screen recorder** | Removed (QS tile only) | A status menu switch (#3). While recording, a red status bar dot; tap it to stop. | Low | S |
| 15 | **Clipboard overlay** (copy chip, edit, share) | Never adapted | An InfoBanner "Copied" without buttons. Clipboard history goes in LatinIME's suggestion strip (Lineage keyboard clipboard), styled N9. | Low | S |
| 16 | **Sharesheet** (direct share, `ChooserActivity`) | Never adapted | N9 Share UI is a sheet from the bottom: a contacts row (squircles), then the app list, plain bold rows. Restyle `IntentResolver` in a patch. Every app's Share leads here, so it is used a lot. | High | M |
| 17 | **Power menu** (emergency, lockdown, restart, wallet, device controls) | Never adapted (?) | N9 power-key menu: a dark list overlay with Power off, Restart, Lockdown (Lock now) and Emergency call. Emergency call is also on the lock/security screen. Wallet and device controls are dropped from the menu; they stay apps. | Med | M |
| 18 | **App shortcuts on long-press** (`ShortcutManager`) and pinned shortcuts | Removed (long-press = edit mode) | The N9 had none. In edit mode, tapping an icon could show a sheet with its shortcuts, "App info" and "Uninstall". Pinned shortcuts (`requestPinShortcut`) go into the grid as normal icons. Avoids clutter outside edit mode. | Med | M |
| 19 | **Widget picker** | Avail (WidgetPicker, Belle) | Done. | – | – |
| 20 | **Lock screen shortcuts** (camera, flashlight, wallet) | Removed | N9 had none. Double-press power for the camera (Lineage gesture) and keep the flashlight in the status menu. Keep it removed. | Low | – |
| 21 | **Smartspace** "At a glance" (next event, weather) | Removed (date only) | The N9 lock screen showed the next alarm and calendar event as small type under the date. Add a single line, with no cards. | Med | S |
| 22 | **Volume panel**: per-stream sliders, live caption, spatial audio, output | Never adapted (?) | N9 volume = one bar for the current stream (done). Per-stream volumes go in the status menu "Volume" row (tap to expand media/alarm/call). Output goes in the media row (#5). Live caption is a switch under accessibility in the status menu "More" (#3). | Med | S–M |
| 23 | **Privacy indicators** (camera/mic/location dot) and privacy chip | Avail (?) in status bar | Keep the green dot, but as a small squircle glyph in the N9 status bar. Tap the status bar while it is lit: the status menu shows the top row "Camera in use by X" with a stop link. | High | S |
| 24 | **Permission dialogs** (PermissionController), photo picker | Never adapted | N9 QueryDialog (`11-patterns` Confirmation): title = question, plain rows "Allow" / "Only this time" / "Don't allow". Theme it with an RRO or a patch on PermissionController and the photo picker (MediaProvider). | Med | M |
| 25 | **Private space**, work profile, multi-user | Hidden or (?) | N9 had none. In Applications, show private apps behind a locked folder at the end of the grid (the Folders panel plus the security screen). Switching users goes in the status menu only when more than one user exists. | Low | M–L |
| 26 | **Digital Wellbeing**, screen time | Hidden (Settings; Lineage has no Google DW) | Low priority. If wanted, add a small Events "Today" section with screen time, or a Belle data-tracker-like ring widget. | Low | M |
| 27 | **Wi-Fi QR share/scan**, Quick Share, NFC | Hidden (Settings) | Long-press Internet in the status menu already opens the panel. Add "Share network" as a QR sheet in the N9 Wi-Fi page. Scan via Aperture's QR mode. | Low | S |
| 28 | **Accessibility**: TalkBack, magnification gestures, accessibility button and shortcut | Hidden; the accessibility nav button is Removed (no navbar) | The accessibility button becomes a toolbar item (EtesianBottomBar) when a service asks for it. Volume-keys shortcut is unchanged. Check that edge gestures do not swallow TalkBack's gestures (principle 10). | High | M |
| 29 | **Keyboard**: emoji, clipboard, voice, one-handed, floating | Avail (LatinIME patched) | Keep it. The hide key is done. | – | – |
| 30 | **Battery saver, dark theme, colours** (Material You) | Hidden | N9 had no theming: battery saver is a status menu switch, dark mode = the N9 inverted theme. Monet does not apply (one accent per app, principle 5). | Low | S |
| 31 | **Satellite SOS, emergency info** | Hidden (?) | Emergency call on the security screen. Medical info as an Events-styled card on the lock screen. | Med | S |
| 32 | **Live updates and ongoing notifications** (A16 progress-centric, timers, navigation, rides) | Never adapted | N9: an ongoing call/timer shows as a coloured status bar strip (green for a call). Make A16 "promoted ongoing" notifications show as a status bar chip and as the first Events item with a progress bar. | Med | M |
| 33 | **App pinning** (screen pinning) | Hidden; unpin gesture (Back + Overview) Removed | Swipes are disabled while pinned; long-press Back in the toolbar unpins (with the security screen). Must exist, or a pinned app traps the user. | Med | S |

## Recommended roadmap (top 10)

1. **Status menu "More" switches + `TileService` host** (#3, #14, #30). This brings back what QS gave, in Belle lit-squircle form, with switches chosen in edit mode. High, M–L.
2. **Events long-press action sheet with inline reply and snooze** (#1, #6). The biggest loss for daily use. High, M.
3. **Media: lock screen music card + status menu music row with output picker** (#5, #22). High, M.
4. **N9 NotificationBanner for heads-up** (#2). High, M.
5. **Profiles = ringer + Do Not Disturb + Modes in the status menu** (#4). High, S–M.
6. **Sharesheet restyle** (#16). High, M.
7. **Privacy indicators + accessibility button and gesture audit** (#23, #28). High, S–M.
8. **Screenshot InfoBanner → Glimpse edit** (#13). Med, M.
9. **Power menu and permission dialogs as N9 overlays and QueryDialogs** (#17, #24). Med, M.
10. **App pinning exit, ongoing live updates as status-bar strip, next event under the lock clock** (#33, #32, #21). Med, S–M each.

Leave out on purpose: one-handed mode, lock screen shortcuts, bubbles, app pairs and Monet. Each one adds clutter or goes against principles 5 and 8.

## Open questions

1. Should the stock shade stay reachable at all, for example by swiping down from the top on Home only, as a hidden fallback? Or is the status menu the only surface?
2. Inline reply: a sheet from Events only, or also from the lock screen cards (with the security screen first)?
3. Status menu "More" grid: should it always show, or only once it has been expanded?
4. Where should screen time live, if anywhere?
5. Does the Pro1-X hardware keyboard change any priorities (for example the assistant on a key, or Fn shortcuts for switches)?
6. The "(?)" rows (heads-up look, lock screen media, volume panel, power menu, screenshot overlay) need a check on the phone before work starts.

# EtesianMail ("Mail")

Thunderbird for Android 24.0 (Apache-2.0), rebranded as "Mail" (`com.bompotis.etesian.mail`, own icon,
no Mozilla trademarks) and restyled like the Nokia N9 email app: light grey pages, N9 blue #00A5E5,
orange #FF8600 links, the system UI font, engraved rules between rows, bold unread senders with a blue dot,
labelled To:/Subject: compose fields. Its actions go on the Etesian system toolbar (STYLE_LIGHT) when
available; the FAB and up arrows are hidden then.

- `patches/0001-N9-mail.patch`: all source changes against tag THUNDERBIRD_24_0 (`git apply`; binary diff
  because the Thunderbird launcher PNGs are deleted). It lists itself in the NOTICE file as required.
- `build-mail.sh`: applies the patch (if needed) to `~/android/email/thunderbird-android`, runs
  `./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx6g :app-thunderbird:assembleFossRelease` with JDK 21 and
  copies the unsigned APK to `EtesianMail.apk`.
- `Android.bp.in` (copied to `Android.bp` by build-mail.sh once the APK exists, because Soong rejects a
  module whose apk file is missing): `android_app_import` into /product/app, signed by Soong with the default key, no preopt.
  `etesian.mk` adds it only when `EtesianMail.apk` exists.

Updating the patch: make changes in the Thunderbird tree, then
`git add -A && git diff --cached --binary > .../patches/0001-N9-mail.patch && git reset`.

Known limitation: OAuth sign-in (Gmail, Outlook, Yahoo, AOL) uses client ids registered to Mozilla's package
name and redirect scheme, so it will not work with the new application id. Use password / app-password
accounts, or register our own OAuth clients.

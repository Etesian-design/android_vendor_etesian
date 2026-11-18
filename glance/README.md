# Etesian Glance

One screen like Google News, with a search bar on top that works like Sesame: apps, app shortcuts, contacts, text
messages, notifications and chat messages, files, Settings pages, the news and the web. The source lives in its own
repo (`~/Git/etesian-glance`); the launcher shows its news as a screen and its results in the applications search.

- `build-glance.sh`: runs Glance's unit tests and release build (`GLANCE_SRC`, default `~/Git/etesian-glance`),
  copies the unsigned APK to `EtesianGlance.apk` and `Android.bp.in` to `Android.bp`.
- `Android.bp.in`: `android_app_import` into /system_ext/app, signed by Soong with the platform key.
  `etesian.mk` adds it only when `EtesianGlance.apk` exists.
- Contacts are granted with the ROM, as for the launcher (`permissions/default-permissions_com.bompotis.etesian.glance.xml`);
  messages, notifications and files are asked for in Glance.

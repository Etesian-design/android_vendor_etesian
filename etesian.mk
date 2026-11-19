# Etesian (Nokia N9) experience on top of LineageOS.
PRODUCT_PACKAGES += \
    EtesianLauncher

# Framework settings: Etesian as the Recents provider.
PRODUCT_PACKAGES += \
    FrameworksEtesianOverlay


# Free fonts for Settings > Display > Font: Fira Sans and Ubuntu Sans (vendor/etesian/fonts).
PRODUCT_PACKAGES += \
    FiraSans-Light.ttf FiraSans-Regular.ttf FiraSans-Medium.ttf FiraSans-Bold.ttf \
    UbuntuSans-VF.ttf \
    FontFiraSansOverlay FontUbuntuSansOverlay

# Optional: the user's own N9 font and tones, if imported (n9-files/import.sh).
-include vendor/harmattan-n9/n9.mk

# Etesian's own interface sounds and tones (sounds/tones.py): always there, under their own names;
# the defaults (launcher N9Defaults.kt) and touch sounds use them when the N9's were not imported.
PRODUCT_COPY_FILES += \
    $(foreach f,$(notdir $(wildcard vendor/etesian/sounds/ui/*.wav)),vendor/etesian/sounds/ui/$(f):$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/etesian/$(f)) \
    vendor/etesian/sounds/tones/Etesian.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Etesian.ogg \
    vendor/etesian/sounds/tones/Etesian_message.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Etesian_message.ogg \
    vendor/etesian/sounds/tones/Etesian_mail.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Etesian_mail.ogg \
    vendor/etesian/sounds/tones/Etesian_calendar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Etesian_calendar.ogg \
    vendor/etesian/sounds/tones/Etesian_dawn.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Etesian_dawn.ogg
ifeq ($(wildcard vendor/harmattan-n9/ui/static_press.wav),)
PRODUCT_PACKAGES += \
    EtesianSoundsOverlay
endif

# Etesian boot animation (bootanimation/README.md): the N9 motion with our own wordmark and
# redrawn background. Set after n9.mk so it replaces the Nokia one.
# Drawn at the screen's own size: 1080x2160 for the Pro1-X, 1440x3120 (the OnePlus 7 Pro) otherwise.
ifeq ($(TARGET_SCREEN_WIDTH),1080)
TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation.zip
else
TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation-1440x3120.zip
endif

# Etesian Store: F-Droid client in the Nokia Store style.
PRODUCT_PACKAGES += \
    EtesianStore

# Etesian Feeds: the N9 Feeds app; the launcher's Feed section reads it.
PRODUCT_PACKAGES += \
    EtesianFeeds

# Meeye icons by YeyeBBC (CC BY-NC 4.0), the launcher's default icon pack (meeye/fetch.sh)
ifneq ($(wildcard vendor/etesian/meeye/Meeye.apk),)
PRODUCT_PACKAGES += \
    Meeye
endif

# Clock's alarm permissions, granted with the ROM (permissions/default-permissions_com.android.deskclock.xml)
PRODUCT_PACKAGES += \
    default-permissions_com.android.deskclock.xml

# Glance: news and search across the phone (prebuilt from vendor/etesian/glance/build-glance.sh)
ifneq ($(wildcard vendor/etesian/glance/EtesianGlance.apk),)
PRODUCT_PACKAGES += \
    EtesianGlance
endif

# N9 Mail (prebuilt from vendor/etesian/mail/build-mail.sh)
ifneq ($(wildcard vendor/etesian/mail/EtesianMail.apk),)
PRODUCT_PACKAGES += \
    EtesianMail
endif

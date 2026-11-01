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
-include vendor/etesian-n9/n9.mk

# Etesian boot animation (bootanimation/README.md): the N9 motion with our own wordmark and
# redrawn background. Set after n9.mk so it replaces the Nokia one.
TARGET_BOOTANIMATION := vendor/etesian/bootanimation/bootanimation.zip

# Etesian Store: F-Droid client in the Nokia Store style.
PRODUCT_PACKAGES += \
    EtesianStore

# Etesian Feeds: the N9 Feeds app; the launcher's Feed section reads it.
PRODUCT_PACKAGES += \
    EtesianFeeds

# N9 Mail (prebuilt from vendor/etesian/mail/build-mail.sh)
ifneq ($(wildcard vendor/etesian/mail/EtesianMail.apk),)
PRODUCT_PACKAGES += \
    EtesianMail
endif

# N9 font and tones, imported from the user's own N9 (see vendor/etesian/n9-files/import.sh).
N9_DIR := vendor/harmattan-n9

PRODUCT_COPY_FILES += \
    $(foreach f,$(notdir $(wildcard $(N9_DIR)/fonts/*.ttf)),$(N9_DIR)/fonts/$(f):$(TARGET_COPY_OUT_PRODUCT)/fonts/$(f)) \
    $(foreach f,$(notdir $(wildcard $(N9_DIR)/icons/*.png)),$(N9_DIR)/icons/$(f):$(TARGET_COPY_OUT_PRODUCT)/media/etesian/icons/$(f)) \
    $(foreach f,$(notdir $(wildcard $(N9_DIR)/ui/*.wav)),$(N9_DIR)/ui/$(f):$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/n9/$(f)) \
    $(foreach f,$(notdir $(wildcard $(N9_DIR)/vibra/*.txt)),$(N9_DIR)/vibra/$(f):$(TARGET_COPY_OUT_PRODUCT)/etc/etesian/$(f)) \
    $(foreach f,$(notdir $(wildcard $(N9_DIR)/vibra/tones/*.txt)),$(N9_DIR)/vibra/tones/$(f):$(TARGET_COPY_OUT_PRODUCT)/etc/etesian/vibra/$(f)) \
    $(foreach d,ringtones notifications alarms,$(foreach f,$(notdir $(wildcard $(N9_DIR)/$(d)/*)),$(N9_DIR)/$(d)/$(f):$(TARGET_COPY_OUT_PRODUCT)/media/audio/$(d)/$(f)))

# The N9's default tones and font are applied once by the launcher (N9Defaults.kt).

# Nokia Pure Text, offered in the font picker.
PRODUCT_PACKAGES += \
    FontNokiaPureOverlay

# The N9's touch and keyboard sounds, when they were imported.
ifneq ($(wildcard $(N9_DIR)/ui/static_press.wav),)
PRODUCT_PACKAGES += \
    N9SoundsOverlay
endif

# The "N9 icons" pack for the launcher, when N9 app icons were imported (make-iconpack.sh).
ifneq ($(wildcard $(N9_DIR)/iconpack/res/drawable-nodpi/*.png),)
PRODUCT_PACKAGES += \
    N9IconPack
endif

# The N9 startup animation, when it was imported.
ifneq ($(wildcard $(N9_DIR)/bootanimation.zip),)
TARGET_BOOTANIMATION := $(N9_DIR)/bootanimation.zip
endif

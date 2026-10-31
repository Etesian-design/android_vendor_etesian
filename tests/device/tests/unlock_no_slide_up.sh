# Bugs 2026-10-29 #1 #2: unlocking slowly, home slid up (SystemUI's stock surface-behind move) instead
# of only zooming in out of the dark; and a widget picker left open covered home in black during the
# unlock. The N9 slide keeps the home window still (the zoom is in-window), and the picker closes on
# screen off.
K=~/android/lineage/frameworks/base/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardUnlockAnimationController.kt
check "N9 slide: surface behind is neither moved up nor scaled" 'grep -q "if (etesianDriven) 0f" $K && grep -q "if (etesianDriven) scaleFactor = 1f" $K'
P=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/WidgetPicker.kt
check "widget picker closes when the screen goes off" 'grep -q "ACTION_SCREEN_OFF" $P'
check "patch carries the unlock fix" 'grep -q etesianDriven ~/android/lineage/vendor/etesian/patches/frameworks_base/*.patch'
# Device: hold the lock screen half way; home's window must sit at the screen's top (no offset).
unlock; home
lock; sh_ input keyevent WAKEUP; sleep 1.5
sh_ "input motionevent DOWN 950 1000; for x in 900 850 800 750 700 650 600 550; do input motionevent MOVE \$x 1000; done"; sleep 1
y=$(sh_ dumpsys SurfaceFlinger | grep -A12 "transition-leash" | grep -m1 -o "tx=[-0-9.]*, ty=[-0-9.]*" )
sh_ input motionevent UP 550 1000 >/dev/null; sleep 1; unlock
check "home is not moved down while the lock screen slides ($y)" '[ -z "$y" ] || echo "$y" | grep -q "ty=0"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

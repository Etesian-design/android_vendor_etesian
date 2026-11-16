# The Etesian lock screen on: lock and swipe to unlock several times, also right after SystemUI restarts (its wallpaper
# is reloaded then, which once crashed SystemUI in a loop: a recycled bitmap drawn by EtesianLockWallpaper).
check "Etesian on" '[ "$(sh_ settings get secure etesian_gestures)" = 1 ]'
sh_ logcat -b crash -c
for i in 1 2 3; do lock; swipe_unlock; done
sh_ am crash com.android.systemui >/dev/null 2>&1; sleep 8; sh_ logcat -b crash -c
for i in 1 2 3; do lock; swipe_unlock; done
check "unlocked" '! sh_ dumpsys window | grep -q "isKeyguardShowing=true"'
check "systemui did not crash" '! crashed com.android.systemui'
home

# Bug 2026-11-04 (user): the bottom bar was "its own thing", out of sync in every transition
# (Settings, Gallery, Messaging): SystemUI drew it in the navigation bar window and swapped its
# buttons on task-stack callbacks. The toolbar library now draws it in the page's own window, so it
# moves with the page; SystemUI draws nothing and takes no touches there.
# Gallery: a photo opens full screen with no bars; a tap slides the bottom bar in, the next out,
# and no second (default) bar shows behind it. Opening a page is the N9 push (from the right).
unlock
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# The Back button's package, or nothing when no bar is on screen.
back_pkg() { dump | tr '>' '\n' | grep 'content-desc="Back"' | grep -o 'package="[^"]*"' | head -1 | cut -d'"' -f2; }
nav_touchable() { sh_ dumpsys window windows | grep -A6 'Window{.* NavigationBar0}' | grep ' fl=' | grep -q NOT_TOUCHABLE && echo no || echo yes; }

sh_ am start -a android.settings.WIFI_SETTINGS >/dev/null; sleep 3
check "Settings sub page: Back is drawn in Settings' own window ($(back_pkg))" '[ "$(back_pkg)" = com.android.settings ]'
check "SystemUI's bar takes no touches over it" '[ "$(nav_touchable)" = no ]'
sh_ am start -n com.android.messaging/.ui.conversationlist.ConversationListActivity >/dev/null; sleep 3
# Its main page has no Back: its New message button, in the bottom strip of its own window
# (2026-11-04: a page that isn't edge-to-edge got a 0 px bar, the inset consumed by the decor).
new_msg() { dump | tr '>' '\n' | grep 'package="com.android.messaging"' | grep -i 'content-desc="Start new conversation"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]' | grep -o ',[0-9]*' | tr -d ,; }
check "Messaging: its bar is in its own window (New message at y=$(new_msg))" '[ "$(new_msg)" -gt 1900 ] 2>/dev/null'
check "page animation is the N9 push" 'sh_ cmd overlay lookup --verbose android android:anim/activity_open_enter | grep -q "from default configuration of com.bompotis.etesian.overlay.framework"'

id=$(sh_ content query --uri content://media/external/images/media --projection _id | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
if [ -n "$id" ]; then
    sh_ am start -a android.intent.action.VIEW -t image/jpeg -d content://media/external/images/media/$id org.lineageos.glimpse >/dev/null; sleep 3
    check "Gallery: a photo opens full screen, no bar ($(back_pkg))" '[ -z "$(back_pkg)" ]'
    sh_ input tap 540 1000; sleep 1.5
    check "Gallery: a tap slides its bar in, in its own window ($(back_pkg))" '[ "$(back_pkg)" = org.lineageos.glimpse ]'
    sh_ input tap 540 1000; sleep 1.5
    check "Gallery: the next tap slides it away, no bar behind it ($(back_pkg))" '[ -z "$(back_pkg)" ]'
    sh_ input keyevent BACK; sleep 1
else
    bad "Gallery: no photo on the phone to open"
fi
for p in com.android.settings com.android.messaging org.lineageos.glimpse com.android.systemui; do
    check "$p did not crash" '! crashed $p'
done
home

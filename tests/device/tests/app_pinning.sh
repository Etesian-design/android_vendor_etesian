# Settings → App pinning (mock 10): "Pin" in the carousel action row pins the middle card
# (screen pinning), the N9 bubble "App pinned · Long-press Back to unpin" shows, long-press Back unpins.
# Long-press on a card is ALWAYS N9 edit mode (Done / Close all), pinning on or off: no card menu.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
pinned() { sh_ dumpsys activity activities | grep -q "mLockTaskModeState=PINNED"; }
card_xy() { dump | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o '[0-9]\+' | tail -4 | tr '\n' ' '; }
old=$(sh_ settings get secure etesian_extra_pinning); oldb=$(sh_ settings get secure etesian_notif_over)
oldl=$(sh_ settings get secure lock_to_app_enabled); olde=$(sh_ settings get secure lock_to_app_exit_locked)
to_switcher() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; sleep 1; }
longpress_card() { set -- $(card_xy "$1"); local x=$(( ($1+$3)/2 )) y=$(( $2 - 200 )); sh_ input swipe $x $y $x $y 900; sleep 1.2; }
unlock; sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2
for v in 0 1; do
  sh_ settings put secure etesian_extra_pinning $v; sh_ settings put secure etesian_notif_over android
  to_switcher; longpress_card Settings
  check "pinning=$v: long-press shows no card menu" '! dump | grep -q "text=\"Pin app\"\|text=\"Close apps\""'
  check "pinning=$v: long-press enters N9 edit mode" 'dump | grep -q "text=\"Done\""'
  tap_text Done; sleep 1
done
# The carousel (one card a row): its action row has Pin while the extra is on.
oldsp=$(sh_ "cat /data/data/$LAUNCHER/shared_prefs/switcher.xml 2>/dev/null")
sh_ "echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><int name=\"span\" value=\"1\" /></map>' > /data/data/$LAUNCHER/shared_prefs/switcher.xml"
sh_ am force-stop $LAUNCHER; sleep 1; to_switcher
check "carousel shows Pin" 'dump | grep -q "text=\"Pin\""'
tap_text Pin; sleep 2
check "Pin app pins the task" 'pinned'
check "pinned bubble shown with Pinning alone (bubbles off)" 'sh_ dumpsys window windows | grep -q EtesianBubble'
shot app_pinned
check "the bar shows Back while pinned" '[ $(pixel 98 2090) -gt 150 ]'
# No secure code is needed in the test: unpinning without the lock keeps the phone usable.
sh_ settings put secure lock_to_app_exit_locked 0
sh_ input swipe 90 2100 90 2100 1600; sleep 2
check "long-press Back unpins" '! pinned'
sh_ am task lock stop >/dev/null 2>&1
[ -n "$oldsp" ] && sh_ "echo '$oldsp' > /data/data/$LAUNCHER/shared_prefs/switcher.xml" || sh_ rm -f /data/data/$LAUNCHER/shared_prefs/switcher.xml
sh_ am force-stop $LAUNCHER; sleep 1   # the running launcher still holds the test layout in memory
sh_ am force-stop $LAUNCHER
for k in extra_pinning:$old notif_over:$oldb; do n=${k%%:*}; v=${k#*:}; [ "$v" = null ] && sh_ settings delete secure etesian_$n >/dev/null || sh_ settings put secure etesian_$n "$v"; done
[ "$oldl" = null ] || sh_ settings put secure lock_to_app_enabled "$oldl"; [ "$olde" = null ] || sh_ settings put secure lock_to_app_exit_locked "$olde"
check "launcher did not crash" '! crashed $LAUNCHER'
check "systemui did not crash" '! crashed com.android.systemui'
home

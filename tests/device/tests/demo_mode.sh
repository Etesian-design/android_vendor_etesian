# Demo mode (Demo.kt; for README screenshots and recordings): made-up data in place of the owner's, switched only over a
# root adb shell. While on: Events shows the launcher's sample notifications and none of the owner's, the made-up calendar,
# feeds and weather; the grid shows only the stock apps, no folders. The owner's grid layout is never changed, and
# switching it off brings everything back.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1.5; }
apps_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 1; }
# Switch, restart the launcher, and wait for its notification listener to be bound again.
demo() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind demo --ez on $1 >/dev/null; sh_ am force-stop $LAUNCHER; sleep 1; home
  for _ in $(seq 20); do sh_ dumpsys notification | grep -q "$LAUNCHER/$LAUNCHER.EventsService} (user 0): android.service.notification.INotificationListener" && break; sleep 1; done; sleep 1; }
APPS=/data/data/$LAUNCHER/shared_prefs/apps.xml
layout0=$(sh_ md5sum $APPS | cut -d' ' -f1)
# The whole of Events, scrolled through.
events_all() { events_pane; local all="" i; for i in 1 2 3 4 5 6 7 8; do all="$all$(dump)"; sh_ input swipe 540 1700 540 500 300; sleep 0.8; done; echo "$all"; }
# The owner's own notifications, as their section headers ("Low priority (2)"...), read before demo mode: nothing is
# posted from another app (that would need a channel with a sound).
owner_sections() { grep -o 'text="\(Low priority\|Notifications\) ([0-9]*)"' | sort -u; }
unlock
owner0=$(events_all | owner_sections)
demo true
# after the restart: stopping the launcher also removes its own notifications
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message >/dev/null; sleep 2
ev=$(events_all)
check "demo: the sample notification shows" 'echo "$ev" | grep -q "Anna Virtanen"'
[ -n "$owner0" ] && check "demo: none of the owner's notifications ($(echo $owner0))" '[ -z "$(echo "$ev" | owner_sections | grep -Fx -f <(echo "$owner0"))" ]'
check "demo: the made-up feeds" 'echo "$ev" | grep -q "Etesian journal\|Northern news"'
check "demo: the made-up calendar" 'echo "$ev" | grep -q "Design review\|Sauna with Mikko\|Stand-up"'
check "demo: the made-up weather place" 'echo "$ev" | grep -q "Helsinki"'
shot demo_events
apps_pane; ap=$(dump)
check "demo: stock apps in the grid" 'echo "$ap" | grep -q "text=\"Settings\""'
check "demo: no folders" '! echo "$ap" | grep -q "text=\"Folder\""'
sh_ pm list packages | grep -q "package:webtor.app" && check "demo: no apps the owner installed" '! echo "$ap" | grep -q "Torrent Player"'
shot demo_apps
demo false
check "the owner's grid layout untouched" '[ "$(sh_ md5sum $APPS | cut -d" " -f1)" = "$layout0" ]'
[ -n "$owner0" ] && check "off: the owner's notifications are back" '[ "$(events_all | owner_sections)" = "$owner0" ]'
check "off: demo setting gone" '! sh_ cat /data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml | grep -q "etesian_demo\" value=\"true"'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
check "launcher did not crash" '! crashed $LAUNCHER'
home

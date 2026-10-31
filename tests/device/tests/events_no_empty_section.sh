# N9: with no notifications the Events view has no Notifications section at all (no header, no
# "No new notifications" text). Kept dismissed ones (Android extras) then show alone, with no header.
# The user's own notifications are never touched: the on-phone check only runs when there are none.
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "no 'No new notifications' text" '! grep -q "No new notifications" $SRC/EventsPage.kt'
check "Notifications header only when there are some" 'grep -B1 "out += Row.Section(EventsClassifier.Section.NOTIFICATIONS" $SRC/EventsPage.kt | grep -q "if (notifications.isNotEmpty() || live.isNotEmpty() || priority.isNotEmpty())"'
check "kept dismissed rows added outside that block" 'awk "/if \(notifications.isNotEmpty\(\) \|\| live/{b=1} b&&/^        }/{b=0} !b&&/Row.Ghosts\(it\)/{f=1} END{exit !f}" $SRC/EventsPage.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
unlock; home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
if [ "$(sh_ dumpsys notification --noredact | grep -c 'NotificationRecord(.*pkg=' )" -eq 0 ]; then
    check "phone: no Notifications header when empty" '! dump | grep -q "text=\"Notifications\""'
fi
check "phone: no 'No new notifications' text" '! dump | grep -q "No new notifications"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

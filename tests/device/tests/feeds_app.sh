# The Feeds app (com.bompotis.etesian.feeds) owns the feeds; the launcher reads its provider.
unlock; home
FEEDS=com.bompotis.etesian.feeds
sh_ logcat -b crash -c
sh_ monkey -p $FEEDS -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 2.5
check "feeds main page is the feeds app" 'top_activity | grep -q "^$FEEDS/"'
check "feeds main page is not a home activity" '[ "$(task_type $FEEDS/.MainActivity)" = standard ]'
check "launcher may read the feeds" 'sh_ dumpsys package $LAUNCHER | grep -q "$FEEDS.permission.READ: granted=true"'
check "provider lists feeds" '[ "$(sh_ content query --uri content://$FEEDS/feeds | grep -c ^Row)" -gt 0 ]'
check "provider lists items" '[ "$(sh_ content query --uri content://$FEEDS/items | grep -c ^Row)" -gt 0 ]'
check "provider state call" 'sh_ content call --uri content://$FEEDS --method state | grep -q refreshing'
# The first item opens in the app's reader (as from Events), in the app's task.
ID=$(sh_ content query --uri content://$FEEDS/items | head -1 | sed 's/.*, id=//; s/, read=.*//')
sh_ am start -n $FEEDS/.ReaderActivity --es feed_item "'$ID'" >/dev/null; sleep 2
check "reader opens in the feeds app" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
check "reader is not in the home task" '[ "$(task_type $FEEDS/.ReaderActivity)" = standard ]'
# The launcher's Events view still lists them (read through the provider).
home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done
FT=$(sh_ content query --uri content://$FEEDS/items | head -1 | sed 's/.*feedTitle=//; s/, title=.*//')
check "events lists feed items" 'sh_ cat /sdcard/u.xml | grep -qF "text=\"$FT\""'
home
check "feeds app didn't crash" '! crashed $FEEDS'
check "launcher didn't crash" '! crashed $LAUNCHER'

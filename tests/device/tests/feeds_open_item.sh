# Bugs 2026-11-04 (feeds): a tap on a widget row didn't open the item; with Feeds already open a
# tap only brought Feeds to the front; back from the reader exited or went to the main page at
# random; read items stayed in Events; the RSS widget's orange dot stayed on read items.
# Items now open through the app's OpenActivity, which rebuilds the Feeds task as
# Feeds › the feed's posts (widgets) or All posts (Events) › reader; the provider has "read".
FEEDS=com.bompotis.etesian.feeds
SRC=/home/memphisx/android/lineage/vendor/etesian
FP=/data/data/$FEEDS/shared_prefs/feeds.xml
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# The Feeds task's activities, top first.
stack() { sh_ dumpsys activity activities | awk '/\* Task\{/{t=$0} /\* Hist/ && t ~ /:'$FEEDS' /' | grep -o "$FEEDS/[.A-Za-z]*"; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; }
check "launcher opens items through OpenActivity" 'grep -q "\$PACKAGE.OpenActivity" $SRC/launcher/app/src/main/java/com/bompotis/etesian/launcher/Feeds.kt'
check "widget rows carry their feed" '[ "$(grep -c "Feeds.itemFillIn(item)" $SRC/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleWidgets.kt $SRC/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt | grep -c ":1")" = 2 ]'
check "RSS widget dot follows read" 'grep -q "R.id.bc_new, if (item.read)" $SRC/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt'
check "Events lists unread only" 'grep -q "Feeds.forEvents(Feeds.items(context))" $SRC/launcher/app/src/main/java/com/bompotis/etesian/launcher/EventsPage.kt'
# dumpsys package lists only activities with intent filters: read the manifest
check "OpenActivity is exported to the launcher" 'grep -A3 "\.OpenActivity" ~/android/lineage/vendor/etesian/feeds/app/src/main/AndroidManifest.xml | grep -q "exported=\"true\""'
# Read state is the user's: put it back afterwards.
sh_ "cp $FP /data/local/tmp/feeds_prefs.bak"
sh_ logcat -b crash -c
unlock; home
ROW=$(sh_ content query --uri content://$FEEDS/items | grep "read=0" | head -1)
check "provider has a read column" 'sh_ content query --uri content://$FEEDS/items | head -1 | grep -q ", read=[01]$"'
ID=$(echo "$ROW" | sed 's/.*, id=//; s/, read=.*//')
URL=$(echo "$ROW" | sed 's/.*feedUrl=//; s/, feedTitle=.*//')
FT=$(echo "$ROW" | sed 's/.*feedTitle=//; s/, title=.*//')
TITLE=$(echo "$ROW" | sed 's/.*, title=//; s/, summary=.*//')
check "an unread item to open" '[ -n "$ID" ]'
# From a widget (warm: Feeds already open on its main page): reader, then back to the feed's posts.
sh_ am start -n $FEEDS/.MainActivity >/dev/null; sleep 2; home
sh_ am start -n $FEEDS/.OpenActivity --es feed_item "'$ID'" --es feed "'$URL'" >/dev/null; sleep 2.5
check "widget: the reader opens over an open Feeds" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
check "widget: stack is reader, posts, main" '[ "$(stack | tr "\n" " ")" = "$FEEDS/.ReaderActivity $FEEDS/.PostsActivity $FEEDS/.MainActivity " ]'
check "item is read in the provider" 'sh_ content query --uri content://$FEEDS/items | grep -F "id=$ID," | grep -q "read=1"'
sh_ input keyevent BACK; sleep 1.5
check "widget: back goes to the feed's posts" 'top_activity | grep -q "$FEEDS/.PostsActivity" && dump | grep -qF "text=\"$FT\"" && ! dump | grep -q "text=\"All posts\""'
sh_ input keyevent BACK; sleep 1.5
check "then back to the Feeds main page" 'top_activity | grep -q "$FEEDS/.MainActivity"'
# Events: the read item has gone (automatically, no reopen); the next unread one opens with a real tap.
events_pane; sleep 1.5
check "Events: read item left" '! dump | grep -qF "text=\"$TITLE: "'
ROW2=$(sh_ content query --uri content://$FEEDS/items | grep "read=0" | head -1)
ID2=$(echo "$ROW2" | sed 's/.*, id=//; s/, read=.*//')
T2=$(echo "$ROW2" | sed 's/.*, title=//; s/, summary=.*//')
check "Events lists the unread item" 'dump | grep -qF "text=\"$T2"'
dump >/dev/null
set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "text=\"$T2" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+')
[ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 2.5
check "Events: a tap opens the reader (Feeds was open)" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
sh_ input keyevent BACK; sleep 1.5
check "Events: back goes to All posts" 'top_activity | grep -q "$FEEDS/.PostsActivity" && dump | grep -q "text=\"All posts\""'
check "second item is read" 'sh_ content query --uri content://$FEEDS/items | grep -F "id=$ID2," | grep -q "read=1"'
home
check "feeds app didn't crash" '! crashed $FEEDS'
check "launcher didn't crash" '! crashed $LAUNCHER'
sh_ "am force-stop $FEEDS; cat /data/local/tmp/feeds_prefs.bak > $FP; rm -f /data/local/tmp/feeds_prefs.bak"

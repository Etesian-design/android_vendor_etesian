# 2026-11-24 (user): Find feeds searches well-known feed lists (rumca-js awesome-database-feeds, InfoBubble), fetched
# once on the first search, kept as an index on the phone and searched offline. Never the live lists: fakes served from
# this machine (data/feeds_directory_fixtures.py) through adb reverse. The user's Feeds data is restored at the end.
FEEDS=com.bompotis.etesian.feeds
D=/data/data/$FEEDS
PORT=8733
DATA=/home/memphisx/android/lineage/vendor/etesian/tests/device/data
FIX=$(mktemp -d)
python3 $DATA/feeds_directory_fixtures.py $FIX $PORT
python3 -m http.server $PORT --bind 127.0.0.1 --directory $FIX >/dev/null 2>&1 & SRV=$!
adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
feeds() { sh_ content query --uri content://$FEEDS/feeds; }
type_query() { tap_text "$1" >/dev/null 2>&1 || true; sh_ input keyevent KEYCODE_MOVE_END; for _ in $(seq 30); do sh_ input keyevent KEYCODE_DEL; done; sh_ input text "$2"; }

sh_ "am force-stop $FEEDS; rm -rf /data/local/tmp/feeds_backup; mkdir -p /data/local/tmp/feeds_backup; cp -a $D/shared_prefs $D/files /data/local/tmp/feeds_backup/ 2>/dev/null"
u=$(sh_ stat -c %u $D)
sh_ "mkdir -p $D/files; rm -f $D/files/directory.db; echo http://127.0.0.1:$PORT > $D/files/test-directory-base; chown -R $u:$u $D/files; restorecon -R $D/files"
OWN=$(feeds | grep -c "url=")
unlock; home
sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1.5
tap_desc() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
tap_desc "Menu"; sleep 1; tap_text "Find feeds" >/dev/null; sleep 1.5
check "Find feeds opens" 'top_activity | grep -q "$FEEDS/.FindActivity"'
check "it says the lists come on the first search" 'dump | grep -q "downloaded the first time you search"'
check "it credits the lists and their licences" 'sh_ cat /sdcard/u.xml | grep -q "CC BY 4.0" && sh_ cat /sdcard/u.xml | grep -q "ODbL"'
type_query "Name, site or topic" "etesian"; sleep 6
check "the first search downloads the lists and finds the feed" 'dump | grep -q "text=\"Etesian Test Feed\""'
check "with what it is about" 'sh_ cat /sdcard/u.xml | grep -q "swipe gestures and the N9"'
check "a dead feed is left out" '! sh_ cat /sdcard/u.xml | grep -q "Etesian Dead Feed"'
check "the lists are kept on the phone" 'sh_ ls $D/files/directory.db >/dev/null 2>&1'
shot feeds_find
type_query "etesian" "testland"; sleep 2
check "the InfoBubble list is searched too (country)" 'dump | grep -q "text=\"quasarnews.example\"" && sh_ cat /sdcard/u.xml | grep -q "Testland · Science &amp; Tech"'
type_query "testland" "etesian"; sleep 2
tap_text "Etesian Test Feed" >/dev/null; sleep 4
check "a tap subscribes to it" 'feeds | grep -q "etesian-test.xml"'
check "and the row says so" 'dump | grep -q "text=\"Subscribed\""'
# Offline: the lists are not fetched again.
adb reverse --remove tcp:$PORT >/dev/null; kill $SRV; wait $SRV 2>/dev/null
type_query "etesian" "kitchen"; sleep 2
check "later searches need no connection" 'dump | grep -q "text=\"Kitchen Gazette\""'
sh_ input keyevent BACK; sleep 0.5; sh_ input keyevent BACK; sleep 1
check "Feeds did not crash" '! crashed $FEEDS'
sh_ "am force-stop $FEEDS; rm -rf $D/shared_prefs $D/files; cp -a /data/local/tmp/feeds_backup/shared_prefs /data/local/tmp/feeds_backup/files $D/ 2>/dev/null; chown -R $u:$u $D/shared_prefs $D/files; restorecon -R $D/shared_prefs $D/files; rm -rf /data/local/tmp/feeds_backup"
check "the user's feeds are back as they were ($OWN)" '[ "$(feeds | grep -c "url=")" = "$OWN" ]'
rm -rf $FIX
home

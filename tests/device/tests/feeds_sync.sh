# 2026-11-24 (user): Feeds can sync with the user's own server: FreshRSS (Google Reader API), Nextcloud News and Tiny Tiny RSS. The
# server keeps the feeds, the posts and what is read or starred; Feeds shows them and sends its changes back. Never a
# live server: a fake one on this machine (data/feeds_sync_server.py) through adb reverse. The user's own feeds are put
# aside while signed in and come back on signing out; everything of theirs is restored exactly at the end.
FEEDS=com.bompotis.etesian.feeds
D=/data/data/$FEEDS
PORT=8731
SRV_PY="$(dirname "$0")/../data/feeds_sync_server.py"
[ -f "$SRV_PY" ] || SRV_PY=/home/memphisx/android/lineage/vendor/etesian/tests/device/data/feeds_sync_server.py
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_desc() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
state() { curl -s http://127.0.0.1:$PORT/_state; }
items() { sh_ content query --uri content://$FEEDS/items; }
feeds() { sh_ content query --uri content://$FEEDS/feeds; }
# Fill a dialog's field (found by its hint) with text.
# Long press on the element with text $1.
hold_text() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; }
server_has() { state | python3 -c "import json,sys; sys.exit(0 if any(f['url']==sys.argv[1] for f in json.load(sys.stdin)['feeds'].values()) else 1)" "$1"; }
refresh() { sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1; tap_desc "Refresh"; sleep 4; }
fill() { tap_text "$1" >/dev/null; sleep 0.4; sh_ input text "$(printf %s "$2" | sed 's/ /%s/g')"; sleep 0.3; }

sh_ "am force-stop $FEEDS; rm -rf /data/local/tmp/feeds_backup; mkdir -p /data/local/tmp/feeds_backup; cp -a $D/shared_prefs $D/files /data/local/tmp/feeds_backup/ 2>/dev/null"
OWN=$(feeds | grep -c "url=")
# 2026-11-24 (user): the password manager's "save password?" prompt covers the dialogs. Off for the test only; the
# user's own autofill service is put back at the end.
AF=$(sh_ settings get secure autofill_service); sh_ settings put secure autofill_service null
unlock; home

sign_in() { # service name, password
  feeds_settings; tap_text "Sync" >/dev/null; sleep 1
  tap_text "$1" >/dev/null; sleep 1
  fill "Server address" "http://127.0.0.1:$PORT"; fill "User name" "etesian"; fill "Password" "$2"
  sh_ input keyevent BACK; sleep 0.5   # the keyboard
  tap_text "Sign in" >/dev/null; sleep 4
}

for SERVICE in "FreshRSS" "Nextcloud News" "Tiny Tiny RSS"; do
  case $SERVICE in FreshRSS) P=fr;; "Nextcloud News") P=nc;; *) P=tt;; esac
  python3 "$SRV_PY" $PORT & SRV=$!
  adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1

  sign_in "$SERVICE" "wrong"
  check "$SERVICE: a wrong password is refused, nothing changes" '! sh_ ls $D/shared_prefs | grep -q "^sync.xml" || ! sh_ cat $D/shared_prefs/sync.xml | grep -q "name=\"service\""'
  sh_ input keyevent BACK; sleep 0.5
  sign_in "$SERVICE" "secret"
  check "$SERVICE: signed in" 'sh_ cat $D/shared_prefs/sync.xml | grep -q "name=\"service\""'
  for _ in 1 2 3 4 5 6 7 8; do items | grep -q "Sync first post" && break; sleep 1; done
  check "$SERVICE: the server's feeds are the list ($(feeds | grep -c url=))" '[ "$(feeds | grep -c "url=")" = 2 ] && feeds | grep -q "Example News" && feeds | grep -q "Example Blog"'
  check "$SERVICE: with its posts" 'items | grep -q "title=Sync first post" && items | grep -q "title=Sync starred post"'
  check "$SERVICE: read on the server is read here" 'items | grep "title=Sync read post" | grep -q "read=1" && items | grep "title=Sync first post" | grep -q "read=0"'
  check "$SERVICE: starred on the server is starred here" 'sh_ cat $D/shared_prefs/feeds.xml | grep -A3 "name=\"starred\"" | grep -q "$P:"'
  shot feeds_sync_$P

  # Read here (opening it in the reader), then refresh: the server has it read.
  ID=$(items | grep "title=Sync first post" | sed 's/.*, id=//; s/, read=.*//')
  sh_ am start -n $FEEDS/.OpenActivity --es feed_item "'$ID'" >/dev/null; sleep 2.5
  sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1
  sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1; tap_desc "Refresh"; sleep 4
  check "$SERVICE: read here goes to the server" 'state | python3 -c "import json,sys; sys.exit(0 if json.load(sys.stdin)[\"items\"][\"101\"][\"unread\"] is False else 1)"'

  # Add a feed: it is added on the server.
  tap_desc "Add feed"; sleep 1; fill "Feed or website address" "https://added.example/rss"
  sh_ input keyevent BACK; sleep 0.5; tap_text "Add" >/dev/null; sleep 4
  check "$SERVICE: adding a feed adds it on the server" 'state | grep -q "added.example/rss"'
  check "$SERVICE: and it is in the list" 'feeds | grep -q "added.example/rss"'

  # 2026-11-24 (user): removing never assumes the server. Box left off: gone here, kept there, skipped by every sync.
  sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1
  hold_text "Example Blog"; sleep 1; tap_text "Remove" >/dev/null; sleep 1
  check "$SERVICE: removing asks, the server box off" 'dump | grep -q "content-desc=\"Also remove it from the server\"" && sh_ cat /sdcard/u.xml | grep "Also remove it from the server" | grep -q "checked=\"false\""'
  tap_text "Remove" >/dev/null; sleep 2
  check "$SERVICE: box off: gone from the phone" '! feeds | grep -q "Example Blog"'
  check "$SERVICE: but still on the server" 'server_has https://blog.example/atom'
  refresh
  check "$SERVICE: and a sync leaves it out" '! feeds | grep -q "Example Blog" && ! items | grep -q "Sync starred post"'
  feeds_settings; tap_text "Ignored feeds" >/dev/null; sleep 1.5
  check "$SERVICE: Ignored feeds lists it" 'dump | grep -q "text=\"Example Blog\""'
  shot feeds_ignored_$P
  tap_text "Example Blog" >/dev/null; sleep 1; tap_text "Sync again" >/dev/null; sleep 4
  check "$SERVICE: synced again, it is back" 'feeds | grep -q "Example Blog"'
  check "$SERVICE: and no longer ignored" '! dump | grep -q "text=\"Example Blog\""'
  sh_ input keyevent BACK; sleep 1
  # Box on: removed on the server too.
  sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1
  hold_text "Example News"; sleep 1; tap_text "Remove" >/dev/null; sleep 1
  tap_desc "Also remove it from the server"; sleep 0.5; tap_text "Remove" >/dev/null; sleep 3
  check "$SERVICE: box on: removed on the server" '! server_has https://news.example/feed.xml'
  check "$SERVICE: and here" '! feeds | grep -q "Example News"'

  # Sign out: the phone's own feeds come back.
  feeds_settings; tap_text "Sync" >/dev/null; sleep 1; tap_text "Sign out" >/dev/null; sleep 3
  check "$SERVICE: signed out, the phone's own feeds are back ($OWN)" '[ "$(feeds | grep -c "url=")" = "$OWN" ] && ! feeds | grep -q "Example News"'
  adb reverse --remove tcp:$PORT >/dev/null; kill $SRV; wait $SRV 2>/dev/null
done

check "Feeds did not crash" '! crashed $FEEDS'
u=$(sh_ stat -c %u $D)
sh_ "am force-stop $FEEDS; rm -rf $D/shared_prefs $D/files; cp -a /data/local/tmp/feeds_backup/shared_prefs /data/local/tmp/feeds_backup/files $D/ 2>/dev/null; chown -R $u:$u $D/shared_prefs $D/files; restorecon -R $D/shared_prefs $D/files; rm -rf /data/local/tmp/feeds_backup"
[ -n "$AF" ] && [ "$AF" != null ] && sh_ settings put secure autofill_service "$AF"
check "the user's autofill service is back ($AF)" '[ "$(sh_ settings get secure autofill_service)" = "$AF" ]'
check "the user's feeds are back as they were ($OWN)" '[ "$(feeds | grep -c "url=")" = "$OWN" ]'
home

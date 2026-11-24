# 2026-11-24 (user): Feeds follows the owner's feeds shared by Cloud Companion (its Feeds, Share: public or private with a token
# in the address, as catalogues are). Read only: the list is changed in Cloud Companion; read and starred stay on the phone.
# Never a live server: the fake share in data/feeds_sync_server.py through adb reverse. The user's Feeds data is restored.
FEEDS=com.bompotis.etesian.feeds
D=/data/data/$FEEDS
PORT=8737
SRV_PY=/home/memphisx/android/lineage/vendor/etesian/tests/device/data/feeds_sync_server.py
B=http://127.0.0.1:$PORT
python3 $SRV_PY $PORT 2>${SRV_LOG:-/dev/null} & SRV=$!
adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_desc() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
hold_text() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; }
fill() { tap_text "$1" >/dev/null; sleep 0.4; sh_ input text "$(printf %s "$2" | sed 's/ /%s/g')"; sleep 0.3; }
items() { sh_ content query --uri content://$FEEDS/items; }
feeds() { sh_ content query --uri content://$FEEDS/feeds; }
main() { sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1.5; }
refresh() { main; tap_desc "Refresh"; sleep 4; }
follow() {
  feeds_settings; tap_text "Sync" >/dev/null; sleep 1; tap_text "Cloud Companion" >/dev/null; sleep 1
  check "Cloud Companion asks only for the feeds address" '! dump | grep -q "text=\"Password\"" && sh_ cat /sdcard/u.xml | grep -q "Feeds address from Cloud Companion"'
  fill "Feeds address from Cloud Companion" "$1"; sh_ input keyevent BACK; sleep 0.5; tap_text "Sign in" >/dev/null; sleep 4
}

sh_ "am force-stop $FEEDS; rm -rf /data/local/tmp/feeds_backup; mkdir -p /data/local/tmp/feeds_backup; cp -a $D/shared_prefs $D/files /data/local/tmp/feeds_backup/ 2>/dev/null"
AF=$(sh_ settings get secure autofill_service); sh_ settings put secure autofill_service null
OWN=$(feeds | grep -c "url=")
unlock; home

follow "$B/feeds/index.json"
check "a private share's plain address is refused" '! sh_ cat $D/shared_prefs/sync.xml 2>/dev/null | grep -q "name=\"service\""'
sh_ input keyevent BACK; sleep 0.5
follow "$B/feeds/cc-token/index.json"
check "the address with its token is followed" 'sh_ cat $D/shared_prefs/sync.xml | grep -q "name=\"service\">cloud<"'
for _ in 1 2 3 4 5 6; do items | grep -q "Sync first post" && break; sleep 1; done
check "the owner's feeds are the list" '[ "$(feeds | grep -c "url=")" = 2 ] && feeds | grep -q "Example News"'
check "with their posts" 'items | grep -q "title=Sync first post" && items | grep -q "title=Sync starred post"'
shot feeds_cloud

ID=$(items | grep "title=Sync first post" | sed 's/.*, id=//; s/, read=.*//')
sh_ am start -n $FEEDS/.OpenActivity --es feed_item "'$ID'" >/dev/null; sleep 2.5; sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1
refresh
check "read here stays read after a refresh (kept on the phone)" 'items | grep "title=Sync first post" | grep -q "read=1"'

tap_desc "Add feed"; sleep 1; fill "Feed or website address" "https://added.example/rss"; sh_ input keyevent BACK; sleep 0.5
A logcat -c; tap_text "Add" >/dev/null; sleep 3
check "adding is left to Cloud Companion's owner" '! curl -s $B/_state | grep -q "added.example" && ! feeds | grep -q "added.example"'

main; hold_text "Example Blog"; sleep 1; tap_text "Remove" >/dev/null; sleep 1
check "removing offers no server box (the owner's list)" '! dump | grep -q "Also remove it from the server" && sh_ cat /sdcard/u.xml | grep -q "stops syncing it"'
tap_text "Remove" >/dev/null; sleep 2
refresh
check "removed here, and a refresh leaves it out" '! feeds | grep -q "Example Blog" && curl -s $B/_state | grep -q "blog.example"'

feeds_settings
check "Etesian settings shows the account under Connections" 'dump | grep -q "text=\"Connections\"" && sh_ cat /sdcard/u.xml | grep -q "Cloud Companion · 127.0.0.1"'
tap_text "Sync" >/dev/null; sleep 1
check "the account says read and starred stay on the phone" 'dump | grep -q "stays on this phone"'
tap_text "Sign out" >/dev/null; sleep 3
check "signed out, the phone's own feeds are back ($OWN)" '[ "$(feeds | grep -c "url=")" = "$OWN" ] && ! feeds | grep -q "Example News"'

# The app's menu no longer holds the connections; Settings there opens this page (2026-11-24, user).
main; tap_desc "Menu"; sleep 1
check "the Feeds menu has Find feeds and Settings, no account or Linkwarden" 'dump | grep -q "text=\"Settings\"" && sh_ cat /sdcard/u.xml | grep -q "text=\"Find feeds\"" && ! sh_ cat /sdcard/u.xml | grep -qE "Sync with an account|Linkwarden|Account:"'
tap_text "Settings" >/dev/null; sleep 2
check "Settings opens Etesian settings at Feeds" 'top_activity | grep -q "$LAUNCHER/.SettingsActivity" && dump | grep -q "text=\"Connections\""'
sh_ input keyevent BACK; sleep 1
check "Feeds did not crash" '! crashed $FEEDS'
adb reverse --remove tcp:$PORT >/dev/null; kill $SRV; wait $SRV 2>/dev/null
[ -n "$AF" ] && [ "$AF" != null ] && sh_ settings put secure autofill_service "$AF"
check "the user's autofill service is back ($AF)" '[ "$(sh_ settings get secure autofill_service)" = "$AF" ]'
u=$(sh_ stat -c %u $D)
sh_ "am force-stop $FEEDS; rm -rf $D/shared_prefs $D/files; cp -a /data/local/tmp/feeds_backup/shared_prefs /data/local/tmp/feeds_backup/files $D/ 2>/dev/null; chown -R $u:$u $D/shared_prefs $D/files; restorecon -R $D/shared_prefs $D/files; rm -rf /data/local/tmp/feeds_backup"
check "the user's feeds are back as they were ($OWN)" '[ "$(feeds | grep -c "url=")" = "$OWN" ]'
home

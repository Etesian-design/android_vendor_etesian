# 2026-11-24 (user): Feeds connects to the user's Linkwarden: an article can be saved to it, and once Linkwarden has
# preserved it, the reader reads its readable copy and its PDF from there. Never a live server: the fake one in
# data/feeds_sync_server.py through adb reverse. The user's Feeds data and autofill are restored at the end.
FEEDS=com.bompotis.etesian.feeds
D=/data/data/$FEEDS
PORT=8735
SRV_PY=/home/memphisx/android/lineage/vendor/etesian/tests/device/data/feeds_sync_server.py
B=http://127.0.0.1:$PORT
python3 $SRV_PY $PORT & SRV=$!
adb reverse tcp:$PORT tcp:$PORT >/dev/null; sleep 1
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_desc() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
fill() { tap_text "$1" >/dev/null; sleep 0.4; sh_ input text "$(printf %s "$2" | sed 's/ /%s/g')"; sleep 0.3; }
items() { sh_ content query --uri content://$FEEDS/items; }
connect() {
  sh_ am start -W -n $FEEDS/.MainActivity >/dev/null; sleep 1.5
  tap_desc "Menu"; sleep 1; tap_text "Connect Linkwarden" >/dev/null; sleep 1
  fill "Server address" "$B"; fill "Access token" "$1"
  sh_ input keyevent BACK; sleep 0.5; tap_text "Connect" >/dev/null; sleep 3
}
open_item() { sh_ am start -n $FEEDS/.OpenActivity --es feed_item "'$1'" >/dev/null; sleep 3; }

sh_ "am force-stop $FEEDS; rm -rf /data/local/tmp/feeds_backup; mkdir -p /data/local/tmp/feeds_backup; cp -a $D/shared_prefs $D/files /data/local/tmp/feeds_backup/ 2>/dev/null"
AF=$(sh_ settings get secure autofill_service); sh_ settings put secure autofill_service null
unlock; home

ROWS=$(items | grep "link=http")
ID_A=$(echo "$ROWS" | sed -n 1p | sed 's/.*, id=//; s/, read=.*//'); LINK_A=$(echo "$ROWS" | sed -n 1p | sed 's/.*, link=//; s/, [a-zA-Z]*=.*//')
ID_B=$(echo "$ROWS" | sed -n 2p | sed 's/.*, id=//; s/, read=.*//'); LINK_B=$(echo "$ROWS" | sed -n 2p | sed 's/.*, link=//; s/, [a-zA-Z]*=.*//')
check "two articles to use" '[ -n "$ID_A" ] && [ -n "$ID_B" ] && [ "$LINK_A" != "$LINK_B" ]'
curl -s "$B/_lw_add?url=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$LINK_A")" >/dev/null

connect "wrong-token"
check "a wrong access token is refused" '! sh_ ls $D/shared_prefs | grep -q "^linkwarden.xml" || ! sh_ cat $D/shared_prefs/linkwarden.xml | grep -q "name=\"token\""'
connect "lw-token"
check "connected with the right one" 'sh_ cat $D/shared_prefs/linkwarden.xml | grep -q "name=\"token\""'

# A preserved article: readable copy and PDF from Linkwarden.
open_item "$ID_A"
tap_desc "Readable version"; sleep 3
check "the readable version is Linkwarden's copy" 'dump | grep -q "LINKWARDEN-COPY" && sh_ cat /sdcard/u.xml | grep -q "content-desc=\"readable:linkwarden\""'
shot feeds_linkwarden_readable
A logcat -c
tap_desc "Menu"; sleep 1
check "the menu offers its PDF, not saving it again" 'dump | grep -q "text=\"Open PDF from Linkwarden\"" && ! sh_ cat /sdcard/u.xml | grep -q "text=\"Save to Linkwarden\""'
tap_text "Open PDF from Linkwarden" >/dev/null; sleep 3
check "the PDF is fetched from Linkwarden" 'A logcat -d -s Etesian | grep -qE "pdf 1.pdf [0-9]+"'
check "and is a PDF" 'sh_ head -c 5 $D/cache/linkwarden/1.pdf | grep -q "%PDF"'
check "and handed to a PDF viewer (or the user is told there is none)" '! top_activity | grep -q "$FEEDS/.ReaderActivity" || A logcat -d | grep -q "No app on this phone opens PDFs"'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1

# An article not in Linkwarden: save it.
open_item "$ID_B"
tap_desc "Menu"; sleep 1
check "an article not saved yet offers Save to Linkwarden" 'dump | grep -q "text=\"Save to Linkwarden\"" && ! sh_ cat /sdcard/u.xml | grep -q "text=\"Open PDF from Linkwarden\""'
tap_text "Save to Linkwarden" >/dev/null; sleep 3
check "saving it sends it to Linkwarden" 'curl -s $B/_state | grep -qF "$LINK_B"'
sh_ input keyevent BACK; sleep 1

check "Feeds did not crash" '! crashed $FEEDS'
adb reverse --remove tcp:$PORT >/dev/null; kill $SRV; wait $SRV 2>/dev/null
[ -n "$AF" ] && [ "$AF" != null ] && sh_ settings put secure autofill_service "$AF"
check "the user's autofill service is back ($AF)" '[ "$(sh_ settings get secure autofill_service)" = "$AF" ]'
u=$(sh_ stat -c %u $D)
sh_ "am force-stop $FEEDS; rm -rf $D/shared_prefs $D/files $D/cache/linkwarden; cp -a /data/local/tmp/feeds_backup/shared_prefs /data/local/tmp/feeds_backup/files $D/ 2>/dev/null; chown -R $u:$u $D/shared_prefs $D/files; restorecon -R $D/shared_prefs $D/files; rm -rf /data/local/tmp/feeds_backup"
check "Linkwarden is not left connected" '! sh_ ls $D/shared_prefs | grep -q "^linkwarden.xml"'
home

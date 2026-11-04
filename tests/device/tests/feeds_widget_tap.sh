# Bug 2026-11-04 (user, again): a tap on a Feeds widget entry opened the posts list, not the article.
# The widget list's click template carried empty item extras and Intent.fillIn never replaces extras
# the template already has, so every row opened item "". The old test started OpenActivity with am
# instead of tapping the widget: this one taps a real row on the home screen.
FEEDS=com.bompotis.etesian.feeds
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
row() { dump | tr '>' '\n' | grep -E 'resource-id="com.bompotis.etesian.launcher:id/(bc|belle)_item"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '; }
unlock; home
r=""; for _ in 1 2 3 4 5; do r=$(row); [ -n "$r" ] && break; sh_ input swipe 900 1100 150 1100 300; sleep 1.5; done
if [ -z "$r" ]; then bad "a Feeds widget with entries on the home screen"; else
    set -- $r
    sh_ input tap $((($1 + $3) / 2)) $((($2 + $4) / 2)); sleep 4
    check "widget entry opens the article ($(top_activity))" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
    sh_ input keyevent BACK; sleep 2
    check "back from it goes to that site's posts ($(top_activity))" 'top_activity | grep -q "$FEEDS/.PostsActivity"'
fi
check "Feeds did not crash" '! crashed $FEEDS'
home

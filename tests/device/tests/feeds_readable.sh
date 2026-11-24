# 2026-11-24 (user): in an article, a toolbar button shows the readable version of the article: the page itself with
# only its article kept (Readability.js), in the same reader page; again, the feed's own text. Never the live site: the
# page is a fake put where Readable looks for test pages (files/test-pages/<sha1 of the link>.html).
FEEDS=com.bompotis.etesian.feeds
FP=/data/data/$FEEDS/shared_prefs/feeds.xml
TP=/data/data/$FEEDS/files/test-pages
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# Read state is the user's: put it back afterwards.
sh_ "cp $FP /data/local/tmp/feeds_prefs.bak"
unlock; home
ROW=$(sh_ content query --uri content://$FEEDS/items | grep "link=http" | head -1)
ID=$(echo "$ROW" | sed 's/.*, id=//; s/, read=.*//')
LINK=$(echo "$ROW" | sed 's/.*, link=//; s/, [a-zA-Z]*=.*//')
check "an item with a link to read ($LINK)" '[ -n "$ID" ] && [ -n "$LINK" ]'
KEY=$(printf %s "$LINK" | sha1sum | cut -d" " -f1)
PAGE=$(mktemp)
cat > "$PAGE" <<'EOF'
<!doctype html><html><head><title>Mock article</title></head><body>
<nav><a href="/">Home</a> <a href="/news">News</a> MENU-JUNK-NAV</nav>
<aside class="sidebar">SIDEBAR-JUNK Subscribe now! Popular stories.</aside>
<article><h1>Mock article</h1>
<p>READABLE-FIRST The first paragraph of the mocked article is long enough for Readability to take it as the body of
the page, with commas, sentences, and the kind of length a real article has, so that its scoring keeps it.</p>
<p>The second paragraph goes on in the same way, adding more words, more commas, and more sentences, so that the
article's container clearly wins over the navigation, the sidebar, and the footer around it on this page.</p>
<p>READABLE-LAST A third paragraph closes the article, again long, plain and written, as articles are, so nothing about
it looks like a menu, a list of links, or an advertisement to the readability scorer.</p>
</article>
<footer>FOOTER-JUNK Copyright, cookies, terms.</footer>
</body></html>
EOF
sh_ "mkdir -p $TP"; A push "$PAGE" /data/local/tmp/readable.html >/dev/null; rm -f "$PAGE"
u=$(sh_ stat -c %u /data/data/$FEEDS)
sh_ "cp /data/local/tmp/readable.html $TP/$KEY.html; rm /data/local/tmp/readable.html; chown -R $u:$u $TP; restorecon -R $TP"
sh_ "rm -rf /data/data/$FEEDS/cache/readable"
sh_ am force-stop $FEEDS
sh_ am start -n $FEEDS/.OpenActivity --es feed_item "'$ID'" >/dev/null; sleep 2.5
check "the reader is open" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
check "its toolbar has Readable version" 'dump | grep -q "content-desc=\"Readable version\""'
FEEDTXT=$(sh_ cat /sdcard/u.xml | grep -c "READABLE-FIRST")
tap_desc() { dump >/dev/null; set -- $(sh_ cat /sdcard/u.xml | sed 's/<node/\n<node/g' | grep -F "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+'); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
tap_desc "Readable version"; sleep 3
shot feeds_readable
dump >/dev/null
check "the readable version shows the article" 'sh_ cat /sdcard/u.xml | grep -q "READABLE-FIRST" && sh_ cat /sdcard/u.xml | grep -q "READABLE-LAST"'
check "without the page's menu, sidebar or footer" '! sh_ cat /sdcard/u.xml | grep -qE "MENU-JUNK|SIDEBAR-JUNK|FOOTER-JUNK"'
check "and stays in the reader" 'top_activity | grep -q "$FEEDS/.ReaderActivity"'
check "it is kept for offline reading" 'sh_ ls /data/data/$FEEDS/cache/readable | grep -q .'
tap_desc "Readable version"; sleep 1.5
check "again: the feed's own text is back" '[ "$(dump | grep -c "READABLE-FIRST")" = "$FEEDTXT" ] && ! sh_ cat /sdcard/u.xml | grep -q "content-desc=\"readable\""'
sh_ input keyevent BACK; sleep 1
sh_ "am force-stop $FEEDS; cat /data/local/tmp/feeds_prefs.bak > $FP; rm -f /data/local/tmp/feeds_prefs.bak; rm -rf $TP /data/data/$FEEDS/cache/readable"
check "Feeds did not crash" '! crashed $FEEDS'
home

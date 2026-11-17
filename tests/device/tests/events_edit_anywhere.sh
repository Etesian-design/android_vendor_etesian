# Events edit mode from anywhere: a long press on a section header (not only on a notification) enters it; in
# edit mode a tap on a feed post gives its menu (Mark all as read, Feed settings) instead of opening it, and an
# app's group gives the same N9 menu as a notification. Show under… is an N9 menu, not an Android dialog.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind ${1:-message} >/dev/null; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
hold() { set -- $(bounds "$1"); [ -n "$4" ] && sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; }
press() { set -- $(bounds "$1"); [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
editing() { dump | grep -q "Swipe entries away"; }
events_pane() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done; for _ in 1 2 3 4 5; do sh_ input swipe 540 900 540 1900 150; sh_ cmd statusbar collapse; done; sleep 1; }
sh_ pm grant $LAUNCHER android.permission.POST_NOTIFICATIONS 2>/dev/null
old=$(sh_ settings get secure etesian_extra_ghost_rows); sh_ settings put secure etesian_extra_ghost_rows 0
unlock; post cancel; post; sleep 1.5
events_pane
check "Events pane" '[ "$(home_pane)" = events ]'
check "not in edit mode" '! editing'
hold 'text="Notifications"'; sleep 1
check "long press on the Notifications header: edit mode" 'editing'
check "the release did not also tap the header" '! dump | grep -q "content-desc=\"Events actions\""'
press 'text="Anna Virtanen"'; sleep 1.2
check "tap in edit mode: the N9 menu" 'dump | grep -q "content-desc=\"Events actions\""'
tap_text "Show under…"; sleep 1.2
check "Show under: an N9 menu" 'dump | grep -q "content-desc=\"Show under\""'
check "Show under: not an Android dialog" '! dump | grep -q "resource-id=\"android:id/select_dialog_listview\"\|resource-id=\"android:id/alertTitle\""'
check "Show under: no Hidden choice" '! dump | grep -q "text=\"Hidden\""'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 1
if dump | grep -q 'resource-id="com.bompotis.etesian.launcher:id/feed_post"'; then
  events_pane
  hold 'resource-id="com.bompotis.etesian.launcher:id/feed_post"'; sleep 1
  check "long press on a feed post: edit mode" 'editing'
  check "the feed post did not open" '[ "$(home_pane)" = events ]'
  # The first post's headline: Events refills from below, so a swiped post shows as the first headline changing.
  first() { dump | python3 -c "
import sys,re; x=sys.stdin.read()
m=re.search(r'resource-id=\"com.bompotis.etesian.launcher:id/feed_post\"[^>]*>(.*?)</node>\s*</node>', x, re.S)
t=re.findall(r'text=\"([^\"]+)\"', m.group(1)) if m else []
print(t[1] if len(t) > 1 else '')"; }
  f0=$(first)
  set -- $(bounds 'resource-id="com.bompotis.etesian.launcher:id/feed_post"')
  sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( $3 - 10 )) $(( ($2+$4)/2 )) 200; sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( $3 )) $(( ($2+$4)/2 )) 150; sleep 2
  check "a swiped feed post leaves Events" '[ -n "$f0" ] && [ "$(first)" != "$f0" ] && ! dump | grep -qF "$f0"'
  press 'resource-id="com.bompotis.etesian.launcher:id/feed_post"'; sleep 1.2
  check "feed post in edit mode: its menu" 'dump | grep -q "content-desc=\"Feed actions\""'
  check "menu: Mark all as read" 'dump | grep -q "text=\"Mark all as read\""'
  check "menu: Feed settings" 'dump | grep -q "text=\"Feed settings\""'
  tap_text "Feed settings"; sleep 2.5
  check "Feed settings page" 'dump | grep -q "text=\"Update feeds\""'
  check "lists notifications shown as feeds" 'dump | grep -q "Notifications shown as feeds\|text=\"None\""'
  sh_ input keyevent BACK; sleep 1
else echo "  skip feed post checks (no feed posts in Events)"; fi
post cancel
[ "$old" = null ] && sh_ settings delete secure etesian_extra_ghost_rows >/dev/null || sh_ settings put secure etesian_extra_ghost_rows "$old"
check "launcher did not crash" '! crashed $LAUNCHER'
home

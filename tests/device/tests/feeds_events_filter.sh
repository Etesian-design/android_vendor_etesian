# 2026-11-23 (user): as on the N9, Feed settings (Events → long press → a feed post → Feed settings) say which of the
# followed sites show in Events; the others stay in the Feeds app.
unlock; home
FEEDS=com.bompotis.etesian.feeds
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
sh_ cp $P /data/local/tmp/ff_prefs.bak
events() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done; sleep 1; }
first=$(sh_ content query --uri content://$FEEDS/feeds | head -1 | sed 's/.*title=//; s/, .*//')
check "a followed feed to hide ($first)" '[ -n "$first" ]'
# The launcher reads the feeds from the Feeds app on its first look: the page may list them a moment later.
for _ in 1 2 3 4; do
  sh_ am start -W --activity-clear-task -n $LAUNCHER/.SettingsActivity --es section feeds >/dev/null; sleep 2
  sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  row=$(sh_ cat /sdcard/u.xml | tr '>' '\n' | awk -v t="text=\"$first\"" 'index($0,t){f=1} f&&/switchWidget/{print; exit}')
  [ -n "$row" ] && break; sleep 2
done
check "Feed settings list the feeds shown in Events" 'sh_ cat /sdcard/u.xml | grep -q "text=\"Feeds shown in Events\""'
check "its switch is on" 'echo "$row" | grep -q "checked=\"true\""'
set -- $(echo "$row" | grep -o 'bounds="[^"]*"' | tr -cs '0-9' ' '); sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
# Saved in the background (SharedPreferences.apply): give the file a moment.
for _ in 1 2 3 4 5 6; do sh_ cat $P | grep -A3 "events_hidden_feeds" | grep -q "<string>" && break; sleep 1; done
check "turning it off is saved" 'sh_ cat $P | grep -A3 "events_hidden_feeds" | grep -q "<string>"'
events
check "Events no longer shows its posts" '! sh_ cat /sdcard/u.xml | grep -qF "text=\"$first\""'
shot feeds_filtered
sh_ am force-stop $LAUNCHER; sh_ "cat /data/local/tmp/ff_prefs.bak > $P"; sh_ rm -f /data/local/tmp/ff_prefs.bak
events
check "launcher did not crash" '! crashed $LAUNCHER'
home

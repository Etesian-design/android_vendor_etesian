# Bug 2026-10-25 #14 (second batch): every Belle replica is offered as a widget; the word clock
# spells the time; Notes keeps a note written with + and deletes it with the bin.
unlock
providers=$(sh_ dumpsys appwidget | grep -o 'etesian.launcher/com.bompotis.etesian.launcher.Belle[A-Za-z0-9]*' | sort -u)
for w in Switch1 DataUsage Notes Agenda BigClock GearClock LcdClock WordClock Web WeatherClassic MusicClassic Rss FmSwitch; do
  check "provider Belle${w}Widget registered" 'echo "$providers" | grep -q "\.Belle${w}Widget$"'
done
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
down() { for _ in 1 2 3 4 5 6; do sh_ input swipe 540 1800 540 400 200; done; sleep 1; }
# Small widgets fill the first gap that fits, so they can land anywhere: look from the top down.
seek() { for _ in 1 2 3 4 5 6 7 8; do sh_ input swipe 540 600 540 1800 150; done; sleep 1
  for _ in 1 2 3 4 5 6 7 8 9 10; do dump | grep -q "$1" && return 0; sh_ input swipe 540 1500 540 900 300; sleep 0.8; done; return 1; }
wp=$(pref_switch home Widgets 1)  # the Widgets view is off by default: on for the test, restored at the end
widgets_pane; down
dump | grep -q 'O.Clock\|Oh \|ty\b\|teen' || add_widget "Word clock (Belle classic)"
down
ones="One Two Three Four Five Six Seven Eight Nine Ten Eleven Twelve"
h=$(sh_ date +%I | sed 's/^0//'); word=$(echo $ones | cut -d' ' -f$h); next=$(echo $ones | cut -d' ' -f$(( h % 12 + 1 )))
# late in the hour it reads "Five to <next hour>", so either hour is right
check "word clock shows the hour ($word or $next)" 'seek_widget "text=\"$word\"" || seek_widget "text=\"$next\""'
dump | grep -q 'content-desc="New note"' || add_widget "Notes (Belle classic)"; sleep 2
down
note="Belle note $$"
tap_desc() { b=$(dump | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   '); set -- $b
  sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
tap_desc "New note"; sleep 2
sh_ input text "$(echo "$note" | sed 's/ /%s/g')"; sleep 0.5
tap_text "Save"; sleep 2; down
check "Notes shows the new note" 'dump | grep -q "text=\"$note\""'
tap_desc "New note"; sleep 2
check "a new note starts blank" '! dump | grep -q "text=\"$note\""'
sh_ input keyevent BACK; sleep 1; sh_ input keyevent BACK; sleep 2; down
tap_desc "Delete note"; sleep 2
check "the bin deletes it" '! dump | grep -q "text=\"$note\""'
# Per-site feed: pick the first subscription; the header shows it and its posts are listed.
feed=$(sh_ content query --uri content://com.bompotis.etesian.feeds/feeds 2>/dev/null | grep -o 'title=[^,]*' | head -1 | cut -d= -f2)
if [ -n "$feed" ]; then
  site="text=\"$feed\" resource-id=\"com.bompotis.etesian.launcher:id/bc_site\""
  seek "$site" || { add_widget "Feed (Belle classic)"; tap_text "$feed"; sleep 3; }
  check "feed widget shows $feed" 'seek "$site"'
  check "and its posts" 'dump | grep -o "<node[^>]*bc_title\"[^>]*>" | grep -vq "text=\"\""'
  # Regression 2026-10-30: four fixed posts; Belle's feed widget scrolls through up to 20.
  titles() { dump | grep -o "<node[^>]*resource-id=\"com.bompotis.etesian.launcher:id/bc_title\"[^>]*>" | grep -o 'text="[^"]*"'; }
  l=$(dump | grep -o "<node[^>]*resource-id=\"com.bompotis.etesian.launcher:id/bc_items\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $l; lx=$(( (${1:-0} + ${3:-0}) / 2 )); ltop=${2:-0}; lbot=${4:-0}
  # the list keeps its position: back to the top first, then one swipe up must bring in posts not shown before
  [ "$lbot" -gt 0 ] && for _ in 1 2 3 4 5; do sh_ input swipe $lx $(( ltop + 30 )) $lx $(( lbot - 30 )) 150; done; sleep 1
  first=$(titles | sort)
  [ "$lbot" -gt 0 ] && { sh_ input swipe $lx $(( lbot - 30 )) $lx $(( ltop + 30 )) 400; sleep 1; }
  check "swiping the feed list shows more posts" '[ "$lbot" -gt 0 ] && [ -n "$(comm -13 <(echo "$first") <(titles | sort))" ]'
fi
# FM and 3G switches show their state (not tapped: FM is audible, 2G-only could drop the connection).
seek 'content-desc="FM radio o[nf]*"' || add_widget "FM Radio switch (Belle classic)"
down
check "FM switch shows its state" 'seek "content-desc=\"FM radio o[nf]*\""'
seek 'content-desc="3G o[nf]*"' || { add_widget "Switch (Belle classic)"; tap_text "3G"; sleep 3; }
down
check "3G switch shows its state" 'seek "content-desc=\"3G o[nf]*\""'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home
pref_switch home Widgets "$wp" >/dev/null; home

# 2026-11-23 (user): on the home screen's Glance page the Refresh and Settings buttons did nothing (Glance's settings
# were not open to other apps), and a refresh showed nothing. Settings opens Glance's settings; Refresh starts a
# refresh, and while Glance fetches, the N9 loading arc runs round the button's squircle ("Refreshing").
unlock
GL=com.bompotis.etesian.glance
dumpn() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_desc() { set -- $(dumpn | tr '>' '\n' | grep "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | tr -cs '0-9' ' ')
  [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
news() { home; for _ in 1 2 3 4 5 6 7; do [ "$(home_pane)" = news ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.2; done; sleep 1; }
np=$(pref_switch home News 1)
news
check "the News view is there ($(home_pane))" '[ "$(home_pane)" = news ]'
tap_desc Settings; sleep 2
check "Settings opens Glance's settings ($(top_activity))" 'top_activity | grep -q "^$GL/.SettingsActivity"'
sh_ input keyevent BACK; sleep 1
news
sh_ logcat -c
tap_desc Refresh; sleep 0.4; x=$(dumpn)
check "Refresh starts a refresh" 'command adb logcat -d -s EtesianNews:I | grep -q "refresh: button"'
check "the button shows it is refreshing" 'echo "$x" | grep -q "content-desc=\"Refreshing\""'
shot news_refreshing
# Glance says when it is done; the arc goes (60 s at most).
for _ in $(seq 1 30); do dumpn | grep -q 'content-desc="Refreshing"' || break; sleep 2; done
check "the arc goes when Glance is done" '! dumpn | grep -q "content-desc=\"Refreshing\""'
pref_switch home News "$np" >/dev/null
check "launcher did not crash" '! crashed $LAUNCHER'
check "Glance did not crash" '! crashed $GL'
home

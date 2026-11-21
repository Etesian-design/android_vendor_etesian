# 2026-11-20 (user): with Glance as a home screen view, "when tapping the search bar, the actual glance app opens
# instead of getting the keyboard to type and get any results ... the search bar should not do that" (opening an
# article in Glance's reader is fine). The News view searches in place: the keyboard comes up, results replace the
# stories, Back brings the stories back.
unlock
G=com.bompotis.etesian.glance
dumpn() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
np=$(pref_switch home News 1)
home; for _ in 1 2 3 4 5 6 7; do [ "$(home_pane)" = news ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.2; done; sleep 2
check "the News view is there ($(home_pane))" '[ "$(home_pane)" = news ]'
b=$(dumpn | grep -o 'content-desc="news:search"[^>]*' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
set -- $b; command adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5
check "tapping the search bar stays on the launcher ($(top_activity))" '! top_activity | grep -q "$G/"'
check "the keyboard comes up" 'sh_ dumpsys input_method | grep -q "mInputShown=true"'
command adb shell input text wifi; sleep 3
shot news_search_results
u=$(dumpn)
check "results show in place of the stories" 'echo "$u" | grep -q "content-desc=\"glance:setting:"'
check "the stories are gone while searching" '! echo "$u" | grep -q "content-desc=\"story:"'
sh_ input keyevent BACK; sleep 0.8; sh_ input keyevent BACK; sleep 1.5
u=$(dumpn)
check "Back leaves the search: the stories again" 'echo "$u" | grep -q "content-desc=\"story:\|Getting the news"'
check "and still on the News view ($(home_pane))" '[ "$(home_pane)" = news ]'
pref_switch home News "$np" >/dev/null
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home

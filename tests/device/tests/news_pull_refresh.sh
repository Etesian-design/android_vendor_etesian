# 2026-11-22 (user): the home screen's Glance page refreshes by pulling down (N9 style text, no Android circle), and
# has Refresh and Settings beside its search bar, as the Glance app's story list (which lost its bottom bar).
unlock
dumpn() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
top_of() { dumpn | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*' | grep -o '[0-9]*$'; }
np=$(pref_switch home News 1)
home; for _ in 1 2 3 4 5 6 7; do [ "$(home_pane)" = news ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.2; done; sleep 2
check "the News view is there ($(home_pane))" '[ "$(home_pane)" = news ]'
r=$(top_of Refresh); s=$(top_of Settings)
check "Refresh and Settings beside the search bar ($r, $s)" '[ -n "$r" ] && [ -n "$s" ] && [ "$r" -lt 400 ] && [ "$s" -lt 400 ]'
# A pull only starts with the stories at their top (an earlier visit may have left them scrolled): scroll up first.
for _ in 1 2 3; do sh_ input swipe 540 700 540 1900 150; sleep 0.6; done; sleep 1.5
command adb logcat -c
sh_ input swipe 540 800 540 1700 900; sleep 1
check "pulling the stories down refreshes" 'command adb logcat -d -s EtesianNews:I | grep -q "refresh: pulled"'
command adb logcat -c
sh_ input swipe 900 1200 150 1300 250; sleep 1.5
check "a sideways swipe is not a pull" '! command adb logcat -d -s EtesianNews:I | grep -q "refresh: pulled"'
shot news_pull
pref_switch home News "$np" >/dev/null
check "launcher did not crash" '! crashed $LAUNCHER'
home

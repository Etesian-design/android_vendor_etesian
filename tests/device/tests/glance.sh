# User 2026-11-18: Glance, a separate app mixing Google News and Sesame: one screen, a search bar on top that finds
# everything on the phone (apps, shortcuts, contacts, messages, notifications, files, settings, the web), the news
# under it (Google News without the app or ads, mixed with the Feeds app's subscriptions, ranked on the phone). The
# launcher shows its news as a view (like the Widgets views) and its results in the Applications search.
G=com.bompotis.etesian.glance
dumpx() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# Taps the node whose content-desc starts with [desc] (mapped coordinates, from the dump).
tap_desc() { local b; b=$(dumpx | grep -o "content-desc=\"$1[^\"]*\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
  [ -n "$b" ] || return 1; set -- $b; sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
glance_search() { sh_ am start -W -n $G/.MainActivity --ez focus_search true >/dev/null; sleep 1.5
  tap_desc "Search your phone" || true; sleep 0.8
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do sh_ input keyevent DEL; done; sh_ input text "$1"; sleep 3; }
unlock

check "Glance is built into the ROM ($(sh_ pm path $G))" 'sh_ pm path $G | grep -q "/system_ext/"'
P=$(sh_ dumpsys package $G)
check "app shortcuts like a launcher (ACCESS_SHORTCUTS, platform key)" 'echo "$P" | grep -q "android.permission.ACCESS_SHORTCUTS: granted=true"'
check "it reads the Feeds app's subscriptions" 'echo "$P" | grep -q "com.bompotis.etesian.feeds.permission.READ: granted=true"'
check "contacts granted with the ROM" 'echo "$P" | grep -q "android.permission.READ_CONTACTS: granted=true"'
check "the launcher may read Glance" 'sh_ dumpsys package $LAUNCHER | grep -q "com.bompotis.etesian.glance.permission.READ: granted=true"'

# The news: Google News' top stories, then picked for you.
sh_ am force-stop $G; sh_ am start -W -n $G/.MainActivity >/dev/null
for _ in $(seq 30); do [ "$(dumpx | grep -o 'content-desc="story:' | wc -l)" -ge 2 ] && break; sleep 2; done
shot glance_news
n=$(dumpx | grep -o 'content-desc="story:' | wc -l)
check "the news shows ($n stories on screen, pictures make them tall)" '[ "$n" -ge 2 ]'
# User 2026-11-19: every story has a picture, as in Google News (Google's feeds carry none: publishers' do).
Q=$(sh_ content query --uri content://$G/news --projection image)
pics=$(echo "$Q" | grep -c "image=http"); all=$(echo "$Q" | grep -c "^Row")
# Live news: now and then a publisher's page has no picture (Glance draws its colour instead).
check "every story has a picture, but for the odd publisher with none ($pics of $all)" '[ "$all" -ge 20 ] && [ $((pics * 10)) -ge $((all * 9)) ]'
# The stories with no picture, named (source: title), so a publisher whose pages carry no og:image shows up here.
nopic=$(sh_ content query --uri content://$G/news --projection source:title:image | grep "image=$" | sed -E 's/.*source=([^,]*), title=(.{0,60}).*/\1: \2/')
[ -n "$nopic" ] && echo "$nopic" | sed 's/^/       no picture: /'
check "stories with no picture are few and reported above ($(echo "$nopic" | grep -c .))" '[ $(( $(echo "$nopic" | grep -c .) * 10 )) -le "$all" ]'
u=$(echo "$Q" | grep -o "image=http[^,]*" | head -1 | cut -d= -f2-)
check "and it loads ($(curl -s -o /dev/null -w "%{http_code} %{content_type}" "$u"))" 'curl -s -o /dev/null -w "%{content_type}" "$u" | grep -q "^image/"'

# User 2026-11-19: stories open in Glance's own reader (Readability, like Firefox's Reader View), the browser a tap away.
# The reader logs what it shows; its WebView's label is a fallback (a screen dump swaps it for the page's own).
reader_state() { local l; l=$(sh_ logcat -d -s GlanceReader:I | grep -o '\(article:[0-9]*\|page\|consent\)$' | tail -1)
  [ -n "$l" ] && echo "content-desc=\"reader:$l\"" || dumpx | grep -o 'content-desc="reader:[^"]*"' | head -1; }
read_story() {  # $1: which story on screen (1-based)
  sh_ am force-stop $G; sh_ logcat -c; sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 3
  [ "$1" -gt 1 ] && { sh_ input swipe 540 1700 540 700 400; sleep 1.5; }
  local b; b=$(dumpx | grep -o 'content-desc="story:[^"]*"[^>]*bounds="[^"]*"' | sed -n "${1}p" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  # A phone that never answered Google's EU consent page gets it once: answer it as a person would (Reject all).
  for _ in $(seq 35); do r=$(reader_state); case "$r" in *article*|*page*) break;; esac
    dumpx | grep -q 'Before you continue to Google' && for _ in 1 2 3 4 5; do sh_ input swipe 540 1700 540 400 300; sleep 1
      set -- $(dumpx | grep -o '\(text\|content-desc\)="Reject all"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tail -4 | tr '\n' ' ')
      [ -n "$4" ] && { sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3; break; }; done; sleep 1; done
  echo "$r"; }
arts=0; tried=""
for n in 1 2 3; do r=$(read_story $n); tried="$tried ${r#*reader:}"; shot reader_$n
  echo "$r" | grep -q "reader:article:" && n_=${r##*:}; n_=${n_%\"}; [ "$n_" -ge 1000 ] 2>/dev/null && arts=$((arts + 1)); done
check "stories open as articles in Glance's reader ($arts of 3:$tried)" '[ "$arts" -ge 2 ]'
tap_desc "Open in browser"; sleep 3
check "and Open in browser leaves for the browser ($(top_activity))" '! top_activity | grep -q "$G"'
sh_ input keyevent BACK; sleep 1

# User 2026-11-19: buttons on the bottom bar, never on top (N9; closer to the thumb). On another phone (no Etesian
# bar) Glance draws the same bar itself, at the bottom.
bottom_of() { dumpx | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\]\[[0-9]*,[0-9]*\]' | tr -dc '0-9,' | cut -d, -f2; }
H=$(adb shell wm size | grep -o '[0-9]*$' | tail -1)
sh_ am force-stop $G; sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 2.5
check "no Settings button on top any more" '! dumpx | grep -q "content-desc=\"Settings\"[^>]*bounds=\"\[[0-9]*,[0-9]\{1,3\}\]"'
bb=$(sh_ settings get secure etesian_bottom_bar)
sh_ settings put secure etesian_bottom_bar 0; sh_ am force-stop $G; sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 2.5
y=$(bottom_of Settings); shot glance_own_bar
check "without the Etesian bar Glance draws its own, at the bottom (Settings ends at $y of $H)" '[ -n "$y" ] && [ "$y" -gt $((H * 85 / 100)) ]'
sh_ settings put secure etesian_bottom_bar "${bb/null/1}"; sh_ am force-stop $G

# Notifications: the setup card's button switches Glance's access on itself (platform key), when the user asks.
L=$(sh_ settings get secure enabled_notification_listeners)
if ! echo "$L" | grep -q $G; then
  sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 1; sh_ input swipe 540 600 540 1800 200; sleep 1
  tap_desc "setup:notifications"; sleep 2
fi
check "notification access on" 'sh_ settings get secure enabled_notification_listeners | grep -q $G'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message --es title QuokkaSighting --es text Lakeside_at_seven >/dev/null  # no spaces: adb shell would split them; sleep 3

glance_search quokka; shot glance_search_quokka
check "a notification is found by its title" 'dumpx | grep -q "content-desc=\"result:[a-z]*:QuokkaSighting"'
glance_search lakeside
check "and by its text" 'dumpx | grep -q "content-desc=\"result:[a-z]*:QuokkaSighting"'
tap_desc "result:message:QuokkaSighting" || tap_desc "result:notification:QuokkaSighting"; sleep 2.5
check "tapping it opens the notification's own action (Etesian Settings: $(top_activity))" 'top_activity | grep -q "com.bompotis.etesian.launcher/.SettingsActivity"'

# A news result from search opens in the reader too, not the browser.
glance_search energy; tap_desc "result:news:" || tap_desc "result:feed:"; sleep 6
check "a news result opens in Glance's reader ($(top_activity))" 'top_activity | grep -q "$G/.ReaderActivity"'
sh_ input keyevent BACK; sleep 1

glance_search wifi; shot glance_search_wifi
check "Settings pages are found (Wi-Fi)" 'dumpx | grep -q "content-desc=\"result:setting:Wi-Fi"'
tap_desc "result:setting:Wi-Fi"; sleep 2.5
check "and open ($(top_activity))" 'top_activity | grep -q "com.android.settings"'
glance_search "set"
check "applications by name (Settings)" 'dumpx | grep -q "content-desc=\"result:app:Settings\""'
check "the web search is always offered" 'dumpx | grep -q "content-desc=\"result:web:Search the web for"'

# The launcher: Glance's results under the applications in the Applications search.
sp=$(pref_switch apps "Pull down to search" 1); se=$(pref_switch apps "Search everything" 1)
apps_pane
sh_ input swipe 540 300 540 2050 1200; sleep 1.5
sh_ input text quokka; sleep 4; shot launcher_search_quokka
check "the Applications search shows Glance's results" 'dumpx | grep -q "content-desc=\"glance:[a-z]*:QuokkaSighting"'
sh_ input keyevent BACK; sleep 0.5; sh_ input keyevent BACK; sleep 0.5

# The launcher: Glance's news as a view, furthest left.
np=$(pref_switch home News 1)
# The views wrap around: swipe until it shows.
home; for _ in 1 2 3 4 5 6 7; do [ "$(home_pane)" = news ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.2; done; sleep 3
shot launcher_news
check "the News view is there ($(home_pane))" '[ "$(home_pane)" = news ]'
check "it shows Glance's stories (pictures make them tall: two fit a short screen)" '[ "$(dumpx | grep -o "content-desc=\"story:" | wc -l)" -ge 2 ]'
sh_ input swipe 1000 1700 100 1700 300; sleep 1.5
check "with Widgets/Events on its right ($(home_pane))" '[ "$(home_pane)" != apps ]'

sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
pref_switch home News "$np" >/dev/null
pref_switch apps "Search everything" "$se" >/dev/null
pref_switch apps "Pull down to search" "$sp" >/dev/null
check "Glance did not crash" '! crashed $G'
check "launcher did not crash" '! crashed $LAUNCHER'
home

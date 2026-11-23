# 2026-11-22 (user): with nothing in it, a section is not there at all: no Notifications header over an empty gap, no Feed
# header with "No unread feed items". The gap was a real row left clipped to nothing when its slide-in animation was
# cut short (a notification posted while Events wasn't showing); it showed again only after a launcher restart.
unlock; home
events() { sh_ input keyevent HOME; sleep 1; for _ in 1 2 3 4; do [ "$(home_pane)" = events ] && break; sh_ input swipe 100 1200 1000 1200 300; sleep 1.2; done; }
texts() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'text="[^"]*"'; }
# Posted while Applications shows, then Events: the new row must be readable, not a blank space.
post() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind person --ei id $1 --es title "Etesian_test_$1" >/dev/null; }
sh_ input keyevent HOME; sleep 1
for i in 1 2 3; do post $i; sleep 0.3; done
events
for i in 1 2 3; do check "notification $i posted off-screen shows on Events" 'texts | grep -q "Etesian_test_$i"'; done
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null; sleep 2
events
t=$(texts)
if ! echo "$t" | grep -q 'USB debugging\|Charging\|Etesian_test\|Android System'; then
  check "no notifications: no Notifications section" '! echo "$t" | grep -q "text=\"Notifications\""'
fi
check "no 'No unread feed items' row" '! echo "$t" | grep -q "No unread feed items\|No feed items yet"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

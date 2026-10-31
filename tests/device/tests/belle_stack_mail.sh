# Belle's Latest mail widget (stackemail, 2026-10-31): one new mail at a time, newest first; ▲ ▼ step through them with
# "n of N", the ends are dimmed, a tap opens that mail (its notification's action) and no mail leaves "No new mail".
unlock
check "provider BelleStackMailWidget registered" 'sh_ dumpsys appwidget | grep -q "etesian.launcher.BelleStackMailWidget"'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
tap_id() { local b; b=$(dump | grep -o "<node[^>]*$1[^>]*>" | head -n 1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '); set -- $b
  [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2; }
wp=$(pref_switch home Widgets 1)  # the Widgets view is off by default: on for the test, restored at the end
widgets_only BelleStackMailWidget
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind mails --ei count 12 >/dev/null; sleep 4
check "shows the newest mail first" 'dump | grep -q "text=\"Sender 12\""'
check "only one mail at a time" '! dump | grep -q "text=\"Sender 11\""'
check "its subject" 'dump | grep -q "text=\"Test mail number 12\""'
check "position 1 of 12" 'dump | grep -q "text=\"1 of 12\""'
tap_id 'content-desc="Older mail"'
check "▼ shows the next older mail" 'dump | grep -q "text=\"Sender 11\""'
check "position 2 of 12" 'dump | grep -q "text=\"2 of 12\""'
tap_id 'content-desc="Newer mail"'
check "▲ goes back to the newest" 'dump | grep -q "text=\"Sender 12\""'
tap_id 'content-desc="Newer mail"'
check "▲ stops at the newest" 'dump | grep -q "text=\"1 of 12\""'
for _ in $(seq 12); do tap_id 'content-desc="Older mail"'; done
check "▼ stops at the oldest" 'dump | grep -q "text=\"12 of 12\"" && dump | grep -q "text=\"Sender 1\""'
tap_id 'resource-id="com.bompotis.etesian.launcher:id/bc_title"'; sleep 3
# the test mails' own action opens Etesian settings (a real mail opens in its mail app)
check "tapping opens that mail (its notification's action)" 'top_activity | grep -q "SettingsActivity"'
sh_ input keyevent KEYCODE_BACK; sleep 1; home; widgets_pane
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null; sleep 3
check "no mail: \"No new mail\"" 'dump | grep -q "text=\"No new mail\""'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home
widgets_restore
pref_switch home Widgets "$wp" >/dev/null; home

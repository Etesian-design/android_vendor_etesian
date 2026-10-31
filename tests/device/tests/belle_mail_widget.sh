# Bug 2026-10-25 #14 (last one): Belle email widget — account and unread badge in a blue header, latest
# senders/subjects from the mail app's notifications (up to 20, scrolling); tapping it opens Mail.
unlock
check "provider BelleMailWidget registered" 'sh_ dumpsys appwidget | grep -q "etesian.launcher.BelleMailWidget"'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
seek() { for _ in 1 2 3 4 5 6 7 8; do sh_ input swipe 540 600 540 1800 150; done; sleep 1
  for _ in 1 2 3 4 5 6 7 8 9 10; do dump | grep -q "$1" && return 0; sh_ input swipe 540 1500 540 900 300; sleep 0.8; done; return 1; }
acct='resource-id="com.bompotis.etesian.launcher:id/bc_account"'
wp=$(pref_switch home Widgets 1)  # the Widgets view is off by default: on for the test, restored at the end
# the widget alone on an emptied Widgets view (the user's own may be full); their widgets are put back at the end
sh_ cp /data/data/$LAUNCHER/shared_prefs/widgets.xml /data/local/tmp/wbak.xml
prefs_put() { sh_ am force-stop $LAUNCHER; sh_ cp $1 /data/data/$LAUNCHER/shared_prefs/widgets.xml
  u=$(sh_ stat -c %u /data/data/$LAUNCHER); sh_ chown $u:$u /data/data/$LAUNCHER/shared_prefs/widgets.xml
  sh_ chmod 660 /data/data/$LAUNCHER/shared_prefs/widgets.xml; sh_ restorecon /data/data/$LAUNCHER/shared_prefs/widgets.xml >/dev/null 2>&1; }
sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
prefs_put /data/local/tmp/wempty.xml
widgets_pane
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls BelleMailWidget >/dev/null; sleep 3
check "mail widget shows its header" 'seek "$acct"'
check "and new mail or \"No new mail\"" 'dump | grep -q "No new mail\|bc_title\|notification access"'
# Regression 2026-10-30: the widget showed a fixed three mails. As Belle's Mail widget, it holds up to 20 and scrolls.
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind mails --ei count 12 >/dev/null; sleep 4
check "the newest mail (posted last) is at the top" 'seek "text=\"Sender 12\""'
check "the oldest is not on screen yet" '! dump | grep -q "text=\"Sender 1\""'
l=$(dump | grep -o "<node[^>]*resource-id=\"com.bompotis.etesian.launcher:id/bc_items\"[^>]*>" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
set -- $l; lx=$(( ($1 + $3) / 2 ))
for _ in 1 2 3 4; do sh_ input swipe $lx $(( $4 - 30 )) $lx $(( $2 + 30 )) 400; sleep 0.6; done
check "swiping inside the widget scrolls down to the oldest" 'dump | grep -q "text=\"Sender 1\""'
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null; sleep 2
src=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleClassic2.kt
check "reads CATEGORY_EMAIL notifications (source)" '[ -f $src ] && grep -q "CATEGORY_EMAIL" $src'

b=$(dump | grep -o "<node[^>]*$acct[^>]*>" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' '); set -- $b
sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 5
check "tapping opens Mail" 'top_activity | grep -q "com.bompotis.etesian.mail"'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
sh_ am force-stop com.bompotis.etesian.mail; home
prefs_put /data/local/tmp/wbak.xml
pref_switch home Widgets "$wp" >/dev/null; home

# 2026-11-23 (user): Android extras → Notifications in the status menu: a Notifications block to add in edit mode,
# off by default, listing every app's notifications or only the apps picked for it, apart from Events' own settings.
# 2026-11-24 (user): the rows are Events' own (same styling and behaviour), not a look-alike.
unlock; home
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
menu() { sh_ am start -n $LAUNCHER/.StatusMenuActivity >/dev/null; sleep 1.5; }
oldx=$(sh_ settings get secure etesian_extra_status_menu); oldn=$(sh_ settings get secure etesian_extra_status_notifications)
sh_ cp $P /data/local/tmp/sn_prefs.bak
sh_ settings put secure etesian_extra_status_menu 1; sh_ settings put secure etesian_extra_status_notifications 0
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message >/dev/null; sleep 2
menu
check "off by default: no Notifications block" '! dump | grep -q "content-desc=\"Notifications\""'
sh_ input keyevent BACK; sleep 1
sh_ settings put secure etesian_extra_status_notifications 1
sh_ am force-stop $LAUNCHER
sh_ "sed -i 's#<string name=\"status_blocks\">[^<]*</string>##; s#<set name=\"status_notification_apps\">.*</set>##; s#</map>#<string name=\"status_blocks\">profile,volume,notifications,strip,row:wifi</string></map>#' $P"
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message >/dev/null; sleep 2
menu; d=$(dump); shot status_notifications
check "the block shows" 'echo "$d" | grep -q "content-desc=\"Notifications\""'
check "with the test notification (all apps)" 'echo "$d" | grep -q "text=\"Anna Virtanen\""'
check "drawn by Events' own list" 'echo "$d" | grep -q "androidx.recyclerview.widget.RecyclerView"'
# Events' behaviour: a tap opens it and the menu goes.
y=$(echo "$d" | tr '>' '\n' | grep 'text="Anna Virtanen"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | sed -n 2p)
sh_ input tap 540 $((y + 10)); sleep 1.5
check "a tap opens the notification and closes the menu" '! top_activity | grep -q StatusMenuActivity'
home; sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message >/dev/null; sleep 2; menu
sh_ input keyevent BACK; sleep 1
# Only another app's: the launcher's own test notification goes from the block (Events keeps it).
sh_ am force-stop $LAUNCHER
sh_ "sed -i 's#</map>#<set name=\"status_notification_apps\"><string>com.android.settings</string></set></map>#' $P"
home; sleep 2; sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind message >/dev/null; sleep 2
menu; d=$(dump)
check "only the picked apps' notifications" '! echo "$d" | grep -q "text=\"Anna Virtanen\""'
sh_ input keyevent BACK; sleep 1
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
sh_ am force-stop $LAUNCHER; sh_ "cat /data/local/tmp/sn_prefs.bak > $P"; sh_ rm -f /data/local/tmp/sn_prefs.bak
put_back etesian_extra_status_menu "$oldx"; put_back etesian_extra_status_notifications "$oldn"
check "launcher did not crash" '! crashed $LAUNCHER'
home

# Sharing → N9 share sheet (mocks 11, 11b, 11c): the chooser is the N9 sheet
# (Filter with word-start matching, Pinned by long-press, All apps); Suggested people optional.
# Off: the stock chooser. Nothing is shared: the sheet is closed with Back.
check "ShareActivity.kt exists" '[ -f /home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/ShareActivity.kt ]'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
share() { sh_ "am start -a android.intent.action.CHOOSER --eu android.intent.extra.INTENT 'intent:#Intent;action=android.intent.action.SEND;type=text/plain;S.android.intent.extra.TEXT=hello;end'" >/dev/null 2>&1; sleep 2.5; }
share2() { sh_ am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT hello -f 0x10000000 >/dev/null 2>&1; }
old=$(sh_ settings get secure etesian_extra_share)
unlock; home
sh_ settings put secure etesian_extra_share 0
sh_ am start -n $LAUNCHER/.SettingsActivity --es section sharing >/dev/null; sleep 2
sh_ input keyevent BACK; sleep 1
check "off: alias disabled" 'sh_ pm dump $LAUNCHER | grep -A2 "disabledComponents" | grep -q ShareChooser || ! sh_ pm query-activities -a android.intent.action.CHOOSER | grep -q ShareChooser'
sh_ am start -n $LAUNCHER/.SettingsActivity --es section sharing >/dev/null; sleep 2
tap_text "N9 share sheet"; sleep 1.5
check "switch on" '[ "$(sh_ settings get secure etesian_extra_share)" = 1 ]'
home; sleep 1
share
check "on: N9 share sheet" 'sh_ dumpsys window windows | grep -q "ShareChooser"'
check "Filter field" 'dump | grep -q "content-desc=\"Filter\""'
check "All apps section" 'dump | grep -q "text=\"All apps\""'
shot share_sheet
first=$(dump | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed -n '/All apps/,$p' | sed -n 2p | cut -d'"' -f2)
sh_ input text "$(echo $first | cut -c1)"; sleep 1.2
check "filter keeps word-start matches ($first)" 'dump | grep -q "text=\"$first\""'
shot share_filter
tap_text "Clear filter" >/dev/null 2>&1; b=$(dump | grep -o 'content-desc="Clear filter"[^>]*bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b; [ -n "$4" ] && sh_ input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1
b=$(dump | grep -o "text=\"$first\"[^>]*bounds=\"[^\"]*\"" | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b
sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; sleep 1.5
check "long-press pins it" 'dump | grep -q "text=\"Pinned\""'
shot share_pinned
sh_ input keyevent BACK; sleep 1; share
check "pin persists" 'dump | grep -q "text=\"Pinned\""'
b=$(dump | grep -o "text=\"$first\"[^>]*bounds=\"[^\"]*\"" | grep -o '[0-9]\+' | tr '\n' ' '); set -- $b
sh_ input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 900; sleep 1.5
sleep 2.5
check "long-press again unpins ($first listed once)" '[ $(dump | grep -o "text=\"$first\"" | wc -l) = 1 ]'
sh_ input keyevent BACK; sleep 1
sh_ am start -n $LAUNCHER/.SettingsActivity --es section sharing >/dev/null; sleep 2
tap_text "N9 share sheet"; sleep 1.5; home
share
check "off again: stock chooser" '! top_activity | grep -q ShareActivity'
sh_ input keyevent BACK; sleep 1
[ "$old" = null ] && sh_ settings delete secure etesian_extra_share >/dev/null || sh_ settings put secure etesian_extra_share "$old"
check "launcher did not crash" '! crashed $LAUNCHER'
home

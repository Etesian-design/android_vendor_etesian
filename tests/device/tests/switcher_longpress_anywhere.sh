# User 2026-10-27: edit mode in Open applications must start from a long-press anywhere on the
# view, not only on a card (none: nothing to edit). Also no help text while merging/splitting.
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "no merge/split hint text left" '! grep -q "showHint\|Pull apart to separate\|Release to split" $L/SwitcherPage.kt'
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
unlock; home
sh_ am start -W -n com.android.settings/.Settings >/dev/null; sleep 1.5; home
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
check "on Open applications" '[ "$(home_pane)" = switcher ]'
# One open app (Settings): hold on the empty lower part of the view.
sh_ input swipe 540 1750 540 1750 1000; sleep 1.2
check "long-press on empty space enters edit mode" 'dump | grep -q "text=\"Close all\""'
shot switcher_longpress_empty
sh_ input keyevent BACK; sleep 1; home
check "launcher did not crash" '! crashed $LAUNCHER'

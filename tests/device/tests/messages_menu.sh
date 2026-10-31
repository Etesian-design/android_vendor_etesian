# Bug 2026-10-25 #9: Messages' ≡ toolbar button showed a stock popup with only "Archived".
# It must open the N9 toolbar menu with every item, and its items must work.
unlock; home
sh_ am start -S -n com.android.messaging/.ui.conversationlist.ConversationListActivity >/dev/null; sleep 3
sh_ input tap 978 2088; sleep 1.5   # ≡ sits in the N9 toolbar menu slot at the right edge
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "menu lists Archived and Settings" 'sh_ cat /sdcard/u.xml | grep -q "text=\"Archived\"" && sh_ cat /sdcard/u.xml | grep -q "text=\"Settings\""'
tap_text "Settings"; sleep 2
check "Settings item opens Messages settings" '[ "$(top_activity)" = "com.android.messaging/.ui.appsettings.ApplicationSettingsActivity" ]'
home

# Bug 2026-10-25 #11: Open apps cards showed the snapshot without the app's toolbar. Every card
# must show the bar along its bottom (dark for Settings' dark pages, light for Messages).
unlock; home
sh_ am start -n com.android.messaging/.ui.conversationlist.ConversationListActivity >/dev/null; sleep 2
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2; tap_text "Display"; sleep 2
home; sleep 1
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
sleep 1
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
# A card: the label's column; the bar is the bottom ~3% of the card above the label.
bar_vs_content() { # label -> "bar content" brightness
  local b; b=$(sh_ cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ')
  set -- $b; local x=$(( ($1 + $3) / 2 + 40 )) y=$(( $2 - 14 ))
  echo "$(pixel $x $y) $(pixel $x $(( y - 60 )))"
}
set -- $(bar_vs_content Messaging); m_bar=$1; m_content=$2
check "Messaging card has a light bar ($m_bar under $m_content)" '[ "$m_bar" -gt 120 ] && [ $(( m_content - m_bar )) -gt 25 ]'
set -- $(bar_vs_content Settings); s_bar=$1
check "Settings card has the dark bar ($s_bar)" '[ "$s_bar" -gt 8 ] && [ "$s_bar" -lt 90 ]'
home

# Bug 2026-10-25 #11: Open apps cards showed the snapshot without the app's toolbar. Every card
# must show the bar along its bottom (dark for Settings' dark pages, light for Messages).
unlock; home
sh_ am start -n com.android.messaging/.ui.conversationlist.ConversationListActivity >/dev/null; sleep 2
sh_ am start -a android.settings.SETTINGS >/dev/null; sleep 2; tap_text "Display"; sleep 2
home; sleep 1
for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.5; done
sleep 1
# The switcher keeps its scroll position between visits: start from the top (the newest cards).
for _ in 1 2 3 4 5 6; do sh_ input swipe 540 500 540 1900 150; sleep .4; done; sleep 1
# The card's label: other apps left open by earlier tests can push a card off the screen, so scroll the switcher until
# the label is on screen (each dump is fresh: an old file never stands in for this screen).
label_bounds() { # label -> "x1 y1 x2 y2", scrolling down to it
  local label=$1 b
  for _ in 1 2 3 4; do
    sh_ rm -f /sdcard/u.xml; sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
    b=$(sh_ cat /sdcard/u.xml | grep -o "text=\"$label\"[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ')
    set -- $b; [ -n "$b" ] && [ "$2" -gt 300 ] && [ "$4" -lt 2050 ] && { echo "$b"; return; }
    sh_ input swipe 540 1700 540 900 400; sleep 1.2
  done
}
# A card: the label's column; the bar is the bottom ~3% of the card above the label.
bar_vs_content() { # label -> "bar content" brightness
  local b; b=$(label_bounds "$1")
  set -- $b; local x=$(( ($1 + $3) / 2 + 40 )) y=$(( $2 - 14 ))
  echo "$(pixel $x $y) $(pixel $x $(( y - 60 )))"
}
# Settings first: it is the newest card, at the top; Messaging can need scrolling down to.
set -- $(bar_vs_content Settings); s_bar=$1
check "Settings card has the dark bar ($s_bar)" '[ "$s_bar" -gt 8 ] && [ "$s_bar" -lt 90 ]'
set -- $(bar_vs_content Messaging); m_bar=$1; m_content=$2
check "Messaging card has a light bar ($m_bar under $m_content)" '[ "$m_bar" -gt 120 ] && [ $(( m_content - m_bar )) -gt 25 ]'
home

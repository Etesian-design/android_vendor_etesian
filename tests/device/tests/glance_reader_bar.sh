# User 2026-11-20: Glance's reader has its buttons on the bottom bar: Back, the original in the browser, Share and the
# menu; nothing on top (no Back up there), and Back on the side chosen in Etesian settings → Toolbar (both sides tried).
G=com.bompotis.etesian.glance
xmlw() { sh_ "uiautomator dump --windows /sdcard/u.xml" >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
# cx <desc>: "<centre x> <top y>" of the first node with that content-desc
cx() { xmlw | tr '>' '\n' | grep "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 | tr -cs '0-9' ' ' | awk '{print int(($1+$3)/2), $2}'; }
unlock
W=$(sh_ wm size | grep -o '[0-9]*x' | tr -d x); H=$(sh_ wm size | grep -o 'x[0-9]*' | tail -1 | tr -d x)
side=$(sh_ settings get secure etesian_toolbar_back_side)
open_reader() {
  sh_ am force-stop $G; sh_ am start -W -n $G/.MainActivity >/dev/null; sleep 3
  for _ in $(seq 20); do xmlw | grep -q 'content-desc="story:' && break; sleep 2; done
  # The list can still be filling in (pictures, more stories) and move under the tap: tap the first story where it is
  # now, wait for the reader, and tap again if it did not open.
  for _ in 1 2 3; do
    set -- $(xmlw | grep -o 'content-desc="story:[^"]*"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | tr -cs '0-9' ' ')
    [ -n "$4" ] && sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    for _ in $(seq 10); do top_activity | grep -q "$G/.ReaderActivity" && break 2; sleep 1; done
  done
  sleep 2
}
for s in 0 1; do
  sh_ settings put secure etesian_toolbar_back_side $s
  open_reader; shot glance_reader_bar_$s
  check "side $s: the reader is open ($(top_activity))" 'top_activity | grep -q "$G/.ReaderActivity"'
  read bx by <<<"$(cx Back)"; read ox oy <<<"$(cx 'Open in browser')"; read hx hy <<<"$(cx Share)"; read mx my <<<"$(cx Menu)"
  check "side $s: Back, Browser, Share and Menu all there (y $by $oy $hy $my)" '[ -n "$by" ] && [ -n "$oy" ] && [ -n "$hy" ] && [ -n "$my" ]'
  check "side $s: all on the bottom bar (below $((H * 85 / 100)))" '[ "${by:-0}" -gt $((H * 85 / 100)) ] && [ "${oy:-0}" -gt $((H * 85 / 100)) ] && [ "${hy:-0}" -gt $((H * 85 / 100)) ] && [ "${my:-0}" -gt $((H * 85 / 100)) ]'
  nb=$(xmlw | grep -o 'content-desc="Back"' | wc -l)
  check "side $s: one Back only, none on top ($nb)" '[ "$nb" = 1 ]'
  if [ $s = 0 ]; then
    check "side 0: Back on the left ($bx of $W)" '[ "${bx:-$W}" -lt $((W / 4)) ]'
  else
    check "side 1: Back on the right ($bx of $W)" '[ "${bx:-0}" -gt $((W * 3 / 4)) ]'
  fi
  sh_ input tap ${bx:-0} ${by:-0}; sleep 1.5
  check "side $s: Back leaves the reader ($(top_activity))" '! top_activity | grep -q "$G/.ReaderActivity"'
done
sh_ settings put secure etesian_toolbar_back_side "${side/null/0}"
check "Back button side restored (${side/null/0})" '[ "$(sh_ settings get secure etesian_toolbar_back_side)" = "${side/null/0}" ]'
check "Glance did not crash" '! crashed $G'
sh_ am force-stop $G; home

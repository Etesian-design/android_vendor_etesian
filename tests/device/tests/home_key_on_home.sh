# Bug 2026-11-04 (found running Etesian in Cloud Companion): Home pressed on Open applications or
# Events did nothing. Android pauses home before delivering the Home intent, and the launcher took
# "paused" for "an application was in front". Home while home is showing goes to Applications.
unlock; home
for pane in switcher events; do
    for _ in 1 2 3 4; do [ "$(home_pane)" = $pane ] && break; sh_ input swipe 900 1100 150 1100 300; sleep 1.5; done
    sh_ input keyevent HOME; sleep 2
    check "Home on $pane goes to Applications ($(home_pane))" '[ "$(home_pane)" = apps ]'
done
check "launcher did not crash" '! crashed $LAUNCHER'

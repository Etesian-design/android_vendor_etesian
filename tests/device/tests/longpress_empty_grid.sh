# Bug 2026-10-25 #2: long-pressing empty space on the app grid opened Etesian settings
# (inside the home task, with no way out). On the N9 it puts the grid in edit mode.
unlock; home
# Bottom of the grid, above the toolbar: empty on a phone with few apps.
sh_ input swipe 540 1950 540 1950 900; sleep 1.5
check "long-press on empty grid stays on home" '[ "$(top_activity)" = "$LAUNCHER/.HomeActivity" ]'
home

# Bug 2026-10-25 #4: unlocking always jumped to the Applications pane (UnlockAnimation forced it).
# Unlocking must come back to the pane that was showing when the phone locked.
unlock; home
for pane in events switcher; do
    # Swipe across the empty area under the grid until the pane shows (pages wrap around).
    for _ in 1 2 3 4; do
        [ "$(home_pane)" = $pane ] && break
        sh_ input swipe 100 1700 1000 1700 300; sleep 1.5
    done
    before=$(home_pane)
    lock; swipe_unlock
    after=$(home_pane)
    check "unlock returns to $pane (was $before, now $after)" '[ "$before" = $pane ] && [ "$after" = $pane ]'
done
home

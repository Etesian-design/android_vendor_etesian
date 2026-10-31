# Bug 2026-10-25 #3: "More notifications" (and the toolbar choices) had no preference key, so
# opening their dialog crashed the launcher. Every choice dialog in Etesian settings must open.
unlock; home
# Settings are in sub pages (#23): each title with the page it lives on.
for entry in "lock:More notifications" "lock:Notifications shown in full" "toolbar:Toolbar" \
             "toolbar:Back button (and side strip)" "toolbar:Toolbar theme"; do
    page=${entry%%:*}; title=${entry#*:}
    sh_ logcat -c -b crash
    sh_ am start -S -n $LAUNCHER/.SettingsActivity --es section $page >/dev/null; sleep 2
    if tap_text "$title"; then
        sleep 1.5
        check "\"$title\" opens without crashing" '! crashed $LAUNCHER'
        sh_ input keyevent BACK; sleep 0.5
    else
        bad "\"$title\" not found in settings"
    fi
done
home

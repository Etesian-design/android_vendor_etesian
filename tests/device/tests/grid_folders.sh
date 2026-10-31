# Bug 2026-10-25 #10: no folders, and edit mode gave no idea what it does.
# Long-press empty space -> edit bar (Done / New folder) and hint; New folder appends "Folder";
# its ✕ removes it again (leaves the grid as it was).
unlock; home
for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.5; done
sh_ input swipe 540 1950 540 1950 900; sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "edit mode shows Done / New folder" 'sh_ cat /sdcard/u.xml | grep -q "text=\"New folder\"" && sh_ cat /sdcard/u.xml | grep -q "text=\"Done\""'
check "edit mode has no hint text (#19)" '! sh_ cat /sdcard/u.xml | grep -q "Drag to rearrange"'
before=$(sh_ cat /sdcard/u.xml | grep -o 'content-desc="Folder"' | wc -l)
tap_text "New folder"; sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
after=$(sh_ cat /sdcard/u.xml | grep -o 'content-desc="Folder"' | wc -l)
check "New folder adds a Folder" '[ "$after" -gt "$before" ]'
# Remove it: the ✕ at the top-right of the last Folder cell.
b=$(sh_ cat /sdcard/u.xml | grep -o 'content-desc="Folder"[^>]*bounds="[^"]*"' | tail -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | tr '[],' '   ')
set -- $b; sh_ input tap $(( $3 - 60 )) $(( $2 + 30 )); sleep 1.5
sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1
check "folder ✕ removes it" '[ "$(sh_ cat /sdcard/u.xml | grep -o "content-desc=\"Folder\"" | wc -l)" -eq "$before" ]'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
tap_text "Done"; sleep 1

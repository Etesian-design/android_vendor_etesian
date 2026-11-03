# The ROM's own apps are what runs: no system app is shadowed by an older adb install -r left from
# iterating, which would test stale code after a flash (2026-11-03: eleven were, the launcher among
# them). Fix: pm uninstall-system-updates <pkg>, or for protected ones delete the /data/app copy and reboot.
for p in $(sh_ pm list packages -s -f | grep -o "=[a-z0-9._]*$" | tr -d =); do
  sh_ pm path $p | grep -q "^package:/data/app" && echo "     shadowed: $p"
done > /tmp/claude-1000/shadowed.txt
cat /tmp/claude-1000/shadowed.txt
check "no system app shadowed by a leftover install" '[ ! -s /tmp/claude-1000/shadowed.txt ]'

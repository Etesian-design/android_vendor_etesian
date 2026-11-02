#!/bin/bash
# On-device regression tests for the Etesian ROM. Needs adb root. Usage: run.sh [test-name...]
cd "$(dirname "$0")" && . ./lib.sh
adb root >/dev/null 2>&1; sleep 2
# Silent for the whole run: Do Not Disturb in total silence and no touch sounds; both put back at the end or on Ctrl-C.
_zen0=$(adb shell settings get global zen_mode | tr -d '\r'); _sys0=$(adb shell cmd media_session volume --stream 1 --get | grep -o "is [0-9]*" | cut -d" " -f2)
quiet_on() { adb shell settings put global zen_mode 2; adb shell cmd media_session volume --stream 1 --set 0 >/dev/null; }
quiet_off() { adb shell settings put global zen_mode "${_zen0:-0}"; adb shell cmd media_session volume --stream 1 --set "${_sys0:-5}" >/dev/null; }
quiet_on; trap quiet_off EXIT
# The user's own settings (extras, Widgets view): every test must leave them as it found them.
user_state() { unlock >/dev/null; sh_ settings list secure | grep '^etesian_extra_' | sort; echo "widgets=$(pref_switch home Widgets)"; }
# An interrupted run (Ctrl-C, killed) must not leave a test's changes behind: put back the
# launcher settings and the extras as they were before the test that was running.
LP=/data/data/com.bompotis.etesian.launcher/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
interrupted() { echo "interrupted: restoring the user's settings"
  sh_ "am force-stop com.bompotis.etesian.launcher; cat /data/local/tmp/run_user_prefs.bak > $LP"
  echo "$_us0" | grep '^etesian_extra_' | while IFS== read -r k v; do sh_ settings put secure "$k" "$v"; done
  quiet_off; exit 130; }
trap interrupted INT TERM HUP
for t in ${@:-tests/*.sh}; do [ -f "$t" ] || t=tests/$t.sh; echo "== $(basename "$t" .sh)"
  _us0=$(user_state); sh_ "cp $LP /data/local/tmp/run_user_prefs.bak"; . "$t"; _us1=$(user_state)
  check "left the user's settings as they were" '[ "$_us0" = "$_us1" ]'
  # Restore only from a sane snapshot, and only by writing values back (never deleting).
  [ "$_us0" = "$_us1" ] || ! echo "$_us0" | grep -q "^etesian_extra_" || {
    diff <(echo "$_us0") <(echo "$_us1") | sed 's/^/     /'
    echo "$_us0" | grep '^etesian_extra_' | while IFS== read -r k v; do sh_ settings put secure "$k" "$v"; done
    pref_switch home Widgets "$(echo "$_us0" | sed -n 's/^widgets=//p')" >/dev/null; home; }
done
echo "passed $PASS, failed $FAIL"; [ $FAIL -eq 0 ]

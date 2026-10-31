# Android extras → Private space (story Phone v2, state private-space): Android's private space as
# a locked folder at the end of the Applications grid; opening it asks for the security code (N9
# code screen); its apps show in the folder; closing it locks the space again. Off by default.
# Uses a temporary private profile, removed at the end. (No test PIN: this phone refuses to set
# credentials while factory reset protection is active, so the space has no code of its own.)
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
center() { set -- $(bounds "$1"); [ -n "$4" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
apps_end() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = apps ] && break; sh_ input swipe 100 1700 1000 1700 300; sleep 1.2; done
  for _ in 1 2 3 4 5 6; do sh_ input swipe 540 1700 540 400 200; sleep 0.4; done; sleep 1; }
quiet() { sh_ dumpsys user | grep -A3 "UserInfo{$pid:" | grep -q "QUIET_MODE"; }
old=$(sh_ settings get secure etesian_extra_private_space)
pid=$(sh_ pm list users | grep -o "UserInfo{[0-9]*:EtesianTestPrivate" | grep -o "[0-9]*" | head -1)
made=0
[ -z "$pid" ] && pid=$(sh_ cmd user list -v | grep "profile.PRIVATE" | grep -o "^ *[0-9]*" | tr -d ' ' | head -1)
if [ -z "$pid" ]; then
  pid=$(sh_ pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE EtesianTestPrivate | tr -d '\r' | grep -o "[0-9]*$"); made=1
  sh_ am start-user -w $pid >/dev/null; sh_ pm install-existing --user $pid com.android.calculator2 >/dev/null
fi
check "a private space exists (user $pid)" '[ -n "$pid" ]'
sh_ settings put secure etesian_extra_private_space 0; sh_ am force-stop $LAUNCHER; sleep 1; unlock
apps_end
check "off: no Private folder" '! dump | grep -q "content-desc=\"Private\""'
sh_ settings put secure etesian_extra_private_space 1; sh_ am force-stop $LAUNCHER; sleep 1
# Locked to start with.
quiet || { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind lock_private >/dev/null; sleep 3; }
check "starts locked" 'quiet'
apps_end
shot private_folder
check "on: Private folder in the grid" 'dump | grep -q "content-desc=\"Private\""'
check "it is the last cell" '[ "$(dump | grep -o "content-desc=\"[^\"]*\"" | grep -v "content-desc=\"\"" | tail -1)" = "content-desc=\"Private\"" ]'
check "locked: its apps are not in the grid" '! dump | grep -q "text=\"Calculator\""'
set -- $(center 'content-desc="Private"'); px=$1; py=$2; sh_ input tap $px $py; sleep 2
check "tap asks for the security code" 'dump | grep -q "text=\"Enter the security code\"" && dump | grep -q "text=\"Private\""'
sh_ input text 12; sleep 0.8
shot private_code
check "code dots fill as you type (2 of 4)" 'dump | grep -q "content-desc=\"Code: 2 digits\""'
set -- $(center 'content-desc="Done"'); sh_ input tap ${1:-540} ${2:-1248}; sleep 4
check "Done opens the space" '! quiet'
shot private_open
check "its apps show in the folder" 'dump | grep -q "text=\"Calculator\""'
set -- $(center 'content-desc="Private"'); sh_ input tap ${1:-$px} ${2:-$py}; sleep 3
check "closing the folder locks it again" 'quiet'
check "locked again: apps hidden" '! dump | grep -q "text=\"Calculator\""'
# Edit mode: the private folder has no ✕.
set -- $(center 'content-desc="Private"'); sh_ input swipe ${1:-540} ${2:-1900} ${1:-540} ${2:-1900} 900; sleep 1.2
check "edit mode: no remove badge on Private" '! dump | grep -q "content-desc=\"Remove Private\""'
sh_ input keyevent BACK; sleep 0.8
[ $made = 1 ] && sh_ pm remove-user $pid >/dev/null
[ "$old" = null ] && sh_ settings delete secure etesian_extra_private_space >/dev/null || sh_ settings put secure etesian_extra_private_space $old
check "temporary private space removed" '[ $made = 0 ] || ! sh_ pm list users | grep -q EtesianTestPrivate'
check "launcher did not crash" '! crashed $LAUNCHER'
home

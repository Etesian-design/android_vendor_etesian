# Etesian settings: the additions the N9 did not have are ordinary settings on the page of what they
# change (no "Android extras" page or label), emergency information has its own page, and each page
# with such settings starts with a live preview that redraws when one of them changes. All additions
# stay off by default (Settings.Secure etesian_extra_<name>, unset = 0).
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
E=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
PY=/home/memphisx/android/.venv-tools/bin/python
page() { sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity --es section $1 >/dev/null; sleep 2.5; }
has() { local i; for i in 1 2 3 4 5 6 7 8; do dump | grep -q "text=\"$1\"" && return 0; sh_ input swipe 540 1700 540 900 300; sleep 0.7; done; return 1; }
# The preview's bounds as "l t r b".
pbounds() { dump | grep -o "content-desc=\"Preview: $1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
# Pixels of the preview area that changed clearly between two shots (a small dot is enough).
pdiff() { $PY -c "
from PIL import Image, ImageChops
l,t,r,b=map(int,'$3'.split()); a=Image.open('$SHOTS/$1.png').convert('L').crop((l,t,r,b)); c=Image.open('$SHOTS/$2.png').convert('L').crop((l,t,r,b))
print(sum(1 for v in ImageChops.difference(a,c).tobytes() if v > 40))"; }
flip() { local k=$1 o; o=$(sh_ settings get secure $k); [ "$o" = 1 ] && echo 0 || echo 1; }
unlock; home

check "no Android extras page in the code" '! grep -q "\"extras\"" $E/SettingsActivity.kt && ! grep -rq "Android extras" $E/SettingsActivity.kt'
sh_ am start -f 0x10008000 -n $LAUNCHER/.SettingsActivity >/dev/null; sleep 2.5
for t in "Status menu" "Open applications" "Emergency information" "Sharing" "Events and notifications"; do
  check "main page: $t" "has '$t'"
done
check "main page: no Android extras" '! has "Android extras"'
sh_ input keyevent BACK; sleep 1

# Each page: its preview at the top and its settings.
for spec in "status|Your own layout|Privacy indicators" "events|Actions and snooze|Notification bubbles" \
            "open|Auto-arrange|Two-finger merge" "apps|Private space" "lock|Glance line" \
            "emergency|Show on the lock screen|Blood type|Contacts" "sharing|N9 share sheet|Share network" \
            "dismissed|Keep dismissed notifications|Rows shown" "priority|Priority people|Add a person"; do
  IFS='|' read -r sec a b c <<< "$spec"
  page $sec
  check "$sec: preview at the top" 'dump | grep -q "content-desc=\"Preview: '$sec'\""'
  check "$sec: $a" "has '$a'"
  [ -n "$b" ] && check "$sec: $b" "has '$b'"
  [ -n "$c" ] && check "$sec: $c" "has '$c'"
  check "$sec: no Android extras label" '! dump | grep -q "Android extras"'
  sh_ input keyevent BACK; sleep 1
done
check "events links to Priority people and Dismissed notifications" 'page events; has "Priority people" && has "Dismissed notifications"'
sh_ input keyevent BACK; sleep 1
check "lock links to Emergency information" 'page lock; has "Emergency information"'
sh_ input keyevent BACK; sleep 1

# The preview follows the setting: flip it, the miniature changes; flip back, it is as before.
for spec in "status|etesian_extra_privacy" "lock|etesian_extra_glance" "open|etesian_extra_card_actions" "apps|etesian_extra_private_space"; do
  IFS='|' read -r sec k <<< "$spec"
  old=$(sh_ settings get secure $k)
  page $sec; bb=$(pbounds $sec); shot pv_${sec}_a
  sh_ settings put secure $k $(flip $k); sleep 1.2; shot pv_${sec}_b
  [ "$old" = null ] && sh_ settings delete secure $k >/dev/null || sh_ settings put secure $k $old; sleep 1.2; shot pv_${sec}_c
  check "$sec preview redraws when $k changes" '[ -n "$bb" ] && [ "$(pdiff pv_${sec}_a pv_${sec}_b "$bb")" -ge 30 ]'
  check "$sec preview back as before" '[ "$(pdiff pv_${sec}_a pv_${sec}_c "$bb")" -lt 30 ]'
  sh_ input keyevent BACK; sleep 1
done

# The switch on a page writes the user's own value and puts it back.
old=$(sh_ settings get secure etesian_extra_privacy); [ "$old" = 1 ] && was=1 || was=0
page status; has "Privacy indicators"; tap_text "Privacy indicators"; sleep 1
check "switch writes etesian_extra_privacy=$((1-was))" '[ "$(sh_ settings get secure etesian_extra_privacy)" = $((1-was)) ]'
tap_text "Privacy indicators"; sleep 1
check "switch writes it back to $was" '[ "$(sh_ settings get secure etesian_extra_privacy)" = $was ]'
[ "$old" = null ] && sh_ settings delete secure etesian_extra_privacy >/dev/null
sh_ input keyevent BACK; sleep 1

# Regression (kept from the old page): a settings page opens at the top every time.
top=0; for _ in 1 2 3; do page status; dump | grep -q "content-desc=\"Preview: status\"" && top=$((top+1)); sh_ input keyevent BACK; sleep 1; done
check "opens at the top every time ($top/3)" '[ $top -eq 3 ]'

check "additions default off (unset key = 0)" 'grep -q "getInt(ctx.contentResolver, key(name), 0) == 1" $E/Extras.kt'
check "nothing but settings switches an addition on" '! grep -rn "putInt([^)]*etesian_extra_\|key(name), 1)" $E --include=*.kt | grep -v "Extras.kt\|SettingsActivity.kt"'
check "launcher did not crash" '! crashed $LAUNCHER'
home

# Open applications (Phone v2 item 7; XMerge.vue is the reference):
#  N9 parity: edit mode drag reorder; "auto-arrange" moves the last opened app to the top.
#  Android extras → Split by two-finger merge (off by default): in edit mode, one finger on each of
#  two cards pulled together snaps into ONE split card (buzz 30); pulling apart past 0.45 of a
#  card's width tears it back (buzz 10); lifting while merged is a real split-screen launch. The
#  reverse: two fingers on a split card tear it in two (buzz 30), pushing back re-joins (buzz 10),
#  lifting while torn separates the pair and stays in Open applications.
# Two-finger paths are injected through the system input by the launcher's test receiver.
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml; }
bounds() { dump | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' '; }
center() { set -- $(bounds "$1"); [ -n "$4" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
cards() { dump | grep -o 'content-desc="[^"]*"[^>]*bounds="\[[0-9]*,1[0-9][0-9]\]' | grep -o 'content-desc="[^"]*"' | grep -v 'desc=""\|pane:\|desc="Close"' | cut -d'"' -f2 | tr '\n' '|'; }
switcher() { home; for _ in 1 2 3 4; do [ "$(home_pane)" = switcher ] && break; sh_ input swipe 1000 1700 100 1700 300; sleep 1.2; done; sleep 1; }
edit() { set -- $(center "content-desc=\"$1\""); sh_ input swipe ${1:-194} ${2:-478} ${1:-194} ${2:-478} 900; sleep 1.2; }
# The broadcast returns when the gesture ends: run it in the background, then wait or look mid-gesture.
gesture() { sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind gesture --es path "'$1'" --ei steps ${3:-20} --ei ms 16 --ei hold ${2:-0} >/dev/null & sleep 0.3; }
buzzes() { sh_ dumpsys vibrator_manager | grep "com.bompotis.etesian.launcher" | grep -c "original: Step=$1ms"; }
shot_dev() { sh_ screencap -p /sdcard/hm.png; adb pull /sdcard/hm.png "$SHOTS/$1.png" >/dev/null 2>&1; }
in_split() { [ "$(sh_ dumpsys activity activities | grep "^ *\* Task{" | grep -E "calculator2|contacts" | grep -c "mode=multi-window")" -ge 2 ]; }
log_has() { sh_ logcat -d -s EtesianSwitcher | grep -q "$1"; }
CALC=com.android.calculator2/.Calculator; CONT=com.android.contacts/.activities.PeopleActivity
old=$(sh_ settings get secure etesian_extra_split_merge)
P=/data/data/$LAUNCHER/shared_prefs/${LAUNCHER}_preferences.xml
unlock
sh_ am force-stop com.android.calculator2; sh_ am force-stop com.android.contacts; sleep 1
sh_ am start -n $CALC >/dev/null; sleep 2; sh_ am start -n $CONT >/dev/null; sleep 2
switcher
o=$(cards)
check "auto-arrange: the last opened first ($o)" 'echo "$o" | grep -q "^Contacts|Calculator"'
sh_ am start -n $CALC >/dev/null; sleep 2; switcher; o=$(cards)
check "auto-arrange: reopened Calculator moves to the top ($o)" 'echo "$o" | grep -q "^Calculator|Contacts"'
# Your own order: drag in edit mode, auto-arrange off keeps it.
sh_ "sed -i 's#<boolean name=\"switcher_auto_arrange\" value=\"true\" />##' $P" 2>/dev/null
sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind cancel >/dev/null
set -- $(center 'content-desc="Calculator"'); ax=$1; ay=$2; set -- $(center 'content-desc="Contacts"'); bx=$1; by=$2
edit Calculator
sh_ logcat -c; sh_ input motionevent DOWN $ax $ay; sleep 1.2; shot split_drag_hold
for k in 1 2 3 4 5; do sh_ input motionevent MOVE $(( ax + (bx-ax)*k/5 )) $(( ay + (by-ay)*k/5 )); done; sleep 0.5
sh_ input motionevent UP $bx $by; sleep 1.5
shot split_drag_after
o=$(cards)
check "edit mode: drag reorders the cards ($o; $ax,$ay→$bx,$by)" 'echo "$o" | grep -q "^Contacts|Calculator"'
sh_ input keyevent BACK; sleep 0.5
# --- two-finger split (extra) ---
sh_ settings put secure etesian_extra_split_merge 0; switcher; edit Contacts
set -- $(center 'content-desc="Contacts"'); ax=$1; ay=$2; set -- $(center 'content-desc="Calculator"'); bx=$1; by=$2
gesture "$ax,$ay,$bx,$by;$(( (ax+bx)/2 + 10 )),$ay,$(( (ax+bx)/2 )),$by" 300; sleep 2
check "off: pulling two cards together does not split" '! in_split'
sh_ settings put secure etesian_extra_split_merge 1; switcher; edit Contacts
set -- $(center 'content-desc="Contacts"'); ax=$1; ay=$2; set -- $(center 'content-desc="Calculator"'); bx=$1; by=$2
mx=$(( (ax+bx)/2 ))
b30=$(buzzes 30); b10=$(buzzes 10); sh_ logcat -c
# Pull together, hold: one merged card showing the split.
gesture "$ax,$ay,$bx,$by;$(( mx + 10 )),$ay,$(( mx - 10 )),$by" 5000; sleep 1.5
shot_dev split_merged
check "snap: ONE card showing the split" 'log_has "snap"'
mw=$(sh_ logcat -d -s EtesianSwitcher | grep -o "merged w=[0-9]* h=[0-9]* card=[0-9]*" | tail -1)
mW=$(echo "$mw" | sed 's/.*w=\([0-9]*\) h.*/\1/'); mC=$(echo "$mw" | sed 's/.*card=//')
check "held merged card is ONE card wide ±10% ($mw)" '[ -n "$mC" ] && [ $((mW*10)) -ge $((mC*9)) ] && [ $((mW*10)) -le $((mC*11)) ]'
sleep 5
check "snap buzz(30)" '[ $(buzzes 30) -gt $b30 ]'
check "release while merged: real split screen" 'in_split'
shot split_launched
switcher
check "the pair is one card: Contacts + Calculator" 'dump | grep -q "content-desc=\"Contacts + Calculator\""'
shot split_pair_card
# The reverse: pull the pair apart, push back (re-join), release: still a pair.
edit "Contacts + Calculator"
set -- $(bounds 'content-desc="Contacts + Calculator"'); cx=$(( ($1+$3)/2 )); cy=$(( ($2+$4)/2 )); cw=$(( $3-$1 ))
b30=$(buzzes 30); b10=$(buzzes 10); sh_ logcat -c
gesture "$cx,$(( cy-100 )),$cx,$(( cy+100 ));$cx,$(( cy-100-cw/2 )),$cx,$(( cy+100+cw/2 ));$cx,$(( cy-100 )),$cx,$(( cy+100 ))" 300 20; sleep 3
check "pull apart past the limit: torn buzz(30)" '[ $(buzzes 30) -gt $b30 ]'
check "pushed back: re-joined buzz(10)" '[ $(buzzes 10) -gt $b10 ]'
check "released re-joined: still one split card" 'in_split && dump | grep -q "content-desc=\"Contacts + Calculator\""'
# Pull apart and hold torn: two cards under the fingers; release separates and stays here.
sh_ logcat -c
gesture "$cx,$(( cy-100 )),$cx,$(( cy+100 ));$cx,$(( cy-140-cw/2 )),$cx,$(( cy+140+cw/2 ))" 5000 20; sleep 1.5
shot_dev split_torn
check "torn: two cards under the fingers" 'log_has "torn"'
tl=$(sh_ logcat -d -s EtesianSwitcher | grep -o "torn w=.*" | tail -1)
tW=$(echo "$tl" | sed 's/torn w=\([0-9]*\).*/\1/'); tC=$(echo "$tl" | sed 's/.*card=\([0-9]*\).*/\1/')
tI=$(echo "$tl" | sed 's/.* icon=\([0-9]*\).*/\1/'); tG=$(echo "$tl" | sed 's/.*gridicon=//')
check "torn cards are normal card width ±10% ($tl)" '[ -n "$tC" ] && [ $((tW*10)) -ge $((tC*9)) ] && [ $((tW*10)) -le $((tC*11)) ]'
check "torn card icon not scaled (= grid icon)" '[ -n "$tI" ] && [ "$tI" = "$tG" ]'
check "no merge/split hint text (removed 2026-10-27)" '! grep -q "pull apart to cancel" $HOME/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/SwitcherPage.kt'
sleep 6
check "release while torn: the pair is separated" '! in_split'
check "and Open applications stays on screen" '[ "$(home_pane)" = switcher ]'
o=$(cards)
check "two cards again ($o)" 'echo "$o" | grep -q "Contacts" && echo "$o" | grep -q "Calculator" && ! echo "$o" | grep -q " + "'
# Tear back while merging: pull together, then apart past the limit before lifting → no split.
edit Contacts
set -- $(center 'content-desc="Contacts"'); ax=$1; ay=$2; set -- $(center 'content-desc="Calculator"'); bx=$1; by=$2; mx=$(( (ax+bx)/2 ))
b10=$(buzzes 10); sh_ logcat -c
gesture "$ax,$ay,$bx,$by;$(( mx + 10 )),$ay,$(( mx - 10 )),$by;$(( ax - 120 )),$ay,$(( bx + 120 )),$by" 300; sleep 3
check "pulled apart before lifting: tears back buzz(10)" 'log_has "tear back" && [ $(buzzes 10) -gt $b10 ]'
check "and no split" '! in_split'
sh_ input keyevent BACK
sh_ am force-stop com.android.calculator2; sh_ am force-stop com.android.contacts
[ "$old" = null ] && sh_ settings delete secure etesian_extra_split_merge >/dev/null || sh_ settings put secure etesian_extra_split_merge $old
check "launcher did not crash" '! crashed $LAUNCHER'
home

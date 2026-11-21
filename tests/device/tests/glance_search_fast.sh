# 2026-11-20 (user): "Show the phone results as they load instead of waiting for everything to be ready. And that goes
# to everything not just internet search": "On a busy phone fetching results can make the phone wait a couple of
# seconds or even more until it starts showing results". Each source shows as it answers, in Glance and in the
# launcher's search (which asks each of Glance's sources on its own). Timings from both apps' debug logs.
unlock
G=com.bompotis.etesian.glance
first_ms() { sh_ logcat -d -s "$1:D" | grep -o "after [0-9]*ms" | head -1 | grep -o "[0-9]*"; }
last_ms() { sh_ logcat -d -s "$1:D" | grep -o "after [0-9]*ms" | tail -1 | grep -o "[0-9]*"; }

# Glance: one letter at a time, as typed.
sh_ am force-stop $G; sh_ monkey -p $G -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
b=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
set -- $b; command adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
sh_ logcat -c
for c in w i f; do command adb shell input text $c; sleep 0.12; done; sleep 2.5
n=$(sh_ logcat -d -s GlanceSearch:D | grep -c "after")
f=$(sh_ logcat -d -s GlanceSearch:D | grep "after" | tail -9 | grep -o "after [0-9]*ms" | head -1 | grep -o "[0-9]*")
check "Glance: the last letter's first results show at once (${f}ms)" '[ -n "$f" ] && [ "$f" -lt 300 ]'
check "Glance: the older letters' searches stopped (answers logged: $n, at most 3 searches)" '[ "$n" -le 27 ]'
u=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml)
check "Glance: Settings pages for \"wif\" are listed" 'echo "$u" | grep -qi "text=\"Settings\"\|text=\"SETTINGS\""'
sh_ am force-stop $G

# The launcher's applications search.
sp=$(pref_switch apps "Pull down to search" 1)
apps_pane
sh_ logcat -c
sh_ input swipe 540 300 540 2050 1200; sleep 1.5
sh_ input text wif; sleep 3
s=$(sh_ logcat -d -s EtesianSearch:D | grep -c "after")
k=$(sh_ logcat -d -s EtesianSearch:D | grep "SETTING [1-9]" | grep -o "[0-9]*ms" | grep -o "[0-9]*")
check "launcher: each of Glance's 8 sources answered on its own ($s)" '[ "$s" = 8 ]'
check "launcher: the Settings pages show without waiting for the rest (${k}ms)" '[ -n "$k" ] && [ "$k" -lt 1000 ]'
u=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml)
check "launcher: the Settings section is on screen" 'echo "$u" | grep -qi "text=\"Settings\""'
sh_ input keyevent BACK; sleep 0.5; sh_ input keyevent BACK; sleep 0.5
check "Glance did not crash" '! crashed $G'
check "launcher did not crash" '! crashed com.bompotis.etesian.launcher'
home

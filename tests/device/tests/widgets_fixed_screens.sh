# Request 2026-10-29: lists inside widgets (mail, feeds) could not be scrolled, as the Widgets view
# scrolled instead. Option: fixed screens that fit the display and never scroll, 1-5 of them.
W=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/WidgetsPage.kt
check "a fixed screen's scroll view only scrolls when it holds too much" 'grep -q "private fun can() = !fixed || overflowing" $W'
check "one widget host shared by every screen" 'grep -q "sharedHost ?: Host" $W'
unlock
P=/data/data/$LAUNCHER/shared_prefs/widgets.xml
sh_ cp $P /data/local/tmp/wfbak.xml
wp=$(pref_switch home Widgets 1)
# Five 4x2 notes widgets: more than one screen holds. Added to the scrolling list (fixed screens would
# refuse those that do not fit), whatever layout the phone had; that layout is put back at the end.
was_layout=$(pref_list home "Widgets layout" "One scrolling list")
sh_ "echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"widget_items\\\"></string></map>\" > /data/local/tmp/wempty.xml"
widgets_put /data/local/tmp/wempty.xml; home
widgets_pane
for _ in 1 2 3 4 5; do sh_ am broadcast -n $LAUNCHER/.TestNotifyReceiver --es kind widget --es cls BelleNotesWidget >/dev/null; sleep 0.7; done
saved() { sh_ cat $P | grep -o "name=\"$1\">[^<]*" | cut -d'>' -f2 | tr ',' '\n' | grep -c .; }
n_list=$(saved widget_items)
check "five widgets on the list ($n_list)" '[ "$n_list" = 5 ]'
pref_list home "Widgets layout" "Fixed screens (no scrolling)" >/dev/null
was_screens=$(pref_list home "Widget screens" "2")
home; widgets_pane; sleep 1
count() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'class="android.appwidget.AppWidgetHostView"' | grep -c .; }
top() { sh_ uiautomator dump /sdcard/u.xml >/dev/null 2>&1; sh_ cat /sdcard/u.xml | grep -o 'class="android.appwidget.AppWidgetHostView"[^>]*bounds="\[[0-9]*,[0-9]*' | head -1 | grep -o '[0-9]*$'; }
t0=$(top)
sh_ input swipe 540 1700 540 500 300; sleep 1; t1=$(top)
check "screen 1 does not scroll (first widget at $t0, then $t1)" '[ -n "$t0" ] && [ "$t0" = "$t1" ]'
sh_ input swipe 100 1200 1000 1200 300; sleep 1.5
check "a second widget screen to its left ($(home_pane))" '[ "$(home_pane)" = widgets ]'
a=$(saved widget_items); b=$(saved widget_items_1)
check "the widgets that did not fit moved there ($a + $b)" '[ "$a" -ge 1 ] && [ "$b" -ge 1 ] && [ $((a + b)) = 5 ] && [ "$(count)" = "$b" ]'
shot widgets_fixed_2
# Back to the list: every screen's widgets join it.
pref_list home "Widgets layout" "One scrolling list" >/dev/null
home; widgets_pane; sleep 1
n_back=$(saved widget_items)
check "as a list again it holds all of them ($n_back)" '[ "$n_back" = "$n_list" ] && ! sh_ cat $P | grep -q widget_items_1'
# Restore.
pref_list home "Widget screens" "${was_screens:-1}" >/dev/null
pref_list home "Widgets layout" "${was_layout:-One scrolling list}" >/dev/null
widgets_put /data/local/tmp/wfbak.xml
pref_switch home Widgets "$wp" >/dev/null; home

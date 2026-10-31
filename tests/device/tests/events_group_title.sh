# User 2026-10-27: two missed calls showed "2 new from Phone", as if Phone were a person. A group
# says what it is ("2 missed calls", "2 new messages", "2 <App> notifications"), never "from <app>".
L=~/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher
check "no \"new from <app>\" title" '! grep -q "new from \${appLabel" $L/EventsPage.kt'
check "missed calls are named" 'grep -q "missed calls" $L/EventsPage.kt && grep -q "CATEGORY_MISSED_CALL" $L/EventsPage.kt'
check "other groups: \"<App> notifications\"" 'grep -q "\${appLabel(items.first().pkg)} notifications" $L/EventsPage.kt'

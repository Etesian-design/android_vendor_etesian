# Edit-bar and Add widget buttons match the N9 (dark #1C1C1C ramp, #393839 rim, 41/72 of the bar), not outlined pills.
D=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher; [ -f $D/Util.kt ] || echo 'source missing'
check "source found" "[ -f $D/Util.kt ]"
check "dark ramp" "grep -q '0xFF1C1C1C.toInt(), 0xFF101010.toInt(), 0xFF080808' $D/Util.kt"
check "rim #393839" "grep -q '0xFF393839' $D/Util.kt"
check "no outlined Add widget pill" "! grep -q 'pill(\"Add widget\")' $D/WidgetsPage.kt"

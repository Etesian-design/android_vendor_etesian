# Album art is content: the Belle music widget must not squircle it (rule 2026-10-25).
SRC=/home/memphisx/android/lineage/vendor/etesian/launcher/app/src/main/java/com/bompotis/etesian/launcher/BelleWidgets.kt
check "music widget art not squircled" "! grep -q 'belle_art.*squircled' $SRC"
check "source found" "[ -f $SRC ]"

# Etesian app icons must be adaptive, or the launcher shrinks them into a white squircle (Feeds, 2026-10-25).
V=/home/memphisx/android/lineage/vendor/etesian
for m in $(ls $V/*/app/src/main/AndroidManifest.xml); do
  app=$(echo $m | cut -d/ -f8); icon=$(grep -o 'android:icon="@[a-z]*/[a-z_]*"' $m | head -1 | sed 's/.*@\([a-z]*\)\/\([a-z_]*\)"/\1 \2/')
  set -- $icon; res=$(dirname $m)/res
  check "$app icon adaptive ($1/$2)" "grep -qs adaptive-icon $res/$1-anydpi-v26/$2.xml $res/$1-anydpi/$2.xml $res/drawable/$2.xml"
done

# Back by swiping in is Disabled unless chosen; Breeze is off unless chosen; the toolbar's side strip is gone.
P=/data/data/$LAUNCHER/shared_prefs/com.bompotis.etesian.launcher_preferences.xml
b=$(sh_ cat $P | grep -o 'name="back_gesture">[a-z]*' | cut -d'>' -f2)
check "back gesture is disabled, left or right (got '${b:-unset}')" 'case "${b:-disabled}" in disabled|left|right) true;; *) false;; esac'
check "no toolbar side strip" '[ "$(sh_ settings get secure etesian_toolbar_mode)" != 1 ]'
check "launcher did not crash" '! crashed $LAUNCHER'

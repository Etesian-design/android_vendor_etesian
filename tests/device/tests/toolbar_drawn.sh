# Bug 2026-11-19: in Calendar and Gallery the N9 toolbar was in the window and took taps, but was never drawn
# (the decor pads its bottom for the navigation bar and clipped the bar sitting in that padding). The bar's
# buttons must be visible on screen: uiautomator only lists views that are drawn somewhere visible.
A() { command adb "$@"; }
SH=$(A shell wm size | grep -o '[0-9]*x[0-9]*' | head -1 | cut -dx -f2)
for p in org.lineageos.etar org.lineageos.glimpse com.android.deskclock com.android.contacts; do
  sh_ am force-stop $p; sh_ monkey -p $p -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
  n=$(A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; A shell cat /sdcard/u.xml | grep -o 'clickable="true"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]' | grep -o ',[0-9]*' | tr -d , | awk -v h=$SH '$1 >= h - 200 && $1 < h' | wc -l)
  A exec-out screencap -p > "$SHOTS/toolbar_$p.png"
  check "$p: the toolbar's buttons are drawn ($n)" '[ "$n" -ge 2 ]'
  sh_ am force-stop $p
done
home

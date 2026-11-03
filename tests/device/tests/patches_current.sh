# Host check: every vendor/etesian/patches/<project> patch holds ALL of that project's working-tree
# changes (incl. new files), so a repo sync + apply.sh can never lose work. Compares files, not patch
# bytes: a sync reorders new files and changes the index lines without changing what the patch makes.
T=~/android/lineage; W=/tmp/claude-1000/pc-wt
for d in $T/vendor/etesian/patches/*/; do
  proj=$(basename $d); path=$T/${proj//_//}; { [ "$proj" = tools ] || [ ! -d "$path" ]; } && continue
  rm -rf $W; git -C $path worktree add -q --detach $W HEAD && git -C $W apply --binary $d*.patch
  # The font list is rewritten by the private font import (import.sh), so it never matches the patch.
  check "patch current: $proj" '[ -z "$(diff -rq --exclude=.git $path $W | grep -v "fonts/etc/fonts_customization.xml")" ]'
  git -C $path worktree remove --force $W
done

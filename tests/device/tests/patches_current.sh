# Host check: every vendor/etesian/patches/<project> patch holds ALL of that project's working-tree
# changes (incl. new files), so a repo sync + apply.sh can never lose work.
T=~/android/lineage
for d in $T/vendor/etesian/patches/*/; do
  proj=$(basename $d); path=$T/${proj//_//}; [ -d "$path" ] || continue
  { git -C $path diff --binary; git -C $path ls-files -o --exclude-standard | while read f; do git -C $path diff --binary --no-index /dev/null "$f"; done; } > /tmp/claude-1000/pc.patch
  check "patch current: $proj" 'cmp -s /tmp/claude-1000/pc.patch $d*.patch'
done

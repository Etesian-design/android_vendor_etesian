#!/bin/bash
# Re-applies Etesian's changes to upstream LineageOS projects (after a repo sync).
set -e
TOP="$(cd "$(dirname "$0")/../../.." && pwd)"
for dir in "$(dirname "$0")"/*/; do
    proj=$(basename "$dir")                 # e.g. packages_apps_Dialer
    [ "$proj" = tools ] && continue        # the scripts that make some of the patches
    path="$TOP/${proj//_//}"                # -> packages/apps/Dialer
    for p in "$dir"*.patch; do
        if git -C "$path" apply --check "$p" 2>/dev/null; then
            git -C "$path" apply "$p" && echo "applied $(basename "$p") to ${proj//_//}"
        else
            echo "skipped $(basename "$p") on ${proj//_//} (already applied or conflicts)"
        fi
    done
done

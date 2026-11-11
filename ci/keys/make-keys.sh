#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Makes Etesian's release keys, once, on the DeskMini (ci/runner/README.md):  ci/keys/make-keys.sh
# - every key LineageOS signs with (apps, platform, OTA, verified boot) and one per APEX in the tree, in memory only;
# - packed and encrypted (gpg, AES-256) with a random passphrase into ${CI_DIR:-~/ci}/keys/etesian-keys.tar.gpg;
# - the passphrase is shown once: put it in the GitHub secret ETESIAN_KEYS_PASS and your password manager.
# Back the .gpg file up outside GitHub. Phones only take updates signed with the keys they were installed with: never replace
# these after the first release.
set -euo pipefail
CI="${CI_DIR:-$HOME/ci}"
SRC="$CI/pro1x/src"; OUT="${KEYS_OUT:-$CI/keys}"
SUBJECT="${SUBJECT:-/C=GR/O=Etesian/CN=Etesian}"
HERE="$(cd "$(dirname "$0")" && pwd)"
[ -x "$SRC/development/tools/make_key" ] || { echo "no LineageOS tree at $SRC" >&2; exit 1; }
[ ! -e "$OUT/etesian-keys.tar.gpg" ] || { echo "$OUT/etesian-keys.tar.gpg exists already; keys are made only once" >&2; exit 1; }
mkdir -p "$OUT"; chmod 700 "$OUT"

docker build -q -t etesian-rom-builder "$HERE/../builder" >/dev/null
PASS="$(openssl rand -base64 48 | tr -d '\n')"

printf '%s\n' "$PASS" | docker run --rm -i --network none --tmpfs /keys:size=64m,mode=1777 -e SUBJECT="$SUBJECT" \
    -v "$SRC:/src:ro" -v "$OUT:/out" etesian-rom-builder bash -euo pipefail -c '
IFS= read -r PASS
cd /keys
key() { printf "\n" | /src/development/tools/make_key "$1" "$SUBJECT" >/dev/null 2>&1 || [ -f "$1.pk8" ]; }
for k in releasekey platform shared media networkstack nfc bluetooth sdk_sandbox verifiedboot; do key "$k"; done
# One per APEX: named after the APEX (release.sh looks them up by that name). Test and sample modules are left out.
apexes=$(grep -rh --include=Android.bp -A2 -E "^apex_key \{" /src/packages /src/system /src/frameworks /src/art /src/external \
    /src/bionic /src/hardware /src/vendor 2>/dev/null | sed -n "s/.*name: \"\([^\"]*\)\".*/\1/p" \
  | sed -e "s/^apex\.\(.*\)\.key$/com.android.\1/" -e "s/\.key$//" \
  | grep -E "^com\.android\." | grep -v -E "test|sample|example|sdv|\.car\.|microfuchsia|overlaytest" | sort -u)
for a in $apexes; do
    key "$a"
    openssl genrsa -out "$a.pem" 4096 2>/dev/null
    python3 /src/external/avb/avbtool.py extract_public_key --key "$a.pem" --output "$a.avbpubkey"
done
n=$(ls *.pk8 | wc -l)
tar -c . | gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 --symmetric --cipher-algo AES256 \
    --s2k-digest-algo SHA512 --s2k-count 65011712 -o /out/etesian-keys.tar.gpg 3<<<"$PASS"
# The archive opens with the passphrase and holds every key.
m=$(gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 -d /out/etesian-keys.tar.gpg 3<<<"$PASS" | tar -t | grep -c "\.pk8$")
[ "$n" = "$m" ] || { echo "archive check failed ($m of $n keys)" >&2; rm -f /out/etesian-keys.tar.gpg; exit 1; }
echo "$n keys ($(echo "$apexes" | wc -w) of them APEX)"
echo "releasekey: $(openssl x509 -in releasekey.x509.pem -noout -fingerprint -sha256)"
'
chmod 600 "$OUT/etesian-keys.tar.gpg"
sha256sum "$OUT/etesian-keys.tar.gpg" | tee "$OUT/etesian-keys.tar.gpg.sha256"
cat <<EOF

The passphrase, shown only this once (GitHub secret ETESIAN_KEYS_PASS, and your password manager):

    $PASS

Then back up $OUT/etesian-keys.tar.gpg outside GitHub.
EOF

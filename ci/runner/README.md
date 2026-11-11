# The DeskMini runner

Etesian's release builds (the Pro1-X ROM here, the container image in [redroid](https://github.com/Etesian-design/redroid)) are
too big for GitHub's own runners, so they run on the DeskMini: one self-hosted runner for the Etesian-design organisation, one
job at a time, in a container that uses the host's Docker. Its trees, caches and the encrypted signing keys stay in `~/ci` on the
DeskMini; nothing of them goes to GitHub.

## Safety

All Etesian-design repositories are public. A self-hosted runner must never run code from a pull request: anyone can open one.

- The workflows that use it run only on a push to `main` or by hand (`workflow_dispatch`), never on `pull_request`.
- Organisation settings, Actions, Runner groups, Default: repository access **Selected repositories**, only
  `android_vendor_etesian` and `redroid`; **Allow public repositories** on (a group refuses public repositories otherwise).
- Organisation settings, Actions, General: **Require approval for all external contributors**.

## Setting it up (on the DeskMini, once)

1. Organisation settings, Actions, Runners, **New self-hosted runner**: copy the token from the `config.sh` line it shows (it
   expires within an hour).
2. In `~/ci/runner` (a copy of this folder):

   ```sh
   RUNNER_TOKEN=<token> DOCKER_GID=$(getent group docker | cut -d: -f3) docker compose up -d --build
   ```

   It registers as `deskmini` (labels `deskmini`, `android`) and keeps its registration in `state/`; later starts need no token.
   Docker starts it again after a reboot.

## Folders on the DeskMini

| Folder | What it holds |
|---|---|
| `~/ci/runner` | this folder and the runner's registration |
| `~/ci/_work` | the jobs' checkouts |
| `~/ci/pro1x/src`, `~/ci/pro1x/ccache` | the Pro1-X LineageOS tree and its compiler cache |
| `~/ci/keys` | the release keys, encrypted; the passphrase is the repository secret `ETESIAN_KEYS_PASS` |

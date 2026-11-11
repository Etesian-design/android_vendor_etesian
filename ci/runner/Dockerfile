# SPDX-License-Identifier: Apache-2.0
# GitHub's runner as the DeskMini's own user (uid 1000): the jobs' files on the host belong to that user, and the builds use the
# host's Docker (its socket is mounted). The runner updates itself when GitHub asks.
FROM ghcr.io/actions/actions-runner:2.338.2
USER root
RUN userdel -r ubuntu 2>/dev/null || userdel ubuntu; usermod -u 1000 runner && groupmod -g 1000 runner && chown -R runner:runner /home/runner
# gh: the release jobs upload their packages with it (GitHub's own apt repository)
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
       > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update && apt-get install -y --no-install-recommends gh && rm -rf /var/lib/apt/lists/*
USER runner

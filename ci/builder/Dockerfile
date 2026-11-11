# The build environment: LineageOS's build dependencies and repo, so the host needs only Docker.
FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      bc bison build-essential ca-certificates ccache curl flex g++-multilib gcc-multilib git git-lfs gnupg gperf \
      imagemagick lib32readline-dev lib32z1-dev libelf-dev liblz4-tool libsdl1.2-dev libssl-dev libxml2 libxml2-utils \
      lzop pngcrush python3 python-is-python3 rsync schedtool squashfs-tools xsltproc zip unzip zlib1g-dev fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o /usr/local/bin/repo && chmod +x /usr/local/bin/repo
ARG UID=1000
ARG GID=1000
RUN userdel -r ubuntu 2>/dev/null; groupadd -g $GID b && useradd -m -u $UID -g $GID b
USER b
RUN git config --global user.name builder && git config --global user.email builder@localhost && git config --global color.ui false
WORKDIR /src

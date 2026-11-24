#!/usr/bin/env bash
# Copyright 2026 deepcodecreate
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ARCH="${1:-arm64v8}"; SUITE="${SUITE:-bookworm}"
case "$ARCH" in
  arm64v8) ABI=arm64-v8a;;
  arm32v7) ABI=armeabi-v7a;;
  amd64) ABI=x86_64;;
  i386) ABI=x86;;
  *) echo "usage: fetch-rootfs.sh [arm64v8|arm32v7|amd64|i386]"; exit 1;;
esac
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="${OUT_DIR:-$HERE/../src/main/assets/debian/$ABI}"; mkdir -p "$DEST"
URL="https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-$ARCH/$SUITE/rootfs.tar.xz"
echo "Downloading $URL"
curl -fL "$URL" -o /tmp/rootfs.tar.xz
xz -dc /tmp/rootfs.tar.xz | gzip -9 > "$DEST/rootfs.tb"
rm -f /tmp/rootfs.tar.xz
ls -lh "$DEST/rootfs.tb"

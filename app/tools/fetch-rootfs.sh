#!/usr/bin/env bash
# Copyright 2026 deepcodecreate
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ARCH="${1:-arm64v8}"; SUITE="${SUITE:-bookworm}"
HERE="$(cd "$(dirname "$0")" && pwd)"; DEST="${OUT_DIR:-$HERE/../src/main/assets/debian}"; mkdir -p "$DEST"
URL="https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-$ARCH/$SUITE/rootfs.tar.xz"
echo "Downloading $URL"
curl -fL "$URL" -o /tmp/rootfs.tar.xz
xz -dc /tmp/rootfs.tar.xz | gzip -9 > "$DEST/rootfs.tb"
rm -f /tmp/rootfs.tar.xz
ls -lh "$DEST/rootfs.tb"

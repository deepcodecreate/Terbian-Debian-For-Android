#!/usr/bin/env bash
# Copyright 2026 deepcodecreate
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT_DIR:-$HERE/../src/main/jniLibs}"
WORK="${WORK:-$HERE/.build}"
API="${API:-24}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"
PROOT_REPO="https://github.com/termux/proot"
PROOT_REF="${PROOT_REF:-master}"
TERMUX_REPO="https://packages.termux.dev/apt/termux-main"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[ -d "$NDK" ] || { echo "Set ANDROID_NDK_HOME to your NDK directory"; exit 1; }
HOST_TAG="$(ls "$NDK/toolchains/llvm/prebuilt")"
TC="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"

mkdir -p "$WORK" "$OUT"
if [ ! -d "$WORK/proot/.git" ]; then
  git clone "$PROOT_REPO" "$WORK/proot"
fi
git -C "$WORK/proot" fetch --all -q
git -C "$WORK/proot" checkout -q "$PROOT_REF"
[ "$PROOT_REF" = master ] && git -C "$WORK/proot" pull -q --ff-only || true
echo "proot revision: $(git -C "$WORK/proot" rev-parse HEAD)"

triple() { case "$1" in
  arm64-v8a) echo aarch64-linux-android;; armeabi-v7a) echo armv7a-linux-androideabi;;
  x86_64) echo x86_64-linux-android;; x86) echo i686-linux-android;; esac; }
debarch() { case "$1" in
  arm64-v8a) echo aarch64;; armeabi-v7a) echo arm;; x86_64) echo x86_64;; x86) echo i686;; esac; }

fetch_talloc() {
  local abi="$1" da; da="$(debarch "$abi")"
  local dir="$WORK/talloc-$abi"
  [ -f "$dir/lib/libtalloc.a" ] && return
  mkdir -p "$dir/x" && cd "$dir"
  local idx="$TERMUX_REPO/dists/stable/main/binary-$da/Packages"
  local file
  file="$(curl -fsSL "$idx" | awk '/^Package: libtalloc-static$/{f=1} f&&/^Filename:/{print $2; exit}')"
  [ -n "$file" ] || { echo "libtalloc-static not found in Termux index ($da)"; exit 1; }
  curl -fsSL "$TERMUX_REPO/$file" -o t.deb
  (cd x && ar x ../t.deb && tar -xf data.tar.* )
  mkdir -p lib include
  find x -name 'libtalloc.a' -exec cp {} lib/ \;
  find x -name 'talloc.h'    -exec cp {} include/ \;
  [ -f lib/libtalloc.a ] && [ -f include/talloc.h ] || { echo "talloc extraction failed"; exit 1; }
  cd "$WORK"
}

for ABI in $ABIS; do
  echo "=== $ABI ==="
  T="$(triple "$ABI")"; fetch_talloc "$ABI"
  TD="$WORK/talloc-$ABI"
  B="$WORK/build-$ABI"; rm -rf "$B"; cp -r "$WORK/proot" "$B"
  CC="$TC/${T}${API}-clang"
  sed -i.bak 's|P_tmpdir|"/data/local/tmp"|g' "$B/src/path/temp.c" || true
  (
    cd "$B/src"
    export CC LD="$CC" STRIP="$TC/llvm-strip" OBJCOPY="$TC/llvm-objcopy" OBJDUMP="$TC/llvm-objdump" AR="$TC/llvm-ar"
    export CFLAGS="-I$TD/include -O2 -DARG_MAX=131072 -Wno-error"
    export CPPFLAGS="-I$TD/include -DARG_MAX=131072"
    export LDFLAGS="-L$TD/lib"
    export PKG_CONFIG=false
    make V=1 -j"$(nproc 2>/dev/null || echo 4)" \
         CC="$CC" LD="$CC" STRIP="$STRIP" OBJCOPY="$OBJCOPY" OBJDUMP="$OBJDUMP" \
         CFLAGS="$CFLAGS" CPPFLAGS="$CPPFLAGS" LDFLAGS="$LDFLAGS $TD/lib/libtalloc.a" \
         proot
  )
  mkdir -p "$OUT/$ABI"
  cp "$B/src/proot" "$OUT/$ABI/libproot.so"
  "$TC/llvm-strip" "$OUT/$ABI/libproot.so"
  L="$(find "$B/src" -name 'loader' -type f -perm -u+x | head -n1 || true)"
  [ -z "$L" ] && L="$(find "$B/src" -name 'loader.elf' -type f | head -n1 || true)"
  if [ -n "$L" ]; then cp "$L" "$OUT/$ABI/libloader.so"; "$TC/llvm-strip" "$OUT/$ABI/libloader.so" || true
  else echo "WARNING: loader binary not found for $ABI"; fi
  rm -f "$OUT/$ABI/libloader32.so"   # 32-bit loader is only valid on x86_64/arm64 hosts that can build -m32
  echo "built: $OUT/$ABI/libproot.so"
done
touch "$WORK/proot.done"
echo "Done."

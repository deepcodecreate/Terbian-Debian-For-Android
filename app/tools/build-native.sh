#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT_DIR:-$HERE/../src/main/jniLibs}"
API="${API:-24}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_ROOT:-}}}"
[ -d "$NDK" ] || { echo "Set ANDROID_NDK_HOME to your NDK directory"; exit 1; }
TC="$NDK/toolchains/llvm/prebuilt/$(ls "$NDK/toolchains/llvm/prebuilt" | head -n1)/bin"

triple() { case "$1" in
  arm64-v8a) echo aarch64-linux-android;; armeabi-v7a) echo armv7a-linux-androideabi;;
  x86_64) echo x86_64-linux-android;; x86) echo i686-linux-android;; esac; }

for ABI in $ABIS; do
  mkdir -p "$OUT/$ABI"
  if [ ! -f "$OUT/$ABI/libtermux.so" ]; then
    "$TC/$(triple "$ABI")${API}-clang" -shared -fPIC -O2 -Wl,-z,max-page-size=16384 \
      -o "$OUT/$ABI/libtermux.so" "$HERE/../src/main/jni-src/termux.c"
    "$TC/llvm-strip" "$OUT/$ABI/libtermux.so"
  fi
  if [ ! -f "$OUT/$ABI/libproot.so" ] || [ ! -f "$OUT/$ABI/libloader.so" ]; then
    ANDROID_NDK_HOME="$NDK" OUT_DIR="$OUT" ABIS="$ABI" API="$API" bash "$HERE/build-proot.sh"
  fi
  ls -l "$OUT/$ABI"
done

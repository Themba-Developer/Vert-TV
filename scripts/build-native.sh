#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
if ! command -v cargo >/dev/null; then
  echo 'Rust is required. Install rustup, then cargo install cargo-ndk --version 4.1.2 --locked.' >&2
  exit 1
fi
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] && [ -f "$root/local.properties" ]; then sdk="$(sed -n 's/^sdk.dir=//p' "$root/local.properties")"; fi
  export ANDROID_NDK_HOME="$sdk/ndk/28.2.13676358"
fi
if [ ! -d "$ANDROID_NDK_HOME" ]; then echo 'Install Android NDK 28.2.13676358 using Android Studio SDK Manager.' >&2; exit 1; fi
cd "$root/native/adblock_bridge"
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"
cargo ndk --platform 26 -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 -o "$root/app/src/main/jniLibs" build --release --locked
python3 "$root/scripts/rust-notices.py"

#!/usr/bin/env bash
# Builds and runs the C smoke test against the mingwX64-built licensecore.dll.
# Run from a shell where the Kotlin/Native MinGW toolchain is on disk (downloaded
# automatically by ./gradlew :core:linkReleaseSharedMingwX64 on first run).
set -euo pipefail

cd "$(dirname "$0")"

MINGW_BIN="$HOME/.konan/dependencies/msys2-mingw-w64-x86_64-2/bin"
DLL_DIR="../../core/build/bin/mingwX64/releaseShared"

"$MINGW_BIN/dlltool.exe" -d "$DLL_DIR/licensecore.def" -l "$DLL_DIR/licensecore.dll.a" -D licensecore.dll
"$MINGW_BIN/gcc.exe" -I"$DLL_DIR" -o smoke_test.exe smoke_test.c -L"$DLL_DIR" -llicensecore
cp "$DLL_DIR/licensecore.dll" .
./smoke_test.exe

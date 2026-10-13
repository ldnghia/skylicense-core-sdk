#!/usr/bin/env bash
# Packages the release LicenseCore.xcframework into a signed, checksummed zip for SwiftPM
# `binaryTarget(url:checksum:)` (Phase 9). Expects to run on the macos release.yml runner, AFTER:
#   ./gradlew :core:assembleLicenseCoreReleaseXCFramework
# which produces core/build/XCFrameworks/release/LicenseCore.xcframework (per Phase 4's
# `XCFramework("LicenseCore")` block in core/build.gradle.kts).
#
# Usage: ./scripts/package-ios-xcframework.sh <version> [apple-distribution-identity]
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?Usage: package-ios-xcframework.sh <version> [identity]}"
identity="${2:-}"

xcf_src="core/build/XCFrameworks/release/LicenseCore.xcframework"
if [ ! -d "$xcf_src" ]; then
    echo "LicenseCore.xcframework not found at $xcf_src - run './gradlew :core:assembleLicenseCoreReleaseXCFramework' first." >&2
    exit 1
fi

out_dir="build/packaging/ios"
mkdir -p "$out_dir"
rm -rf "$out_dir/LicenseCore.xcframework"
cp -R "$xcf_src" "$out_dir/LicenseCore.xcframework"

if [ -n "$identity" ]; then
    codesign --timestamp -s "$identity" "$out_dir/LicenseCore.xcframework"
    echo "Signed LicenseCore.xcframework with identity: $identity"
else
    echo "WARNING: no signing identity given - shipping LicenseCore.xcframework UNSIGNED." \
         "OK for an internal RC per Phase 9's risk-acceptance (Unresolved Questions); block GA on this." >&2
fi

zip_name="LicenseCore.xcframework.zip"
(cd "$out_dir" && zip -r -y "../../../$zip_name" "LicenseCore.xcframework" >/dev/null)

checksum=$(swift package compute-checksum "$zip_name")
echo "Packaged $zip_name"
echo "SwiftPM checksum: $checksum"

# release.yml consumes this to patch Package.swift's binaryTarget(url:, checksum:) before
# committing it on the release branch/tag - see docs/release-process.md.
echo "version=$version" >> "${GITHUB_OUTPUT:-/dev/stdout}"
echo "checksum=$checksum" >> "${GITHUB_OUTPUT:-/dev/stdout}"
echo "zip_name=$zip_name" >> "${GITHUB_OUTPUT:-/dev/stdout}"

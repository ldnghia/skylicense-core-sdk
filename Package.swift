// swift-tools-version:5.9
// LicenseCore SDK - SwiftPM distribution manifest (Phase 9).
//
// `url` and `checksum` below are placeholders, overwritten by `release.yml`'s macOS job after
// each tag: it runs `scripts/package-ios-xcframework.sh`, computes the real checksum with
// `swift package compute-checksum`, and commits the updated values here before/alongside
// publishing the GitHub Release. Never hand-edit these two fields for a real release - the
// checksum must match the exact zip attached to that release's GitHub Release assets, or SwiftPM
// consumers will fail to resolve the package.
//
// CocoaPods is intentionally not supported (trunk goes read-only 2026-12-02, see Phase 9 Context
// Links) - SwiftPM's binaryTarget is the only iOS distribution channel for this SDK.
import PackageDescription

let version = "0.0.0" // placeholder - see comment above
let checksum = "0000000000000000000000000000000000000000000000000000000000000000" // placeholder

let package = Package(
    name: "LicenseCore",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "LicenseCore", targets: ["LicenseCore"]),
    ],
    targets: [
        .binaryTarget(
            name: "LicenseCore",
            url: "https://github.com/ldnghia/skylicense-core-sdk/releases/download/v\(version)/LicenseCore.xcframework.zip",
            checksum: checksum
        ),
    ]
)

---
phase: 9
title: "Packaging and Distribution"
status: pending
priority: P2
effort: "3d"
dependencies: [8]
---

# Phase 9: Packaging and Distribution

## Context Links
- [Phase 4 XCFramework](./phase-04-ios-target-and-xcframework.md), [Phase 5 C ABI](./phase-05-windows-mingwx64-dll.md)
- CocoaPods trunk goes read-only 2026-12-02: https://blog.cocoapods.org/CocoaPods-Specs-Repo/ , https://nesbitt.io/2026/06/23/sunsetting-a-package-manager.html
- SwiftPM binary targets: https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages

## Overview
- Priority: P2. Status: pending. Parallel with Phase 10 (disjoint files).
- One version, one tag, three artifact channels: Maven (Android/KMP), SwiftPM binary XCFramework (iOS), zip (+ optional NuGet) for Windows x64. Signed, checksummed, produced by a tag-triggered release workflow.

## Key Insights
- **CocoaPods: skip.** Trunk read-only from 2026-12-02 -> no new pod versions. SwiftPM `binaryTarget(url:checksum:)` only.
- Maven: KMP publication via `maven-publish` publishes root metadata + `-android` AAR (+ native klibs, harmless). Repo target confirmed: GitHub Packages. `com.vanniktech.maven.publish` simplifies if Maven Central is ever needed (YAGNI now).
- Windows MSVC consumers need an import lib: maintain `packaging/windows/licensecore.def` (lists `lc_*`) and generate `licensecore.lib` with `lib.exe /def:licensecore.def /machine:x64` on the windows runner. MinGW consumers link the DLL directly. .NET needs neither.
- Authenticode signing of `licensecore.dll` strongly advised (SmartScreen/AV false positives). Needs code-signing cert (open question). XCFramework signing (`codesign` with Apple Distribution cert) recommended since Xcode 15 shows signature status.
- iOS privacy manifest: check whether framework needs `PrivacyInfo.xcprivacy` (uses Keychain only; no required-reason APIs expected - verify `NSUserDefaults`/file timestamp usage from Ktor/K/N runtime).
- Versioning: SemVer; `VERSION_NAME` in `gradle.properties` single source; C ABI version independent (`lc_abi_version`), bumped only on ABI break.
- Immutable releases: never overwrite a published version; rollback = consumers pin previous version.

## Requirements
- Functional: `git tag vX.Y.Z` -> CI publishes Maven artifact, GitHub Release with `LicenseCore.xcframework.zip` + checksum, `licensecore-win-x64-X.Y.Z.zip`; `Package.swift` updated with url+checksum.
- Non-functional: SHA-256 checksums file per release; reproducible from tag; secrets only in CI.

## Architecture
```
tag vX.Y.Z -> release.yml
  linux job  : publish Maven (core KMP publication)              -> Maven repo
  macos job  : assemble release XCFramework -> codesign -> zip -> swift package compute-checksum
               -> attach to GitHub Release -> commit Package.swift (url, checksum) on release branch
  windows job: linkReleaseSharedMingwX64 -> signtool sign -> lib.exe import lib
               -> zip {licensecore.dll, licensecore.lib, include/licensecore.h, LICENSE, README-windows.txt}
               -> nuget pack LicenseCore.Native.win-x64 (runtimes/win-x64/native/licensecore.dll)
  final job  : SHA256SUMS.txt -> GitHub Release notes
```

## Related Code Files (ownership: this phase; Phase 10 must not touch)
- Create: `.github/workflows/release.yml`, `Package.swift`, `scripts/package-ios-xcframework.sh`, `scripts/package-windows-dll.ps1`, `packaging/windows/licensecore.def`, `packaging/windows/README-windows.txt`, `docs/release-process.md`, `packaging/nuget/LicenseCore.Native.nuspec`
- Modify: `core/build.gradle.kts` (publishing block, POM metadata), `gradle.properties` (`VERSION_NAME`, `GROUP`) - only phase touching build files in this window

## Implementation Steps
1. Publishing config (group/artifact, POM, repo URL + credentials from env); dry run to `mavenLocal`, consume from sample Android app via coordinates.
2. iOS: release XCFramework -> codesign (if cert) -> zip -> checksum; `Package.swift` with `binaryTarget`; test consumption from a scratch Xcode project via SwiftPM URL.
3. Windows: `.def` + import lib generation; signtool (if cert); zip layout; verify .NET sample + C smoke work from the zip only.
4. NuGet: native package with `runtimes/win-x64/native/`; consume from .NET sample.
5. `release.yml` tying jobs together; release notes from `docs/project-changelog.md` (Phase 10 owns content).
6. `docs/release-process.md`: tagging, secrets list, rollback (yank = mark release deprecated, publish patch), key-rotation coordination step.
7. Rehearse: `v0.1.0-rc.1` end to end.

## Todo List
- [ ] Maven publication + local consumption test
- [ ] XCFramework zip + SwiftPM consumption test
- [ ] Windows zip (+ import lib, signing) consumption test
- [ ] NuGet package
- [ ] release.yml + release-process doc
- [ ] RC rehearsal

## Success Criteria
- [ ] `v0.1.0-rc.1` tag produces all artifacts with zero manual steps (besides approvals).
- [ ] Fresh Android app, fresh Xcode project (SwiftPM URL), fresh .NET app each integrate from published artifacts and activate against dev backend.
- [ ] `SHA256SUMS.txt` matches artifacts; DLL signature valid (`signtool verify /pa`) if cert available.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| No signing certs available in time | M x M | Ship RC unsigned internally; block GA on signing |
| SwiftPM checksum/URL mismatch after re-upload | L x H | Immutable assets; never re-upload same tag |
| Maven repo choice delays | M x L | Default GitHub Packages; switchable via env |

## Security Considerations
- Signing keys/certs in CI secret store only; release workflow restricted to protected tags.
- Checksums published; SwiftPM enforces checksum.
- Artifacts embed no environment secrets (public keys/base URL are consumer-provided config, not baked in).

## Rollback
Published versions immutable; rollback = consumers pin prior version; broken release -> patch release + deprecation note. Workflow revert via PR.

## Next Steps
GA release after Phase 10 docs merged.

## Unresolved Questions
Resolved (user, 2026-10-12): Maven repo = GitHub Packages (default accepted). NuGet package confirmed wanted (consumer stacks = .NET Framework + .NET 6, see Phase 5). Distribution = private/internal only (GitHub Release not public). Still open: code-signing certs (Authenticode + Apple Distribution) available? If not confirmed by GA, ship RC unsigned internally per Risk Assessment mitigation.

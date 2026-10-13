---
phase: 4
title: "iOS Target and XCFramework"
status: partial
priority: P1
effort: "3d"
dependencies: [2]
---

# Phase 4: iOS Target and XCFramework

## Context Links
- [Phase 2 architecture](./phase-02-common-core-license-client.md)
- Kotlin docs - Apple framework / XCFramework: https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html
- Ktor Darwin CertificatePinner: https://api.ktor.io/ktor-client-darwin/io.ktor.client.engine.darwin.certificates/-certificate-pinner/-certificate-pinner.html

## Overview
- Priority: P1. Status: pending. Parallel with Phases 3, 5.
- iOS `actual`s (Keychain storage, keychain-persisted device id, Darwin engine), XCFramework export, Swift-friendly API surface check, minimal SwiftUI sample.

## Key Insights
- **XCFramework Gradle syntax (believed current for Kotlin 2.x, verify in spike):**
  ```kotlin
  val xcf = XCFramework("LicenseCore")
  listOf(iosArm64(), iosSimulatorArm64()).forEach {
      it.binaries.framework { baseName = "LicenseCore"; isStatic = true; xcf.add(this) }
  }
  ```
  Tasks expected: `assembleLicenseCoreXCFramework`, `assembleLicenseCoreReleaseXCFramework`. Confirm via `./gradlew :core:tasks --all | grep -i xcframework` - do not hardcode in CI before confirming.
- **Static vs dynamic:** static framework = no embed step for consumers; risk if the host app already embeds another Kotlin/Native framework (duplicate K/N runtimes/symbols). Confirmed static (user, 2026-10-12); document limitation.
- **Device id:** `identifierForVendor` changes when all vendor apps are uninstalled -> would burn a MaxDevice slot on reinstall. Use a random UUID stored in Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, not synchronizable) - Keychain items survive app reinstall in practice; fallback to IDFV only if Keychain write fails.
- **Keychain in K/N tests:** simulator test binaries are unsigned/no entitlements -> Keychain calls may return `errSecMissingEntitlement (-34018)`. Mitigation: test Keychain via sample-app UI test host or XCTest in sample; keep `iosSimulatorArm64Test` for commonMain logic with in-memory storage. Spike first.
- **Swift API ergonomics:** suspend funcs export as async/completion-handler; `StateFlow` is awkward -> use `addStateListener` (`fun interface`). Sealed classes become class hierarchies (switch needs `as?` casts). SKIE skipped for v1 (user, 2026-10-12) - rely on `addStateListener` + default interop.
- Swift Export (direct Kotlin->Swift) is experimental - do not use.
- Use `platform.Security` / `platform.Foundation` (built-in K/N platform libs); no custom cinterop needed.

## Requirements
- Functional: XCFramework with device + simulator slices; Swift sample activates against dev backend; Keychain persistence across app restart.
- Non-functional: framework `baseName` stable (`LicenseCore`); public API reviewed from Swift perspective (names, nullability); binary size recorded.

## Architecture
```
PlatformContext (actual)     -> empty class (no state)
KeychainSecureStorage        -> kSecClassGenericPassword, service "com.dcorp.licensecore.<appSalt-hash>", account=key, AccessibleAfterFirstUnlockThisDeviceOnly; SecItemAdd / SecItemUpdate / SecItemCopyMatching / SecItemDelete via memScoped CFDictionary
IosDeviceIdProvider          -> Keychain "device-uuid" or create NSUUID; fallback IDFV
DarwinHttpEngineFactory      -> Darwin.create { } ; pinning hook Phase 7
```
Swift consumer flow: `LicenseClientFactory.create(config:context:)` -> `try await client.activate(licenseKey:)` -> switch on result -> `client.addStateListener { state in ... }`.

## Related Code Files (ownership: this phase only)
- Modify: `core/src/iosMain/kotlin/com/dcorp/licensecore/platform/PlatformModule.ios.kt`, `.../platform/PlatformContext.ios.kt`
- Create: `core/src/iosMain/kotlin/com/dcorp/licensecore/platform/KeychainSecureStorage.kt`, `.../IosDeviceIdProvider.kt`, `.../DarwinHttpEngineFactory.kt`
- Create: `samples/ios-sample/` (Xcode project `LicenseCoreSample.xcodeproj`, `LicenseCoreSampleApp.swift`, `ContentView.swift`, `LicenseViewModel.swift`, XCTest target `KeychainStorageTests.swift`)
- Create: `scripts/build-ios-xcframework.sh` (wraps Gradle task, outputs to `build/dist/ios/`)
- Build file: XCFramework block confirmed/edited by lead (single-owner rule)

## Implementation Steps
1. Spike (2h): XCFramework task names; Keychain access from `iosSimulatorArm64Test` (entitlement behavior). Report to lead.
2. `KeychainSecureStorage` (CF bridging with `memScoped`, `CFBridgingRetain/Release` correctness - leak check in Instruments once).
3. `IosDeviceIdProvider` with Keychain UUID.
4. `DarwinHttpEngineFactory`; `createPlatformModule` actual.
5. Build XCFramework (debug + release); inspect `Headers/LicenseCore.h` for Swift naming (`@ObjCName` where Kotlin names map badly).
6. SwiftUI sample: config via `.xcconfig` (gitignored) for base URL + PEM; Activate/Check/Deactivate; state label via listener.
7. XCTest in sample: Keychain round trip, device id stable across launches (persisted).
8. Record XCFramework size (device slice, release).

## Todo List
- [ ] Spike: tasks + Keychain test feasibility - NOT run; impossible on this Windows machine (Kotlin/Native iOS targets require a macOS host to compile at all, unlike Android which only lacks an SDK)
- [x] Keychain storage + device id written - `KeychainSecureStorage.kt`, `IosDeviceIdProvider.kt` - **UNVERIFIED, never compiled**
- [x] Darwin engine + module written - `DarwinHttpEngineFactory.kt`, `PlatformModule.ios.kt` now real (no more `TODO()`); added `cryptography-provider-apple` dependency (verified to exist on Maven Central, not verified to actually work)
- [ ] XCFramework built (release) + header review - blocked, no macOS host
- [ ] SwiftUI sample + XCTest - blocked, no macOS host
- [ ] Size recorded - blocked

## Success Criteria
All blocked pending a macOS host - none attempted:
- [ ] `scripts/build-ios-xcframework.sh` produces `LicenseCore.xcframework` with `ios-arm64` + `ios-arm64-simulator` slices.
- [ ] Sample on simulator + physical device: activate, relaunch offline -> `Valid(Offline)`; delete + reinstall app -> same `deviceId()`.
- [ ] XCTest Keychain tests pass on simulator.
- [ ] Swift call sites need no Kotlin-internal types (only public facade/results/states).

## What's Actually Verified Here
- Only a regression check: `./gradlew :core:mingwX64Test` still green (43/43) after adding the
  `cryptography-provider-apple` dependency and the new iosMain files - confirms this phase's
  changes didn't break the shared build or other targets.
- The iOS Kotlin/Native source itself has **zero compiler feedback** - Gradle cannot even attempt
  `compileKotlinIosArm64`/`compileKotlinIosSimulatorArm64` on a non-macOS host, so unlike Phase 3
  (Android, blocked only by a missing SDK on an otherwise-compatible OS), there is no way to
  partially validate this code from this machine at all. Treat `KeychainSecureStorage.kt` and
  `IosDeviceIdProvider.kt` as a best-effort draft following known-good Kotlin/Native Security-
  framework interop patterns, not as tested code - the plan's own Step 1 spike (Keychain
  entitlement behavior in K/N test binaries, exact `kSecXxx` constant typing) still needs to run
  on a real Mac before trusting this.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| Keychain unusable in K/N test runner | H x L | Test via sample XCTest host |
| Duplicate K/N runtime with other KMP frameworks in host app | M x M | Document; offer dynamic variant if requested |
| Swift ergonomics poor (sealed types, generics erased) | M x M | Facade-level design review; optional SKIE |
| XCFramework DSL changed in chosen Kotlin version | L x M | Spike step 1 |

## Security Considerations
- `ThisDeviceOnly` accessibility: no iCloud/backup migration of token/device id to other devices.
- No IDFA; no privacy-manifest-required API used beyond Keychain/UserDefaults - confirm whether `PrivacyInfo.xcprivacy` needed in framework (Phase 9 check).

## Rollback
Revert PR; other targets unaffected.

## Next Steps
Phase 7 pinning in `DarwinHttpEngineFactory.kt`; Phase 9 SwiftPM packaging.

## Unresolved Questions
Resolved (user, 2026-10-12): static framework confirmed; iOS min deployment target 14 confirmed; no `iosX64`; SKIE skipped for v1. Still open: will host apps embed other KMP frameworks (duplicate-runtime risk)?

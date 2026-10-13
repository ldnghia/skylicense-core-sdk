# iOS Integration Guide

> **Status caveat**: this guide describes the public API as written in
> `core/src/iosMain/kotlin/com/dcorp/licensecore/`. **None of this has ever been compiled** - Kotlin/Native
> iOS targets cannot be built on a non-macOS host, and no macOS host was available while this SDK
> was developed. `KeychainSecureStorage.kt` and `IosDeviceIdProvider.kt` both carry their own
> "never built" warnings in their source for the same reason - expect first-build Kotlin/Native
> Security-interop compile errors (a known rough edge for `kSecXxx` constant types) and budget a
> spike to fix them up on a real Mac before depending on this. There is no sample iOS app in
> `samples/` to link compiled snippets from; everything below is written directly from the public
> API source.

## Install (SwiftPM, once released - see `docs/release-process.md`)

```swift
// Package.swift, or Xcode: File > Add Package Dependencies...
.package(url: "https://github.com/ldnghia/skylicense-core-sdk", from: "0.1.0")
```

This resolves `Package.swift`'s `binaryTarget` pointing at a prebuilt, static
`LicenseCore.xcframework` (iOS device arm64 + simulator arm64 only - no `iosX64`, confirmed
decision). CocoaPods is **not supported** (CocoaPods trunk goes read-only 2026-12-02; SwiftPM is
the only distribution channel for this SDK).

The framework is static and built without SKIE - calling Kotlin suspend functions and sealed
classes from Swift uses default Kotlin/Native Objective-C interop conventions (completion-handler
style async, `__KotlinEnum`-style sealed class bridging), not SKIE's more idiomatic
`async`/`enum` wrappers. Budget extra interop glue code on your side for this v1.

## Configure and create a client

```swift
import LicenseCore

let config = LicenseConfig(
    baseUrl: "https://your-skyapp-api-host",   // must be https://
    orgId: 123,
    appSalt: "a-stable-per-app-secret",          // see "appSalt" warning below
    trustedSigningKeys: ["v1": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"],
    certificatePins: ["your-skyapp-api-host": ["base64-spki-sha256-pin-1", "base64-spki-sha256-pin-2"]]
    // omit certificatePins (or pass [:]) to disable pinning
)

let client = LicenseClientFactory().create(context: PlatformContext(), config: config)
```

**`appSalt` must never change** once set for a given install - it's mixed into the hashed device
id sent to the backend. Changing it makes the backend see what looks like a brand-new device,
consuming another `MaxDevice` slot.

## Lifecycle

The exact Swift call shape for Kotlin `suspend fun`s depends on your Kotlin/Native version's
default interop (typically a trailing completion handler, or `async`/`await` via Xcode 15+'s
Swift concurrency bridging for suspend functions - check what your actual Kotlin version
generates, since this was never compiled to confirm):

```swift
// On app start:
client.restore { state, error in /* ... */ }      // or `let state = try await client.restore()`

client.start(scope: /* your CoroutineScope bridge */)

// Activation:
client.activate(licenseKey: licenseKey) { state, error in renderUi(state) }
// or
client.activateForStore(storeId: 42, appId: 7, accProductId: nil) { state, error in /* ... */ }

// Observe state (StateFlow) - bridge via Kotlin/Native's Flow-to-Combine/AsyncSequence pattern of
// your choice; there is no first-party SwiftUI wrapper in this SDK.

client.checkNow { state, error in /* ... */ }

client.deactivate { state, error in /* ... */ }
```

See `docs/license-lifecycle-and-offline-grace.md` for what each state means and the exact offline
grace timing, and `docs/error-codes-reference.md` for `Invalid.reason` -> UX mapping.

## Admin-mediated offline activation

```swift
let deviceId = try await client.deviceId()   // hand this to your admin tooling
// ... later, once you have the JWT:
client.importOfflineToken(jwt: jwt) { state, error in /* ... */ }
```

## Platform-specific notes

- **Secure storage**: `KeychainSecureStorage` stores a generic-password Keychain item per cache
  key, with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` - not synced to iCloud Keychain, not
  migrated by a device backup/restore onto a different device. This matches the token's `did`
  claim check (a restored cache on a different device would be rejected anyway), so no extra
  backup-exclusion configuration is needed on your end.
- **Device id**: a random UUID generated once and stored in the Keychain (survives app
  reinstall in practice, unlike `identifierForVendor`, which resets when every app from your
  vendor is uninstalled). Falls back to `identifierForVendor` only if the Keychain write itself
  fails.
- **Certificate pinning**: wired via Ktor Darwin's `CertificatePinner`/`handleChallenge` when
  `certificatePins` is non-empty. Always configure at least 2 pins (current + backup) - see the
  ops runbook for rotation ordering.
- **Duplicate Kotlin/Native runtime risk**: if your app embeds more than one Kotlin/Native
  framework (this SDK plus another KMP library), check for duplicate-runtime/duplicate-symbol
  linker issues - a known category of problem for apps with multiple K/N dependencies, not
  something this SDK can prevent on its own. Untested in this project (open item, Phase 4 of the
  build plan).
- **Privacy manifest**: this SDK uses the Keychain (declared "required reason" API category under
  Apple's privacy manifest rules) and otherwise no other required-reason APIs are expected from
  its own code or its Ktor/Kotlin-Native runtime dependencies - **not independently verified**;
  confirm your app's aggregated `PrivacyInfo.xcprivacy` covers this before App Store submission.
- **Threading**: suspend functions do not block an arbitrary thread - don't call them
  synchronously from the main thread without `await`/a completion handler.

## Known gaps (be honest with yourself before shipping)

- Nothing in `iosMain` has ever been compiled. Treat the Keychain interop code as a known source
  of first-build errors, not a guarantee.
- No SwiftPM/Xcode consumption has ever been attempted. Rehearse this (a scratch Xcode project
  pulling the package by URL) before depending on it for a release - this is Phase 9's own open
  "RC rehearsal" item.

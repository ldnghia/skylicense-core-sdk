---
phase: 3
title: "Android Target"
status: partial
priority: P1
effort: "2d"
dependencies: [2]
---

# Phase 3: Android Target

## Context Links
- [Phase 2 architecture](./phase-02-common-core-license-client.md) (interfaces `SecureStorage`, `DeviceIdProvider`, `PlatformModule`)
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android KMP plugin: https://developer.android.com/kotlin/multiplatform/plugin

## Overview
- Priority: P1. Status: pending. Runs in parallel with Phases 4 and 5.
- `actual` implementations for Android: Keystore-backed secure storage, ANDROID_ID-based device id, OkHttp engine wiring, `PlatformContext` wrapping `Context`. Minimal sample app.

## Key Insights
- **`EncryptedSharedPreferences` (androidx.security:security-crypto) is deprecated** (2025). The PM doc's "EncryptedSharedPreferences-equivalent" => use Android Keystore AES-256-GCM key (non-exportable) + ciphertext stored in a private `SharedPreferences` file. No extra dependency.
- `Settings.Secure.ANDROID_ID`: since Android 8, scoped per (app signing key, user, device); survives reinstall; resets on factory reset. Good fit for slot binding (reinstall does not burn a MaxDevice slot). Same signing key across vendor apps => same id (fine; hashed with per-app salt anyway).
- StrongBox optional: use if available (`setIsStrongBoxBacked`) with fallback - do not require.
- Keystore keys can be invalidated (e.g., lock-screen changes on some OEMs if user-auth bound - we do NOT bind to user auth) -> on `KeyPermanentlyInvalidatedException`/decrypt failure: wipe + treat as no cache (online re-verify).
- No WorkManager: re-check runs only while app process alive (license gating is in-app). YAGNI.
- Sample module must be a separate `com.android.application` module (AGP 9 rule: app entry points not in KMP library module).

## Requirements
- Functional: `createPlatformModule(PlatformContext(context), config)` returns working storage/device id/engine; sample activates a real key against dev skyapp-api.
- Non-functional: no new runtime deps besides Ktor OkHttp engine; minSdk per Phase 1 decision; R8/ProGuard consumer rules shipped for kotlinx-serialization.

## Architecture
```
PlatformContext(actual)  -> holds applicationContext
KeystoreSecureStorage    -> alias "licensecore.<appSalt-hash>", AES/GCM/NoPadding, 12-byte IV prefixed to ciphertext, Base64 into SharedPreferences("licensecore_secure")
AndroidDeviceIdProvider  -> Settings.Secure.ANDROID_ID ; null/"9774d56d682e549c"(known bad) -> fallback random UUID persisted in KeystoreSecureStorage
OkHttp engine factory    -> Ktor OkHttp engine; pinning hook left for Phase 7
```
Data flow: commonMain calls `storage.put("record", jsonBytes)` -> encrypt with Keystore key -> SharedPreferences commit (apply is async; use `commit()` on IO dispatcher for durability).

## Related Code Files (ownership: this phase only)
- Modify: `core/src/androidMain/kotlin/com/dcorp/licensecore/platform/PlatformModule.android.kt`, `.../platform/PlatformContext.android.kt` (stubs from Phase 2)
- Create: `core/src/androidMain/kotlin/com/dcorp/licensecore/platform/KeystoreSecureStorage.kt`, `.../platform/AndroidDeviceIdProvider.kt`, `.../platform/AndroidHttpEngineFactory.kt`
- Create: `core/src/androidMain/consumer-rules.pro` (path per AGP 9 KMP plugin convention - verify)
- Create tests: `core/src/androidDeviceTest/...` or `androidInstrumentedTest` (source-set name depends on AGP 9 KMP plugin - verify): `KeystoreSecureStorageInstrumentedTest.kt`, `AndroidDeviceIdProviderInstrumentedTest.kt`
- Create: `samples/android-sample/` (`build.gradle.kts`, `src/main/AndroidManifest.xml`, `MainActivity.kt`, layout)
- Build file changes (dependency/plugin for instrumented tests, `include(":samples:android-sample")`): request via lead (single-owner rule)

## Implementation Steps
1. `PlatformContext` actual wrapping `applicationContext`.
2. `KeystoreSecureStorage`: get-or-create AES key; encrypt/decrypt; on any crypto failure -> remove entry, return null; `commit()` writes.
3. `AndroidDeviceIdProvider` with bad-value fallback.
4. `AndroidHttpEngineFactory` returning `OkHttp.create { }` with config hook.
5. `createPlatformModule` actual wiring.
6. Instrumented tests: storage round trip, key-loss recovery (delete key -> get returns null, no crash), device id stable across two provider instances.
7. Sample app: input license key, Activate/Check/Deactivate buttons, state text bound to `addStateListener`; base URL + public key PEM from `BuildConfig` (local.properties, not committed).
8. Consumer ProGuard rules for serialization; build sample in `release` with R8 and run activation.

## Todo List
- [x] PlatformContext + Keystore storage - `KeystoreSecureStorage.kt` (AES-256-GCM, non-exportable key, fail-soft on any crypto error)
- [x] Device id provider - `AndroidDeviceIdProvider.kt` (ANDROID_ID + known-bad-value + UUID fallback)
- [x] OkHttp engine factory + module wiring - `AndroidHttpEngineFactory.kt`, `PlatformModule.android.kt` now real (no more `TODO()`)
- [ ] Instrumented tests on emulator - NOT run; this dev machine has no Android SDK (`ANDROID_HOME` points to a non-existent path, same gap as Phase 1). `:core:compileAndroidMain` confirmed blocked only by "SDK location not found", not a script/syntax error - the Gradle script itself is valid.
- [ ] Sample app - NOT built; needs a real device/emulator to be worth building, deferred until SDK is available

## Success Criteria
- [ ] Instrumented tests pass on emulator at minSdk and latest API - blocked, no SDK in this environment.
- [ ] Sample: activate -> kill app -> airplane mode -> relaunch -> ... - blocked, no sample app built yet.
- [ ] Sample release (R8) build works (no serialization stripping) - blocked; also `consumerProguardFiles(...)` is NOT a recognized property on this AGP 9 KMP `android {}` DSL (confirmed by a real build error) - the correct wiring for `consumer-rules.pro` is still unresolved, flagged in `core/build.gradle.kts`.
- [ ] Reinstall sample -> same `deviceId()` -> ... - blocked, no sample app / device.

## What's Actually Verified Here
- The Kotlin source compiles logically (reviewed by hand against the Android Keystore/ANDROID_ID APIs) and the Gradle script parses and reaches real dependency resolution (fails only at "SDK location not found", confirmed via a real `./gradlew :core:compileAndroidMain` run).
- mingwX64 regression-tested after these changes - still green (43/43), so nothing here broke the shared build.
- None of the Android-specific code has actually executed on a JVM/device - needs a real Android SDK install to verify the Keystore/SharedPreferences logic compiles and behaves as written.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| OEM Keystore bugs | M x M | Fail-soft to "no cache" -> online re-verify |
| ANDROID_ID null on some devices/emulators | L x M | UUID fallback persisted |
| AGP 9 test source-set naming confusion | M x L | Verify on scaffold; document in README |

## Security Considerations
- Key non-exportable; ciphertext in app-private prefs; `android:allowBackup` guidance in docs (exclude `licensecore_secure` from backup so cache isn't restored onto another device - `did` check would reject anyway).
- No raw ANDROID_ID sent; hashed in commonMain.

## Rollback
Revert PR; commonMain unaffected (stubs return to `TODO()` - Android target fails at runtime only, other platforms unaffected).

## Next Steps
Phase 7 adds OkHttp certificate pinning in `AndroidHttpEngineFactory.kt`.

## Unresolved Questions
Resolved (user, 2026-10-12): minSdk 24 confirmed. Still open: do any consumers run multi-process apps sharing the license cache?

# Project Roadmap

Current state and known gaps, by platform and by phase. See
`plans/261012-1520-license-core-sdk-kmp-implementation/plan.md` for the full phase-by-phase build
plan and history; this doc is the user-facing summary of "what works today, what doesn't yet".

## Platform status

| Platform | Status | Verified how |
|---|---|---|
| Android | Code complete, **unverified** | No Android SDK available during development; never built or run |
| iOS (arm64 device + simulator) | Code complete, **unverified** | No macOS host available during development; never compiled |
| Windows x64 | Done, verified | Built and tested on real Windows CI runners (unit tests + C ABI smoke test) |
| Windows x86 | **Deferred, not started** | Planned design: a separate `license-agent.exe` helper process communicating over a named pipe, so a 32-bit host process can use the (64-bit-only) Kotlin/Native DLL indirectly. Revisit only on explicit request - no work has begun |
| Windows ARM64 | **Not supported** | Kotlin/Native has no Windows ARM64 compilation target. Running the x64 DLL under x64 emulation on ARM64 Windows is architecturally plausible but untested - do not promise this |

## What's solid

- Core license logic (activation, token verification, caching, offline grace, revocation, clock
  rollback guard): fully implemented and unit-tested in `commonTest`, platform-independent.
- Windows target: the only platform with real, verified builds and tests in CI.
- Security posture: a manual security review found no critical/high issues (see
  `docs/security-threat-model.md`); certificate pinning works (by code review) on Android/iOS and
  is a documented, accepted gap on Windows.

## What's not yet verified (needs a real build environment, not more code)

- **Android**: compile against a real Android SDK; run the instrumented tests that exercise
  `KeystoreSecureStorage` and `AndroidDeviceIdProvider` for real; confirm certificate pinning
  actually rejects a wrong pin; resolve the `consumer-rules.pro`/R8 wiring gap (see
  `docs/integration-guide-android.md`).
- **iOS**: compile on a real macOS host (expect first-try Kotlin/Native Security-interop errors in
  `KeychainSecureStorage.kt`/`IosDeviceIdProvider.kt`, both already flagged in their own source);
  run the XCTest suite in the (not-yet-created) sample app; confirm pinning; check for
  duplicate-Kotlin/Native-runtime linker issues if embedded alongside other KMP libraries.
- **Integration tests against a real backend**: `:integration-tests` module exists with tests for
  activate/find/error-codes/device-limit/offline-transition on `mingwX64Test`, but needs: fixture
  licenses provisioned on a real dev/staging `skyapp-api` instance, golden response fixtures
  captured from live calls (the current `GoldenResponses.kt` reflects assumptions, not captured
  data), and the same test classes written for android-host/iosSimulatorArm64.
- **Packaging/release**: Maven publish, SwiftPM XCFramework distribution, Windows zip/NuGet, and
  the tag-triggered release workflow are all written (see `docs/release-process.md`) but never
  run end-to-end - the "RC rehearsal" is the next concrete step before any real release.

## Deferred / explicitly out of scope for v1

- **Windows x86 helper process** (`license-agent.exe`): deferred per an explicit product decision,
  not started.
- **Binary tamper resistance** (obfuscation, anti-debug, integrity checks): explicitly out of
  scope for v1 (product decision) - a sufficiently motivated attacker can patch the compiled
  binary/DLL to bypass checks. No control in this SDK defends against that.
- **SKIE** (idiomatic Swift bindings for Kotlin suspend/sealed types): skipped for v1; iOS
  consumers get default Kotlin/Native Objective-C interop only.

## Cross-repo recommendations (not fixable from this repo alone)

These require changes in `skyapp-api`, tracked here so they aren't lost, not acted on by this SDK
repo:

1. A public signing-key/JWKS endpoint, to remove the manual, deploy-ordered key-rotation process
   documented in `docs/ops-signing-keys-and-pins-runbook.md`.
2. Admin authentication on `/{id}/devices`, `/devices/release` (admin path), `/token/revoke`, and
   `POST /settings` - these are currently anonymous/unauthenticated endpoints.
3. Put `allow_offline` and the check interval into the signed token claims directly, rather than a
   separate anonymous `/settings` endpoint that needs `orgId` (which the verify response doesn't
   carry).
4. Serialize backend DateTime fields with an explicit UTC offset.

None of these block v1 shipping (confirmed decision) - they're quality-of-life and defense-in-depth
improvements for later.

## Next steps, in order

1. Get Android building on a real SDK; run instrumented tests; close the R8 gap.
2. Get iOS compiling on a real macOS host; fix up Keychain interop; run XCTest.
3. Provision real fixture licenses; capture real golden response fixtures; finish integration
   tests on all 3 targets.
4. Rehearse an RC release end-to-end (`v0.1.0-rc.1`) through the real `release.yml` workflow.
5. GA, once the above are all green.

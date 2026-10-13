# Release Candidate E2E Checklist

Run manually, on real devices/machines, before tagging a release candidate. Automated
unit/integration tests (Phase 2/7/8) cover the logic; this checklist covers what only a real
device + real backend + real human can catch (UI wiring, OS-level storage behavior, actual
network conditions).

## Prerequisites

- Dev/staging `skyapp-api` instance reachable, with a fixture license this run can activate and
  release freely (see `LICENSE_IT_*` env vars in `integration-tests/`).
- Trusted signing key PEM for that environment's current `kid`.

## Windows (sample: `samples/windows-c-smoke`, or a .NET host once built)

- [ ] `lc_create` with the real config succeeds; `lc_activate` with the fixture key returns `VALID`.
- [ ] Kill the process, relaunch, `lc_get_state` immediately after `lc_create`+cache load shows `VALID_CACHED`.
- [ ] Disconnect network, `lc_check_now` -> `OFFLINE` with `offlineGraceUntilEpochSeconds` in the future.
- [ ] Wait past that offline grace window (or use a short-lived fixture token) with network still
      down -> `NETWORK_REQUIRED`.
- [ ] Reconnect network, `lc_check_now` -> back to `VALID`.
- [ ] `lc_deactivate` -> `UNLICENSED`; re-`lc_activate` with the same key succeeds again (device
      slot was actually released server-side, not just locally).
- [ ] Copy `%LOCALAPPDATA%\LicenseCore\...` to a different Windows user account on the same
      machine (different DPAPI user scope) -> `lc_get_state` after `lc_create` there reports no
      cache (DPAPI decrypt fails as expected, not a crash).

## Android (sample app, once built - Phase 3 follow-up)

- [ ] Activate -> kill app -> airplane mode -> relaunch -> `Valid(Cached)` or `Valid(Offline)`
      until the cached token's `exp`.
- [ ] Uninstall + reinstall the app -> `deviceId()` is unchanged (ANDROID_ID survives reinstall) ->
      re-activating does NOT consume a second `MaxDevice` slot (check the `LicenseDevice` table
      row count in the backend for this license).
- [ ] R8/ProGuard release build: activation still works (kotlinx.serialization survives
      minification) - **blocked today**: `consumerProguardFiles(...)` isn't wired on the AGP 9 KMP
      `android {}` DSL yet (Phase 3 finding); confirm the right API before trusting this.
- [ ] Revoke the device from an admin action on the backend -> next periodic check shows `Revoked`.

## iOS (sample app, once built - Phase 4 follow-up)

- [ ] Activate -> background the app -> airplane mode -> relaunch -> `Valid(Cached/Offline)` until
      token `exp`.
- [ ] Delete + reinstall the app -> `deviceId()` unchanged (Keychain item survives reinstall,
      `AfterFirstUnlockThisDeviceOnly`) -> no new `MaxDevice` slot consumed.
- [ ] Revoke from the backend -> next check shows `Revoked`.

## Cross-platform

- [ ] Wrong/stale `trustedSigningKeys` (simulating a missed key-rotation deploy) -> every
      `activate`/refresh shows `Invalid(TokenUntrusted)`, never a crash.
- [ ] Malformed/truncated JSON from a deliberately broken proxy -> no crash, surfaces as a
      transport or invalid-token state depending on where it breaks.

## Known gaps at time of writing

- Android and iOS sample apps don't exist yet (Phase 3/4 both stopped at the SDK-code layer - no
  SDK/macOS available in that session to build them).
- Android/iOS checklist items above are therefore unverified on a real device as of this writing;
  they describe the intended behavior per the SDK's design, not confirmed results.

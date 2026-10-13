---
phase: 8
title: "Integration Testing and CI Matrix"
status: partial
priority: P1
effort: "3d"
dependencies: [7]
---

# Phase 8: Integration Testing and CI Matrix

## Context Links
- Unit tests already delivered per phase: Phase 2 (`commonTest`), 3 (Android instrumented), 4 (iOS XCTest in sample), 5 (`mingwX64Test` + C smoke), 7 (edge cases).
- Backend contracts: `F:\Project\skyapp-api\Presentation\Web.Api\Controllers\LicenseController.cs:98-144`, envelope `Libraries/Core/ApiResponse.cs:5-37`
- CI skeleton: `.github/workflows/ci.yml` (Phase 1)

## Overview
- Priority: P1. Status: pending.
- Adds (1) `:integration-tests` KMP module hitting a real skyapp-api dev/staging instance, (2) golden contract fixtures captured from that instance, (3) full CI matrix + coverage gate, (4) release-candidate manual E2E checklist per platform.

## Key Insights
- Standing up skyapp-api in SDK CI is heavy (DB + Redis per `LicenseController.cs:4` `using StackExchange.Redis`) -> use a shared dev/staging environment with pre-provisioned fixture licenses. Stubbed backend already covered by Ktor `MockEngine` in unit tests - no second stub server (DRY/YAGNI).
- Don't rely on anonymous admin endpoints (create license, bind with lifetime) in tests - they should get auth later (cross-repo rec). Fixture licenses provisioned once by an admin.
- Device slots are shared state: each run uses unique synthetic device ids (`it-<runId>-<target>`), teardown releases them; the `MaxDevice=1` limit test runs on ONE target only (android host) to avoid cross-job races.
- K/N test env vars: mingwX64 reads process env; iOS simulator tests need `SIMCTL_CHILD_` prefix - research gap, verify. Fallback: generate a Kotlin constants file from env at build time (gitignored).
- Integration tests must fail (not skip) when the env is configured in CI but unreachable; skip only when not configured (local dev).

## Requirements
- Functional: integration suite covers activate, find, introspect, refresh, release, revoke, error codes 1/13/14/16/22, offline transition (by stopping network via engine-level fault injection), on android-host + iosSimulatorArm64 + mingwX64.
- Non-functional: PR pipeline < 25 min; integration on `main` + nightly; coverage >= 85% commonMain enforced.

## Test Matrix
| Layer | What | Where | Trigger |
|-------|------|-------|---------|
| Unit | policy, verifier, API mapping (MockEngine), scheduler (virtual time), cache | `core/src/commonTest` on 3 targets | every PR |
| Platform unit | DPAPI, registry id, C ABI | `core/src/mingwX64Test` | every PR (windows) |
| Platform device | Keystore, ANDROID_ID | Android instrumented (emulator) | every PR (linux, emulator) or nightly if slow |
| Platform device | Keychain, device uuid | iOS sample XCTest | every PR (macos) |
| ABI smoke | `lc_*` exports from C | `samples/windows-c-smoke` | every PR (windows) |
| Contract | golden JSON parsed by DTOs | `core/src/commonTest/.../contract/` | every PR |
| Integration | real backend flows | `:integration-tests` | main + nightly + manual |
| E2E manual | sample apps on real devices | checklist below | each release candidate |

## Architecture
```
integration-tests/build.gradle.kts   (KMP: android host, iosSimulatorArm64, mingwX64; depends on :core)
integration-tests/src/commonTest/kotlin/com/dcorp/licensecore/it/
  IntegrationEnv.kt            # baseUrl, fixture keys, public keys PEM, runId (expect/actual env read)
  ActivateFlowIT.kt            # verify happy path, token verifies locally with configured PEM
  FindFlowIT.kt                # /find with org/store fixtures
  RevocationFlowIT.kt          # release -> client state Revoked; admin token/revoke simulated via raw Ktor call in test -> next check Revoked
  ErrorCodesIT.kt              # unknown key(1), inactive(13), expired(14), no license(22)
  DeviceLimitIT.kt             # MaxDevice=1 fixture, android host only
  OfflineTransitionIT.kt       # activate online, then fault-injecting engine -> Offline(until == real token exp); crossing exp is covered by unit tests with virtual clock (no 30+ min waits)
  ContractFixtureCaptureIT.kt  # optional task: dumps live responses to refresh golden fixtures
```
Secrets: `LICENSE_IT_BASE_URL`, `LICENSE_IT_FIXTURES_JSON` (keys/org/store ids), `LICENSE_IT_SIGNING_KEYS_JSON` (kid->PEM) as CI secrets.

## Related Code Files (ownership: this phase)
- Create: `integration-tests/**` (above), `core/src/commonTest/kotlin/com/dcorp/licensecore/contract/GoldenResponsesTest.kt`, `.../contract/GoldenResponses.kt`
- Modify: `.github/workflows/ci.yml` (matrix, coverage, integration job), `settings.gradle.kts` (`include(":integration-tests")`) - sequential phase, no conflicts
- Create: `docs/release-e2e-checklist.md`

## Implementation Steps
1. Agree fixture licenses with backend team (see questions): active key, inactive, expired, MaxDevice=1, store-pool for `/find` (org/store/app ids).
2. Capture live responses (verify ok, each error, introspect, settings) -> `GoldenResponses.kt`; confirm camelCase + date format assumptions from Phase 2 Key Insights; fix DTOs if wrong.
3. Build `:integration-tests` module + env reading per target.
4. Implement IT classes; teardown releases all `it-<runId>-*` devices (best effort) + nightly cleanup step.
5. CI: PR = unit (3 OS) + C smoke + instrumented (or nightly) + Kover gate; main/nightly = integration on 3 targets; concurrency group to avoid parallel nightly runs.
6. Write `docs/release-e2e-checklist.md` (per platform: activate, restart offline, revoke from admin UI, deactivate, reinstall same device id, wrong pin).

## Todo List
- [ ] Fixture licenses provisioned - owned by user (Nghia LD) per Unresolved Questions; not done this session
- [ ] Golden fixtures captured + DTO assumptions confirmed - `GoldenResponses.kt`/`GoldenResponsesTest.kt` exist (from a prior session) but reflect assumed, not live-captured, responses; needs a real backend run to confirm
- [x] Integration module + ITs - `:integration-tests` module + `IntegrationEnv`/`IntegrationClient` (prior session) plus `ActivateFlowIT`, `ErrorCodesIT`, `FindFlowIT`, `RevocationFlowIT` (prior session) and `DeviceLimitIT`, `OfflineTransitionIT` (this session) on `mingwX64Test`; android-host/`iosSimulatorArm64` variants not written (no SDK/macOS this or prior session)
- [x] CI matrix + coverage gate - `.github/workflows/ci.yml` (prior session) has the 3-OS unit matrix + a separate `integration` job (main/nightly, skip-if-unconfigured); this session added Kover (`core/build.gradle.kts`, `:core:koverVerify`, >=85% commonMain line coverage) but did NOT wire it into the CI workflow yet (follow-up) or verify it runs (Google Maven blocked in this sandbox - see Unresolved Questions)
- [x] E2E checklist doc - `docs/release-e2e-checklist.md` (prior session)

## Success Criteria
- [ ] Integration suite green on android-host, iosSimulatorArm64, mingwX64 against dev env, 3 consecutive nightly runs (flake check).
- [ ] Golden fixtures prove (or correct) camelCase + date-offset assumptions.
- [ ] Kover: commonMain line coverage >= 85%, enforced in CI.
- [ ] No leaked device rows after a run (query count of `it-<runId>-%` = 0).

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| Shared dev env unstable -> flaky CI | M x M | Integration not PR-blocking; retries only on transport errors; health check first |
| Fixture license mutated by humans | M x M | Dedicated test org/store, documented "do not touch" |
| Device-slot races between jobs | M x M | Unique device ids; limit test on one target |
| Simulator env var passing | M x L | `SIMCTL_CHILD_` or generated constants |

## Security Considerations
- Fixture keys/PEMs only in CI secrets; never committed; generated constants file gitignored.
- Test org isolated from customer data.

## Rollback
Integration job can be disabled independently (workflow flag); no product code changed.

## Next Steps
Phase 9 packaging; Phase 10 docs (parallel).

## Unresolved Questions
Resolved (user, 2026-10-12): dev/staging skyapp-api environment + fixture-license/test-org provisioning owned by the user (Nghia LD). Still open: Android instrumented tests on every PR (slow emulator) or nightly only?

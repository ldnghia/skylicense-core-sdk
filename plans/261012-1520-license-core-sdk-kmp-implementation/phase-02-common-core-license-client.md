---
phase: 2
title: "Common Core License Client"
status: done
priority: P1
effort: "4d"
dependencies: [1]
---

# Phase 2: Common Core License Client

## Context Links
- Backend (skyapp-api, read-only reference; paths relative to `F:\Project\skyapp-api`):
  - Routes: `Presentation/Web.Api/Controllers/LicenseController.cs:11` (`api/licenses`), verify `:98`, find `:106`, bind `:114`, release `:122`, introspect `:130`, token revoke `:138`
  - Settings: `Presentation/Web.Api/Controllers/LicenseSettingController.cs:11` route, GET `(orgId, storeId)` `:22-23`
  - DTOs: `Presentation/Web.Api/Models/Licenses/LicenseModel.cs:23-89`
  - Envelope: `Libraries/Core/ApiResponse.cs:5-37`
  - Error codes: `Presentation/Web.Api/Services/Licenses/LicenseDefaults.cs:15-65`
  - Verify/auto-bind: `Presentation/Web.Api/Services/Licenses/LicenseApiService.cs:155-200`; token lifetime `:368-388`; release+revoke-all `:936-971`; introspect `:973-998`
  - Token signing/claims: `Libraries/Service/LicenseSigningKeys/TokenSigningService.cs:47-84`, validation skew 30s `:117`
  - Settings defaults: `Libraries/Core/Domain/Licenses/LicenseSettings.cs:15` (AllowOffline=true), `:21` (60 min)
  - DeviceId column max 255: `Libraries/Data/Mapping/Builders/Licenses/LicenseDeviceBuilder.cs:18`
- cryptography-kotlin: https://github.com/whyoleg/cryptography-kotlin

## Overview
- Priority: P1. Status: pending.
- All business logic in `commonMain`: API client for the client-relevant subset of `/api/licenses/*` (5 calls), envelope/error mapping, ES256 token verification, cache, offline grace policy, clock-rollback guard, periodic re-check, public facade. Platform seams = interfaces + one `expect fun`. Ships with full `commonTest` suite (TDD; tests written alongside).

## Key Insights (verified against backend source)
1. **HTTP 200 for business errors.** Controller always `Ok(result)` (`LicenseController.cs:101-103`); failure = `success=false, error=<int>, message`. SDK must branch on envelope, not HTTP status. Non-2xx/IO = transport failure (retryable).
2. **JSON casing:** `AddControllers()` default System.Text.Json (`Presentation/Web.Framework/Infrastructure/CommonStartup.cs:41`) -> camelCase out, case-insensitive in. Send camelCase. `[inferred from ASP.NET defaults; confirm with a captured response in Phase 8 golden fixture]`.
3. **DateTimes may lack offset** (DB `DateTime` Kind likely Unspecified) -> parse leniently: no offset => UTC. Same for `exp_date` claim (`ToString("o")`, `TokenSigningService.cs:68`). `[UNVERIFIED at runtime]`.
4. **Token lifetime == offline grace.** Default lifetime `max(2*interval, 30)` min (`LicenseApiService.cs:384-388`). SDK does NOT extend grace beyond token `exp`; long offline = admin-mediated bind with `TokenLifetimeMinutes` (`LicenseModel.cs:42-49`) -> token imported into SDK.
5. **No public-key endpoint.** `kid` = `KeyVersion` (`TokenSigningService.cs:54`), first key auto-created as `"v1"` per environment (`:149-156`). SDK config takes `trustedSigningKeys: Map<kid, spkiPem>`; ops exports `PublicKeyPem` per env. Unknown `kid` => token untrusted.
6. **Re-check must introspect before refresh.** Release-device revokes all device tokens (`LicenseApiService.cs:955`) but a later `verify` would silently re-bind (`:187-196`). So periodic check = introspect first; `isRevoked` => stop, clear, state `Revoked`; do NOT auto re-verify.
7. **Settings need `orgId`** (`LicenseSettingController.cs:23`), verify response has none (`LicenseModel.cs:74-89`). Confirmed (user, 2026-10-12): all integrating apps know their `orgId` => `LicenseConfig.orgId` required; `/settings` always fetched directly, no token-derived-interval fallback.
8. **"bind-device" mapping:** normal client binding = verify auto-bind (`:189-196`). Explicit `/{id}/devices` is admin-mediated and anonymous today -> NOT exposed by SDK (would let any client mint long-lived tokens). Offline activation = `importOfflineToken(jwt)`. Adding a wrapper later = one function.
9. **Release needs license id** -> taken from token claim `lid` (`TokenSigningService.cs:64`); refresh uses claim `lk` (`:65`) so `find`-activated licenses refresh via `verify` too.
10. **Research gaps:**
    - cryptography-kotlin ECDSA P-256 verify with RAW (R||S, JOSE) signature format - **mingwX64 CONFIRMED working** (2026-10-12, real test `core/src/mingwX64Test/kotlin/com/dcorp/licensecore/EcdsaSpikeTest.kt`, provider `cryptography-provider-openssl3-prebuilt`: sign+verify+tamper-detection all pass, RAW signature is exactly 64 bytes as expected). No BCrypt/CNG fallback needed for Windows. Android (JDK provider) and iOS (Apple/openssl3 provider) still unverified - check in Phase 3/4.
    - `kotlin.time.Instant/Clock` vs `kotlinx-datetime` - use whichever is stable for the chosen Kotlin version.
    - Swift interop of `StateFlow` is poor without SKIE/KMP-NativeCoroutines -> expose a listener interface too.

## Requirements
Functional:
- `activate(licenseKey)` -> verify (auto-bind) -> verify token locally -> persist -> `Valid(Online)`.
- `activateForStore(orgId, storeId, appId?, accProductId?)` -> `/find`.
- `importOfflineToken(jwt)` -> local verify (sig, iss, `did` == this device) -> persist.
- `restore()` on startup: load cache -> local verify -> `Valid(Cached)` or `Unlicensed`.
- `start(scope)` / `stop()`: periodic re-check every interval; `checkNow()`.
- `deactivate()` -> release device (`lid`) -> clear cache (token auto-revoked server-side).
- `deviceId()` -> hashed id (for admin offline activation).
- `state: StateFlow<LicenseState>` + `addStateListener(listener): Closeable`.
Non-functional:
- No exception crosses public API (except `CancellationException`); results are sealed types.
- Each file <200 LOC. Retry: 3 attempts, exponential backoff + jitter, only on IO/5xx/408/429. Timeouts: connect 10s, request 15s (configurable).
- Thread-safe: single `Mutex` serializes activate/check/deactivate.

## Architecture

### Component layout (`core/src/commonMain/kotlin/com/dcorp/licensecore/`)
| File | Responsibility |
|------|---------------|
| `LicenseClient.kt` | Public facade; wires components; Mutex; StateFlow |
| `LicenseClientFactory.kt` | `LicenseClient.create(config, platformContext)` -> calls `createPlatformModule` |
| `LicenseConfig.kt` | baseUrl, appSalt, trustedSigningKeys, orgId, timeouts, retry, pins (Phase 7) |
| `LicenseState.kt` | `Unlicensed`, `Valid(info, mode: Online/Cached/Offline(until))`, `Revoked`, `Expired`, `DeviceLimitReached`, `Invalid(reason)`, `NetworkRequired` |
| `LicenseResult.kt` / `LicenseError.kt` | Sealed results; backend code -> error mapping |
| `LicenseStateListener.kt` | `fun interface` for Swift/C friendliness |
| `api/LicenseApiService.kt` | verify, find, release, introspect, getSettings (no `token/revoke`: release already revokes device tokens, `LicenseApiService.cs:955`; no `/{id}/devices`: see insight 8) |
| `api/LicenseApiDtos.kt` | `@Serializable` DTOs + `ApiEnvelope<T>`; `ignoreUnknownKeys=true`, optional fields nullable |
| `api/LicenseHttpClientFactory.kt` | Ktor client: ContentNegotiation, HttpTimeout, HttpRequestRetry; engine from platform |
| `token/LicenseTokenVerifier.kt` | Split JWT, base64url, ES256 verify by `kid`, `iss`, `nbf`/`exp` with 30s skew, `did` match |
| `token/LicenseTokenClaims.kt` | Parsed claims (`jti,lid,lk,did,status,exp_date,max_device,exp,nbf`) |
| `cache/CachedLicenseRecord.kt` | `schemaVersion, token, info, settings?, lastOnlineCheckAt, maxObservedTime` |
| `cache/LicenseCacheRepository.kt` | JSON <-> `SecureStorage`; corrupt/unknown version -> null |
| `policy/OfflineGracePolicy.kt` | Pure decision fn (table-driven tests) |
| `policy/ClockRollbackGuard.kt` | now < maxObservedTime - 5 min => tampered |
| `scheduler/LicenseRecheckScheduler.kt` | Coroutine loop; normal interval; 1-min fast retry while `NetworkRequired` |
| `device/DeviceIdHasher.kt` | `hex(SHA-256(rawId + appSalt))` (64 chars < 255 limit) |
| `platform/SecureStorage.kt` | `interface { get(key): ByteArray?; put(key, bytes); remove(key) }` |
| `platform/DeviceIdProvider.kt` | `interface { rawDeviceId(): String }` |
| `platform/PlatformModule.kt` | `class PlatformModule(storage, deviceIdProvider, httpEngineFactory)`; `expect fun createPlatformModule(ctx: PlatformContext, config: LicenseConfig): PlatformModule` |
| `platform/PlatformContext.kt` | `expect class PlatformContext` (Android wraps `Context`; iOS/Windows empty) |

Why interfaces + one `expect fun` (not `expect class` per seam): fakes are injectable in `commonTest`; platform phases only implement interfaces; single expect point keeps stubs trivial.

### Data flows
```
activate(key):
  deviceId = hash(provider.rawDeviceId(), appSalt)
  POST /verify {licenseKey, deviceId}
    -> envelope.success=false -> map error (table below) -> state, no cache write (clear on 1/13/14)
    -> success -> verifier.verify(data.licenseToken) -> fail => Invalid(TokenUntrusted)
    -> settings = GET /settings?orgId&storeId=data.storeId
    -> cache.save(record) -> Valid(Online)

periodic check (every interval, Mutex):
  guard clock rollback -> tampered => require online (treat offline as NetworkRequired)
  POST /token/introspect {licenseToken}
    transport fail (after retries) -> OfflineGracePolicy:
        allowOffline && now < token.exp(+skew) -> Valid(Offline(until=exp))
        allowOffline && expired                 -> NetworkRequired (offline grace exhausted)
        !allowOffline                           -> NetworkRequired
    isRevoked -> cache.clear -> Revoked (stop scheduler)
    !isValid && !isExpired (bad sig/unknown kid server-side) -> Invalid
    ok or isExpired or remaining < interval -> POST /verify {lk claim, deviceId} -> new token -> save -> Valid(Online)
    ok and plenty remaining -> update lastOnlineCheckAt -> Valid(Online)

deactivate():
  POST /{lid}/devices/release {deviceId} -> success or DEVICE_NOT_FOUND(17) -> cache.clear -> Unlicensed
  transport fail -> keep state, return error (caller may retry)
```

### Backend error -> SDK mapping (`LicenseDefaults.cs:15-65`)
| Code | Name | SDK state/result | Cache |
|------|------|-----------------|-------|
| 1 | LICENSE_NOTFOUND | `Invalid(NotFound)` | clear |
| 12 | LICENSE_KEY_REQUIRED | `Error(InvalidArgument)` (SDK bug/empty input) | keep |
| 13 | LICENSE_STATUS_INVALID | `Invalid(Inactive)` | clear |
| 14 | LICENSE_EXPIRED | `Expired` | clear |
| 15 | DEVICE_ID_REQUIRED | `Error(Internal)` | keep |
| 16 | DEVICE_LIMIT_REACHED | on `activate`: `DeviceLimitReached`; on refresh: `Revoked` (device was released) | activate: keep; refresh: clear |
| 17 | DEVICE_NOT_FOUND | on release: treat as success | clear |
| 18-22 | find input/availability | `Error(InvalidArgument)` / `Invalid(NoLicenseAvailable)` (22) | keep |
| 23/24 | TOKEN_INVALID / TOKEN_NOT_FOUND | `Invalid(TokenUntrusted)` | clear |
| other | unknown | `Error(Server(code,msg))` | keep |

## Related Code Files
- Create: all files in the table above under `core/src/commonMain/kotlin/com/dcorp/licensecore/`
- Create (actual stubs, `TODO()` bodies, handed to Phases 3/4/5): `core/src/androidMain/kotlin/com/dcorp/licensecore/platform/PlatformModule.android.kt`, `.../iosMain/.../PlatformModule.ios.kt`, `.../mingwX64Main/.../PlatformModule.mingw.kt`, plus `PlatformContext` actuals in same dirs
- Create tests: `core/src/commonTest/kotlin/com/dcorp/licensecore/` -> `LicenseTokenVerifierTest.kt`, `OfflineGracePolicyTest.kt`, `ClockRollbackGuardTest.kt`, `LicenseApiServiceTest.kt` (Ktor `MockEngine`), `LicenseClientActivateTest.kt`, `LicenseClientRecheckTest.kt`, `LicenseCacheRepositoryTest.kt`, `DeviceIdHasherTest.kt`; helpers `testsupport/TestTokenSigner.kt` (generates real P-256 keypair + signs JWTs), `testsupport/InMemorySecureStorage.kt`, `testsupport/FixedDeviceIdProvider.kt`, `testsupport/TestClock.kt`
- Modify: none outside `core/src/*` (build file owned by Phase 1/lead)

## Implementation Steps
1. Spike (2h): ES256 RAW-signature verify via cryptography-kotlin on android-host, iosSimulatorArm64, mingwX64 using a token+PEM captured from a dev skyapp-api. Record provider per target. If mingwX64 fails -> fallback BCrypt plan, notify lead.
2. DTOs + `ApiEnvelope<T>` + lenient date serializer; tests with golden JSON (camelCase, nulls omitted).
3. `LicenseHttpClientFactory` (Json, timeouts, `HttpRequestRetry` with `retryOnExceptionOrServerErrors(3)` + `exponentialDelay()`, 408/429 included). Engine from `PlatformModule.httpEngineFactory`.
4. `LicenseApiService` + MockEngine tests for each endpoint (success, business error, 500 -> retry, timeout).
5. `LicenseTokenVerifier` + claims; tests: valid, tampered payload, wrong kid, unknown kid, iss mismatch, did mismatch, expired, nbf future, skew boundary +/-30s.
6. `DeviceIdHasher`, `CachedLicenseRecord`, `LicenseCacheRepository` (JSON, schemaVersion=1, corrupt -> null).
7. `ClockRollbackGuard` + `OfflineGracePolicy` (pure, table-driven tests covering every row of data-flow matrix).
8. `LicenseClient` facade + error mapping table; `LicenseRecheckScheduler` using injected `CoroutineScope` + clock; tests with `kotlinx-coroutines-test` virtual time (revoked stops loop, network-fail -> offline -> recover, refresh when remaining < interval).
9. `expect fun createPlatformModule` + `expect class PlatformContext` + `TODO()` actual stubs in 3 platform source sets; confirm all targets compile.
10. KDoc on every public symbol (explicitApi).

## Todo List
- [x] Crypto spike + provider decision recorded - mingwX64 confirmed working with cryptography-provider-openssl3-prebuilt
- [x] DTOs/envelope/date parsing + tests - `api/LicenseApiDtos.kt`, no date parsing needed (display fields kept as opaque strings, see Architecture note below)
- [x] HTTP client + retry + API service + MockEngine tests - `api/LicenseHttpClientFactory.kt`, `api/LicenseApiService.kt`, 6 MockEngine tests
- [x] Token verifier + tests - `token/LicenseTokenVerifier.kt`, 8 tests (valid/tamper/unknown-kid/iss/did/expired/nbf-future/skew)
- [x] Cache, hasher, policy, rollback guard + tests - `cache/`, `device/DeviceIdHasher.kt`, `policy/`, 12 tests total
- [x] Facade + scheduler + tests - `LicenseClient.kt`, `scheduler/LicenseRecheckScheduler.kt`, 9 integration tests
- [x] expect/actual stubs; all targets compile - mingwX64 compiles+tests clean (verified real run); android/iOS stubs compile-correct by construction but not host-verified (no Android SDK / macOS in this dev environment, same gap as Phase 1)
- [x] commonTest green on mingwX64 (43/43 passing, real `./gradlew :core:mingwX64Test` run). android-host/iosSimulatorArm64 not run - no SDK/macOS available; CI will cover.

Simplification vs. original design: `LicenseConfig.orgId` is required (not optional) and the
token-derived-interval fallback was dropped, per the resolved Unresolved Question that all
integrating apps know their `orgId`.

## Success Criteria
- [x] `./gradlew :core:mingwX64Test` green (Windows runner) - verified real run, 43/43 passing. macOS runner not available in this dev environment; CI will verify iosSimulatorArm64Test/testAndroidHostTest.
- [ ] Line coverage of `commonMain` >= 85% (Kover) - not measured; no Kover plugin wired yet (deferred, not blocking).
- [x] Every row of the error-mapping table has a named test (via `LicenseClientActivateTest`/`LicenseApiServiceTest`); every row of the data-flow matrix has a named test (via `LicenseClientRecheckTest`).
- [x] Golden test: real backend-issued-shaped token (signed by `TestTokenSigner`, matching `TokenSigningService.cs`'s exact claim set) + real PEM verifies on mingwX64.
- [x] No public function throws - all public API returns `LicenseState`; verified by test assertions on return values, not exceptions.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| ES256 not supported by chosen provider on mingwX64 | RESOLVED (2026-10-12) | Spike confirmed working, see Key Insight 10 - risk no longer applies |
| .NET JWT encoding quirks (DER vs RAW sig, base64 padding) | L x H | Golden fixture from real backend |
| Date parsing without offset misreads expiry by TZ offset | M x M | Lenient UTC parser + test; recommend backend fix |
| Revocation suppressed by blocking introspect | M x L | Bounded by token exp (default <= 2x interval) |
| Interval derivation wrong when override lifetime used (offline token) | M x L | Clamp derived interval to [15, 1440] min; offline tokens re-checked when online anyway |

## Security Considerations
- Trust anchor = embedded public keys; no TOFU/key download.
- `did` claim must equal local hashed device id -> copied token useless elsewhere.
- Raw device identifiers never leave device (hashed with app salt).
- Never log tokens/license keys; logging hook redacts (`lk`, `licenseToken`).
- Clock rollback => offline grace disabled until next successful online check.

## Rollback
Revert PR; no persisted data yet in production. Cache `schemaVersion` reserved for future migrations (unknown -> discard).

## Next Steps
Phases 3, 4, 5 in parallel; each owns only its platform source set + sample.

## Unresolved Questions
Resolved (user, 2026-10-12): post-revocation = explicit `activate` only, no auto re-verify. `orgId` is known to all integrating apps -> real `/settings` fetch works as planned, no fallback-estimation path needed.
- (Resolved by trace, not a question) `DEVICE_LIMIT_REACHED` cannot occur on refresh of a still-bound device: verify skips the limit check when the device row exists (`LicenseApiService.cs:183-186`). It only appears if the device was released between introspect and verify -> treated like `Revoked`.

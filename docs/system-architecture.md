# System Architecture

## Module layout

One KMP module, `:core`, holds all license logic. Platform differences (secure storage, device
id, HTTP engine/TLS pinning) sit behind `expect`/`actual` interfaces, each assembled by one
`expect fun createPlatformModule(context: PlatformContext, config: LicenseConfig): PlatformModule`
per target (`core/src/{androidMain,iosMain,mingwX64Main}/.../platform/PlatformModule.*.kt`).

```
core/src/commonMain/kotlin/com/dcorp/licensecore/
  LicenseClient.kt              # public facade - activate/find/checkNow/deactivate/restore
  LicenseClientFactory.kt        # wires LicenseClient from a PlatformModule + LicenseConfig
  LicenseConfig.kt                # baseUrl, orgId, appSalt, trustedSigningKeys, pins, timeouts
  LicenseState.kt                  # the sealed state every public call returns
  LicenseErrorMapping.kt          # backend error code -> LicenseState
  api/            # ApiEnvelope DTOs + LicenseApiService (verify/find/release/introspect/settings)
  cache/          # CachedLicenseRecord + LicenseCacheRepository (wraps SecureStorage)
  device/         # DeviceIdHasher (SHA-256(rawDeviceId + appSalt))
  internal/       # LicenseVerifyHandler, LicenseRecheckLogic - the actual decision logic
  policy/          # OfflineGracePolicy, ClockRollbackGuard - pure functions, heavily unit-tested
  scheduler/       # LicenseRecheckScheduler - periodic re-check loop, faster retry while offline
  token/            # LicenseTokenVerifier (ES256 JWT, offline-verifiable) + claims model
  platform/         # expect interfaces: SecureStorage, DeviceIdProvider, PlatformContext, PlatformModule
```

## Backend contract

Every `skyapp-api` `/api/licenses/*` call returns HTTP 200 with an envelope
`{success, error?, message?, data?}` (`Libraries/Core/ApiResponse.cs`) - business failures are
signaled through `error`/`message`, never the HTTP status. `LicenseApiService` never throws for a
business error; only a real transport failure (`CancellationException` aside) becomes
`LicenseApiOutcome.TransportError`, which the SDK treats as "offline", not "error".

The license token is an ES256 JWT (`iss=skyapp-license-engine`), claims `jti, lid, lk, did,
status, exp_date, max_device`, with `kid` identifying which signing key produced it. There is no
public-key/JWKS endpoint - the SDK ships trusted public keys itself via
`LicenseConfig.trustedSigningKeys` (`kid -> PEM`), set by the integrating app. Default token
lifetime is `max(2 * LicenseCheckIntervalMinutes, 30)` minutes - this is what ultimately bounds
offline grace (see `license-lifecycle-and-offline-grace.md`).

## Data flow: activation

```
LicenseClient.activate(licenseKey)
  -> deviceId() = DeviceIdHasher.hash(DeviceIdProvider.rawDeviceId(), config.appSalt)
  -> LicenseApiService.verify(licenseKey, deviceId)   -- POST /api/licenses/verify
  -> LicenseVerifyHandler.handle(outcome, deviceId, isRefresh=false)
       Success     -> LicenseTokenVerifier.verify(token, deviceId, now) -> LicenseState.Valid(Online)
                      + CachedLicenseRecord saved via LicenseCacheRepository (SecureStorage)
       BusinessError -> mapBusinessError(code, message, isRefresh=false) -> e.g. Invalid/Expired/DeviceLimitReached
       TransportError -> state unchanged (caller may retry; no cache mutation)
```

`activateForStore(storeId, appId, accProductId)` is the same shape via `POST /api/licenses/find`
instead, for devices that don't know their license key up front (it claims a free slot on the
soonest-expiring eligible license).

## Data flow: periodic re-check

`LicenseClient.start(scope)` runs `LicenseRecheckScheduler`, which calls `checkNow()` every
`checkIntervalMinutes` (from the cached record; 1 minute instead while in `NetworkRequired`, to
recover from a connectivity blip quickly without hammering the server at full rate otherwise).
`LicenseRecheckLogic.recheck()`:

1. Locally re-verify the cached token's signature/claims (cheap, no network).
2. `POST /api/licenses/token/introspect` - revoked/invalid short-circuits immediately (this is
   *before* any decision to use the cached token, so a revoked device stops immediately rather
   than waiting out the token's own `exp`).
3. If introspection says the token needs refreshing (expired, or close enough to `exp` that the
   next interval would cross it), call `verify` again for a fresh token.
4. If introspection fails with a transport error, fall through to `OfflineGracePolicy` - bounded
   by the cached token's own `exp`; see the lifecycle doc for the exact rules and
   `ClockRollbackGuard`'s role.

## Token verification (local, no network)

`LicenseTokenVerifier` (`core/src/commonMain/.../token/LicenseTokenVerifier.kt`) hardcodes the
expected algorithm (ES256/P-256-SHA256) regardless of the JWT header's own `alg` field - the
header is only read for `kid`, so there is no algorithm-confusion/`alg:none` acceptance path. An
unknown `kid` (e.g. after a backend key rotation the app hasn't picked up yet) is rejected, not
silently trusted. Signature is checked before any claim is read; issuer, the token's `did` claim
against this device's own hashed id, and `nbf`/`exp` (with a bounded clock-skew tolerance) are all
checked after that.

## Why the SDK never calls certain backend endpoints

`/{id}/devices` (admin-mediated lifetime-override device bind), `/devices/release` as an *admin*
action (vs. the SDK's own `release` call for its own device), and `/token/revoke` are deliberately
not wrapped - they're anonymous/unauthenticated endpoints on the backend today (a cross-repo gap,
see `docs/security-threat-model.md`), and wrapping them in the SDK would let any integrating app
call them against *any* device, not just its own. `release()` already revokes the device's token
server-side (`LicenseApiService.cs:955`), which is the only revocation path the SDK itself needs.

## Platform-specific pieces (one line each - see the per-platform integration guides for detail)

| Concern | Android | iOS | Windows (mingwX64) |
|---|---|---|---|
| Secure storage | AES-256-GCM, Android Keystore key, private `SharedPreferences` | Keychain generic password, `AfterFirstUnlockThisDeviceOnly` | DPAPI (user scope) + file under `%LOCALAPPDATA%` |
| Device id | `Settings.Secure.ANDROID_ID` (fallback: random UUID in plain prefs) | Random UUID in Keychain (fallback: `identifierForVendor`) | Registry `MachineGuid` (fallback: random id via DPAPI) |
| HTTP engine | Ktor OkHttp | Ktor Darwin | Ktor WinHttp |
| Certificate pinning | OkHttp `CertificatePinner` when configured | Darwin `CertificatePinner`/`handleChallenge` when configured | **Not supported** - WinHttp/Ktor's Curl engine expose no pinning hook; documented risk-acceptance in `docs/security-threat-model.md` |

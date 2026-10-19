# License API Mapping Review: skylicense-core-sdk vs skyapp-api

Scope: verify SDK code (not plan.md) against real backend controllers/services/JWT signer. Read-only.
Backend commit for license API: `284a374` (2026-10-08) - no changes since on these files (`git log --since=2026-10-01` on the relevant files returns only this commit).

## Per-endpoint verdict

### POST /api/licenses/verify - MATCH
- Backend route/method: `Presentation/Web.Api/Controllers/LicenseController.cs:98-104`, impl `LicenseApiService.cs:155-200`.
- Request model `VerifyLicenseRequest` (LicenseKey, DeviceId) - `Presentation/Web.Api/Models/Licenses/LicenseModel.cs:23-27`.
- SDK: `core/.../api/LicenseApiService.kt:22-23` posts `VerifyLicenseRequestDto(licenseKey, deviceId)` - `LicenseApiDtos.kt:16`. Field names/order match. Casing: `AddControllers()` registered with no `JsonOptions`/naming-policy override (`Presentation/Web.Framework/Infrastructure/CommonStartup.cs:41`), so System.Text.Json's ASP.NET-default camelCase applies - matches SDK's camelCase properties.
- Response `VerifyLicenseResponse` (`LicenseModel.cs:74-89`) field names/types align 1:1 with `VerifyLicenseResponseDto` (`LicenseApiDtos.kt:34-48`): `isValid, licenseKey, licenseToken, status, statusText, activationDate, expiryDate, daysRemaining, maxDevice, isTrial, applicationRegisterId, accProductId, storeId`. `DateTime?` fields serialize to ISO strings, SDK types them `String?` - consistent (parsing happens downstream, not reviewed here).

### POST /api/licenses/find - MATCH
- Route `LicenseController.cs:106-112`, impl `LicenseApiService.cs:202-300`.
- Request `FindLicenseRequest` (OrgId, AppId?, AccProductId?, StoreId, DeviceId) - `LicenseModel.cs:29-36`. SDK `FindLicenseRequestDto` - `LicenseApiDtos.kt:19-25` - exact field match, `config.orgId` supplied by SDK (`LicenseApiService.kt:26`).
- Used by `LicenseClient.activateForStore` - `LicenseClient.kt:62-64`.

### POST /api/licenses/{id}/devices/release - MATCH
- Route `LicenseController.cs:122-128`, impl `LicenseApiService.cs:936-971`. Body `ReleaseDeviceModel.DeviceId` - `LicenseModel.cs:52-55`.
- SDK builds `/api/licenses/$licenseId/devices/release` with `ReleaseDeviceRequestDto(deviceId)` - `LicenseApiService.kt:28-34`. Response data is a `License` object server-side but SDK's `ApiEnvelopeAck` omits `data` - safe because `ignoreUnknownKeys = true` is set globally (`LicenseHttpClientFactory.kt:19`).

### POST /api/licenses/token/introspect - MATCH (including edge-case semantics)
- Route `LicenseController.cs:130-136`, impl `LicenseApiService.cs:973-998`.
- Important subtlety verified: on bad signature, backend returns `success:true` with `{isValid:false, isRevoked:false, isExpired:false}` (not a BusinessError) - `LicenseApiService.cs:981-987`. On revoked token, `isValid = isValid && !isRevoked` so `isValid:false, isRevoked:true` - `LicenseApiService.cs:994`. SDK's `handleIntrospectSuccess` checks `isRevoked` first, then `!isValid && !isExpired` → `TokenUntrusted` - `LicenseRecheckLogic.kt:64-71`. Order/logic correctly mirrors backend's precedence.

### GET /api/licenses/settings - MATCH
- Route `LicenseSettingController.cs:22-27` (`[AllowAnonymous]`), params `(int orgId, int storeId)`. Impl returns `LicenseSettingModel` with `AllowOffline, AllowOffline_OverrideForStore, LicenseCheckIntervalMinutes, LicenseCheckIntervalMinutes_OverrideForStore` - `LicenseSettingModel.cs:8-12`.
- SDK sends `orgId`/`storeId` query params (`LicenseApiService.kt:39-43`) and declares `LicenseSettingDto(allowOffline, licenseCheckIntervalMinutes)` - `LicenseApiDtos.kt:54`, dropping the two `_OverrideForStore` fields (irrelevant to a client, correctly ignored via `ignoreUnknownKeys`).

### token/revoke, {id}/devices (bind) - INTENTIONALLY NOT WRAPPED
- Confirmed not implemented client-side per `LicenseApiService.kt:16-18` comment; `release` already revokes all tokens for the device server-side (`LicenseApiService.cs:955` `RevokeAllForDevice`), so this is consistent, not a gap.

## JWT / token verification - MATCH
- Backend signs ES256, `iss="skyapp-license-engine"` - `Libraries/Service/LicenseSigningKeys/TokenSigningService.cs:72-73,113`. Claims: `jti, lid, lk, did, status, exp_date, max_device` plus standard `nbf/exp` (epoch seconds, from `JwtSecurityToken`) - `TokenSigningService.cs:61-77`. `kid` = `KeyVersion` on the security key - `TokenSigningService.cs:54`. Clock skew 30s - `TokenSigningService.cs:117`.
- SDK verifier checks `iss == "skyapp-license-engine"`, `did == expectedDeviceId`, 30s skew, `kid`→trusted-key lookup (no JWKS endpoint, confirmed no such route exists anywhere under `Presentation/Web.Api/Controllers`) - `LicenseTokenVerifier.kt:25-26,38-39,52-55,68`. `exp_date`/`max_device` claims are decoded into `LicenseTokenPayload` but deliberately not surfaced in `LicenseTokenClaims` (unused by SDK logic) - `LicenseTokenClaims.kt:21-22` vs `:26-35`. Not a bug, just dead fields.
- One edge case worth flagging (informational, not a bug): backend writes `max_device` claim as `license.MaxDevice?.ToString() ?? string.Empty` (`TokenSigningService.cs:69`) - an empty string, not a JSON null, when unlimited. Harmless today since SDK never reads this field, but if a future SDK change starts consuming `LicenseTokenPayload.maxDevice` it must handle `""` as "unlimited", not attempt `.toIntOrNull()` and silently get `null`-as-error.

## Error codes - MATCH
- `LicenseDefaults.ErrorCodes` enum (`Presentation/Web.Api/Services/Licenses/LicenseDefaults.cs:15-65`, values 1-24) maps 1:1 to `mapBusinessError` in `LicenseErrorMapping.kt:6-16`. Every code branch SDK handles (1,12,13,14,15,16,18,19,20,21,22,23,24) exists server-side with identical numeric value and identical triggering condition, cross-checked against `LicenseApiService.cs:155-200` (verify), `:202-300` (find), `:936-971` (release), `:973-998` (introspect).
- Device-limit code 16 dual meaning (fresh activation vs refresh-after-release) verified against the actual branch logic at `LicenseApiService.cs:181-197`: code 16 only fires when `GetByLicenseAndDevice` returns null (device not currently registered) AND count≥max - consistent with SDK's "device was released elsewhere" interpretation on refresh (`LicenseErrorMapping.kt:5,11`).

## Auth/authorization - MATCH
- `LicenseController` has no `[Authorize]` attribute and extends plain `Controller` (not an authenticated base class); `LicenseSettingController` explicitly `[AllowAnonymous]` (`LicenseSettingController.cs:12`). No global default-auth-policy/fallback policy found anywhere under `Presentation/Web.Api` (grepped for `FallbackPolicy`, `RequireAuthenticatedUser`, `AddAuthorization(` - no matches). Confirms plan's "anonymous" claim still holds and SDK correctly sends no auth header.

## Backend changes since plan's "verified facts" (2026-10-12)
- `git log` on all cited backend files shows only the single commit `284a374` (2026-10-08), which predates the plan's verification date - i.e. nothing has changed in the license API surface since the SDK's facts were recorded. No catch-up needed.
- Unrelated license-admin endpoints (search, dashboard, report/by-store, renew/revoke/reactivate/transfer, CRUD) have had many commits after 10-08, but none of those touch verify/find/release/introspect/settings/token-signing, and the SDK correctly does not wrap any of them (admin-only surface, out of SDK scope).

## Unresolved Questions
- None requiring backend clarification - confidence high (grep-verified controller/service/DTO/JWT code on both sides, current HEAD). One informational note (max_device empty-string claim) left above for future-proofing only, not a present defect.

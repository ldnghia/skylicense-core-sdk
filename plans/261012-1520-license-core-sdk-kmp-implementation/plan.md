---
title: "License Core SDK - KMP Implementation"
description: "Kotlin Multiplatform client SDK (Android, iOS XCFramework, Windows mingwX64 DLL) for skyapp-api /api/licenses/* verify, token caching, offline grace, revocation."
status: pending
priority: P1
effort: 25d
branch: ""
tags: [kmp, kotlin-native, licensing, sdk, android, ios, windows, security]
blockedBy: []
blocks: []
created: "2026-10-12T08:27:48.503Z"
createdBy: "ck:plan"
source: skill
---

# License Core SDK - KMP Implementation

## Overview
New repo `F:\Project\skylicense-core-sdk`. One KMP module `:core` holds all license logic in `commonMain`; platform bits (secure storage, device id, HTTP engine, TLS pinning) sit behind interfaces created by one `expect fun createPlatformModule(...)` per target. Backend = existing skyapp-api `/api/licenses/*` (no backend changes in scope).

Locked decisions (PM, 2026-10-12, not reopened): KMP not Rust; targets = Android, iOS (XCFramework), Windows `mingwX64` DLL only; x86 Windows via `license-agent.exe` named-pipe helper = DEFERRED; Windows ARM64 = known gap, out of scope.

## Phases

| Phase | Name | Status | Effort | Depends on |
|-------|------|--------|--------|-----------|
| 1 | [Project Scaffolding and CI Skeleton](./phase-01-project-scaffolding-and-ci-skeleton.md) | Done | 1d | - |
| 2 | [Common Core License Client](./phase-02-common-core-license-client.md) | Done | 4d | 1 |
| 3 | [Android Target](./phase-03-android-target.md) | Partial - code written, unverified (no SDK) | 2d | 2 |
| 4 | [iOS Target and XCFramework](./phase-04-ios-target-and-xcframework.md) | Partial - code written, zero compiler feedback (no macOS host) | 3d | 2 |
| 5 | [Windows mingwX64 DLL](./phase-05-windows-mingwx64-dll.md) | Done | 4d | 2 |
| 6 | [License Agent x86 Helper (Deferred)](./phase-06-license-agent-x86-helper-deferred.md) | Deferred - not in scope yet | - | 5 |
| 7 | [Security Hardening](./phase-07-security-hardening.md) | Partial - Windows pinning resolved for real; Android/iOS pinning unverified; scan not run | 3d | 3,4,5 |
| 8 | [Integration Testing and CI Matrix](./phase-08-integration-testing-and-ci-matrix.md) | Pending | 3d | 7 |
| 9 | [Packaging and Distribution](./phase-09-packaging-and-distribution.md) | Pending | 3d | 8 |
| 10 | [SDK Integration Docs](./phase-10-sdk-integration-docs.md) | Pending | 2d | 8 (parallel w/ 9) |

Parallel windows: {3,4,5} after 2; {9,10} after 8. File ownership per phase listed in each phase file; `core/build.gradle.kts` + `gradle/libs.versions.toml` are single-owner (lead) during parallel windows.

## Key Backend Facts (verified in skyapp-api, cite in phases)
- All responses HTTP 200 + envelope `{success, error?, message?, data?}` - `Libraries/Core/ApiResponse.cs:5-37`.
- Token = ES256 JWT, `iss=skyapp-license-engine`, claims `jti,lid,lk,did,status,exp_date,max_device`, `kid=KeyVersion` - `Libraries/Service/LicenseSigningKeys/TokenSigningService.cs:54-77`.
- Default token lifetime = `max(2*LicenseCheckIntervalMinutes, 30)` min - `Presentation/Web.Api/Services/Licenses/LicenseApiService.cs:384-388`.
- NO public-key/JWKS endpoint exists; signing key auto-bootstrapped per env (`TokenSigningService.cs:135-161`) -> SDK must ship trusted public keys via config.
- License + settings endpoints are anonymous (no `[Authorize]`; `LicenseSettingController.cs:12` is `[AllowAnonymous]` incl. POST).

## Cross-Repo Recommendations (skyapp-api, NOT in this plan's scope)
1. Public signing-key endpoint (JWKS-like) for key rotation.
2. Require admin auth on `/{id}/devices` (lifetime override), `/devices/release`, `/token/revoke`, `POST /settings`.
3. Put `allow_offline` + check interval into signed token claims (settings GET needs `orgId`, which verify response lacks).
4. Serialize DateTimes with explicit UTC offset.

## Rollback
Each phase = own PR; revert PR. Only persisted client state = cache record with `schemaVersion`; unknown/corrupt -> discard + online re-verify (server is source of truth).

## Confirmed Decisions (user, 2026-10-12)
1. Package name `com.dcorp.licensecore` confirmed. CI = GitHub Actions confirmed.
2. Android minSdk 24, iOS min 14 confirmed; `iosX64` not needed.
3. Integrating apps know their `orgId` -> `/settings` fetch works as planned; no backend token-claim change needed.
4. Post-revocation: explicit re-activate only, no auto re-verify.
5. iOS framework: static. SKIE: skipped for v1, default Swift/Kotlin interop only.
6. Windows consumer stacks: .NET Framework + .NET 6 -> NuGet package confirmed wanted (Phase 9 `LicenseCore.Native.win-x64`, no longer conditional). DPAPI scope (user vs machine) and proxy config: still open, decide during Phase 5 implementation.
7. Windows cert pinning via Curl engine: still undecided - proceed with documented no-pinning fallback (WinHttp) as risk-accepted default until resolved; revisit in Phase 7.
8. Dev/staging skyapp-api environment + fixture-license provisioning: owned by user (Nghia LD).
9. Maven repo: default GitHub Packages (per Phase 9 mitigation). Signing certs (Authenticode/Apple Distribution): status still unknown -> ship RC unsigned internally, block GA on signing per Phase 9 risk mitigation. Distribution: private/internal only (not public GitHub Release).
10. No binary tamper resistance required for v1.
11. Cross-repo skyapp-api gaps (JWKS/public-key endpoint, admin auth, UTC offset): fixed after SDK is working, not blocking this plan/timeline.
12. Docs language: English only (no Vietnamese).

## Unresolved Questions
None remaining from the original consolidated list (see phase-10 history). Still-open micro-decisions: Windows DPAPI scope/proxy config (Phase 5), Windows pinning fallback (Phase 7), signing cert availability (Phase 9).

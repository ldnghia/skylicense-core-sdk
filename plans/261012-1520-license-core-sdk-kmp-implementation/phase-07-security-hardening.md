---
phase: 7
title: "Security Hardening"
status: partial
priority: P1
effort: "3d"
dependencies: [3, 4, 5]
---

# Phase 7: Security Hardening

## Context Links
- [Phase 2 data flows](./phase-02-common-core-license-client.md), [Phase 3](./phase-03-android-target.md), [Phase 4](./phase-04-ios-target-and-xcframework.md), [Phase 5](./phase-05-windows-mingwx64-dll.md)
- Backend: introspect `Presentation/Web.Api/Services/Licenses/LicenseApiService.cs:973-998` (response unsigned); settings controller anonymous `Presentation/Web.Api/Controllers/LicenseSettingController.cs:12`
- Ktor Darwin pinning: https://api.ktor.io/ktor-client-darwin/io.ktor.client.engine.darwin.certificates/-certificate-pinner/-certificate-pinner.html
- WinHttp engine config (only `sslVerify` toggle documented): https://api.ktor.io/ktor-client-winhttp/io.ktor.client.engine.winhttp/-win-http-client-engine-config/ssl-verify.html

## Overview
- Priority: P1. Status: pending. Runs after all 3 platforms land (touches their engine factories sequentially - no parallel conflict).
- Certificate pinning per platform, storage review, revocation/clock edge cases, dependency verification, threat model doc.

## Key Insights - threat model first (what pinning actually protects)
Assets: ability to run licensed app. Attacker: usually the device owner (piracy), sometimes network MITM.
| Attack | Without pinning | Already mitigated by |
|--------|-----------------|---------------------|
| Forge valid license token | Impossible | ES256 sig vs embedded public keys + `did` claim (Phase 2) |
| Fake `introspect` -> hide revocation | Possible via user-installed root CA + proxy | Bounded: token `exp` (default `max(2x interval,30)` min, `LicenseApiService.cs:384-388`); refresh needs real server-signed token, server refuses inactive license (`:167-169`) |
| Fake `/settings` -> `allowOffline=true` | Possible (also anonymous unsigned endpoint) | Offline still bounded by token exp |
| Block network -> extend offline | Possible | Bounded by token exp + clock rollback guard |
| Copy cache to other device | n/a | DPAPI/Keychain/Keystore binding + `did` claim |
| Clock rollback while offline | n/a | `ClockRollbackGuard` (Phase 2) |
=> Pinning is **defense-in-depth** (narrows revocation-suppression window to zero), not a primary control. Consequence: Windows may ship without pinning if WinHttp cannot support it, with documented rationale. Do not over-invest.

Other insights:
- Pin SPKI SHA-256 of **intermediate CA or own key**, always >= 2 pins (current + backup) to survive cert rotation; pins come from `LicenseConfig.certificatePins` (host -> list), empty = pinning off (dev).
- Android: Ktor OkHttp engine `config { certificatePinner(CertificatePinner.Builder().add(host, "sha256/...").build()) }`.
- iOS: Ktor Darwin `handleChallenge(CertificatePinner.Builder().add(host, "sha256/...").build())`.
- Windows: WinHttp engine exposes no pinning API (only `sslVerify`). **Resolved (2026-10-13, real spike)**: Ktor's Curl engine artifact exists for mingwX64, but its public `CurlClientEngineConfig` has no `CURLOPT_PINNEDPUBLICKEY` equivalent (`sslVerify`/`caInfo`/`caPath` only) - option (a) would need direct libcurl cinterop, not a config change. Chose option (b): no pinning, rationale in `docs/security-threat-model.md`.
- Key rotation gap: no public-key endpoint; `trustedSigningKeys` map supports multiple `kid`s -> ship next key before backend rotates (runbook in Phase 10). Backend JWKS = cross-repo recommendation.

## Requirements
- Functional: pinning on Android + iOS (configurable); Windows decision recorded; revocation and clock edge cases covered by tests; logging redaction verified.
- Non-functional: no new mandatory deps on Android/iOS; Gradle dependency verification on.

## Architecture
```
LicenseConfig.certificatePins: Map<String host, List<String sha256Spki>>
  -> AndroidHttpEngineFactory  -> OkHttp CertificatePinner
  -> DarwinHttpEngineFactory   -> Darwin CertificatePinner (handleChallenge)
  -> WinHttpEngineFactory      -> (a) Curl engine w/ pinned key OR (b) ignore + warn log once
Pin failure -> transport error -> same path as offline (grace bounded by token exp), state reason flagged PinningFailure for telemetry hook.
```

## Related Code Files
- Modify: `core/src/commonMain/kotlin/com/dcorp/licensecore/LicenseConfig.kt` (add `certificatePins`)
- Modify: `core/src/androidMain/.../platform/AndroidHttpEngineFactory.kt`, `core/src/iosMain/.../platform/DarwinHttpEngineFactory.kt`, `core/src/mingwX64Main/.../platform/WinHttpEngineFactory.kt`
- Modify: `core/src/commonMain/.../LicenseClient.kt` (pinning-failure reason), `core/src/commonMain/.../api/LicenseHttpClientFactory.kt` (log redaction)
- Create: `core/src/commonTest/.../RevocationEdgeCasesTest.kt`, `.../LogRedactionTest.kt`
- Create: `gradle/verification-metadata.xml`
- Create: `docs/security-threat-model.md`
- Sequential phase: no concurrent ownership conflicts.

## Implementation Steps
1. Write `docs/security-threat-model.md` (table above + residual risks).
2. Add `certificatePins` to config; wire Android + iOS pinners; tests with wrong pin -> transport failure (Android instrumented against dev host; iOS sample).
3. Windows spike (max 1d): Curl engine pinning on mingwX64. Decide (a)/(b); record in threat model.
4. Revocation edge-case tests: revoked while offline then online; release-device between introspect and refresh; token with unknown `kid` after key rotation; `exp_date` < token `exp`; duplicate `activate` concurrent calls (Mutex).
5. Clock tests: rollback > 5 min while offline; forward jump; timezone change (no effect - all UTC).
6. Storage review checklist per platform (accessibility flags, backup exclusion, file ACL on Windows `%LOCALAPPDATA%` default per-user ACL acceptable).
7. Log redaction: no `licenseKey`/`licenseToken` in any log line (test with capturing logger).
8. Enable Gradle dependency verification (sha256) + Dependabot/Renovate config.
9. Run `/ck:security-scan` (secrets, deps) and fix findings.

## Todo List
- [x] Threat model doc - `docs/security-threat-model.md`, includes the real Windows spike finding (not guessed)
- [x] Android + iOS pinning written - `AndroidHttpEngineFactory.kt`/`DarwinHttpEngineFactory.kt` now take `certificatePins` and wire `CertificatePinner`; **UNVERIFIED** (same no-SDK/no-macOS gap as Phases 3-4), no instrumented/XCTest pinning tests run
- [x] Windows pinning decision - **resolved definitively, not left "chưa rõ"**: real spike in this session found Ktor's Curl engine has no `CURLOPT_PINNEDPUBLICKEY`-equivalent config at all (only `sslVerify`/`caInfo`/`caPath`), so option (a) would need raw libcurl cinterop, not a config change. Chose option (b) - no pinning, documented rationale in the threat model doc. `WinHttpEngineFactory` logs once if `certificatePins` is set but ignored.
- [x] Revocation + clock edge-case tests - real run on mingwX64: `RevocationEdgeCasesTest.kt` (unknown kid after rotation, device-released-elsewhere-on-refresh -> Revoked, concurrent `activate` calls serialized by the Mutex) + `ClockEdgeCasesTest.kt` (rollback >5min while offline -> NetworkRequired even with a still-valid token, forward jump -> normal offline grace)
- [x] Storage review checklist - in the threat model doc; Android backup-exclusion item flagged as not yet verified (Phase 3 follow-up, non-blocking per the `did`-claim mitigation)
- [x] Log redaction test - `LogRedactionTest.kt`, real run on mingwX64
- [x] Dependency verification enabled - `gradle/verification-metadata.xml` generated for real via `--write-verification-metadata sha256`, confirmed a clean rebuild still succeeds with verification on. **Gap**: only covers dependencies resolved on this machine (mingwX64 + plugin classpath) - Android/iOS dependencies were never resolved here (no SDK/macOS) and are NOT in this metadata yet; will need regenerating (additively) the first time someone builds those targets on a capable machine.
- [x] Security scan - manual scan (no SAST tool available, network-restricted sandbox) of `core/src/**/kotlin` for secrets/deps/logging/crypto/storage/OWASP, documented in `docs/security-threat-model.md`. No critical/high findings. Fixed one low-severity gap (`LicenseConfig.baseUrl` now requires `https://`); one low-severity item tracked, not fixed (`consumer-rules.pro` not wired into AGP 9 KMP `android {}` DSL - needs Android SDK/docs access this session lacked).

## Success Criteria
- [ ] Wrong pin on Android/iOS -> request fails - code written, not tested (no SDK/macOS to run instrumented/XCTest).
- [x] Windows decision + rationale in `docs/security-threat-model.md` - done, with a real (not guessed) spike finding.
- [x] Revocation/clock edge-case tests green - on mingwX64 (the only target buildable here); 59/59 tests passing total after this phase's additions.
- [x] Security scan: 0 high/critical findings - done (see Todo List entry above).

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| Pins break after backend cert rotation -> fleet goes offline | M x H | >= 2 pins incl. backup; pin intermediate/own key; offline grace absorbs; remote pin update impossible -> ship SDK update before rotation |
| Curl engine adds DLL dependency/size on Windows | M x M | Prefer option (b) unless static |
| False sense of security re binary patching | H x M | Out of scope; document (no obfuscation/anti-tamper in v1) |

## Security Considerations
This phase is the security pass; residual risks (binary patching, anonymous backend admin endpoints, no JWKS) documented, not solved here.

## Rollback
Pinning off by config (empty pins) without code revert; code revert per PR otherwise.

## Next Steps
Phase 8 runs everything against real backend.

## Unresolved Questions
Resolved (user, 2026-10-12): binary tamper resistance NOT required for v1. Windows pinning (option a vs b above) still undecided ("chưa rõ") - proceed with default (b) no-pinning fallback until a Curl-engine spike says otherwise. Still open: who owns TLS cert rotation for the API host, and can SDK releases be scheduled ahead of it?

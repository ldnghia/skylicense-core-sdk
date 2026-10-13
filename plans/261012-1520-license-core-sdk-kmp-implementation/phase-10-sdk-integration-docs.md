---
phase: 10
title: "SDK Integration Docs"
status: partial
priority: P2
effort: "2d"
dependencies: [8]
---

# Phase 10: SDK Integration Docs

## Context Links
- Public API: [Phase 2](./phase-02-common-core-license-client.md); platform specifics: [3](./phase-03-android-target.md), [4](./phase-04-ios-target-and-xcframework.md), [5](./phase-05-windows-mingwx64-dll.md); threat model `docs/security-threat-model.md` (Phase 7); E2E checklist `docs/release-e2e-checklist.md` (Phase 8); release process `docs/release-process.md` (Phase 9)

## Overview
- Priority: P2. Status: pending. Parallel with Phase 9 (disjoint files).
- Consumer-facing integration guides per platform + lifecycle/offline/revocation semantics + error reference + project docs set (architecture, roadmap, changelog). Every code snippet compiled/run from the sample apps (no untested snippets).

## Key Insights
- Integrators mostly need 4 answers: how to activate, what each state means/what UI to show, how offline grace works (bounded by token exp, default `max(2x interval, 30)` min), what happens on revocation/deactivate.
- Ops needs a runbook: exporting backend `PublicKeyPem` per environment into app config; key rotation order (ship new `kid` in apps first, then rotate backend); TLS pin rotation order.
- Must state platform support honestly: Android, iOS, Windows x64 supported; Windows x86 = via `license-agent.exe`, **not yet available (deferred)**; Windows ARM64 = not supported (K/N limitation; x64 emulation on ARM64 Windows may run the x64 DLL inside x64 apps - untested, do not promise).

## Requirements
- Functional: a developer new to the SDK integrates on any supported platform using only the docs + published artifacts within 1 hour.
- Non-functional: docs in English only (confirmed, user 2026-10-12, no Vietnamese); snippets sourced from `samples/`.

## Architecture (doc set)
```
README.md                                   # what, platform matrix, quick start links
docs/system-architecture.md                 # layers, expect/actual seams, data flows (from Phase 2)
docs/license-lifecycle-and-offline-grace.md # states, transitions diagram (Mermaid v11), offline rules, revocation, clock guard
docs/integration-guide-android.md
docs/integration-guide-ios.md               # SwiftPM, Swift async usage, listener, Keychain notes
docs/integration-guide-windows.md           # C ABI, .NET P/Invoke, threading, no FreeLibrary, proxy, x86/ARM64 status
docs/error-codes-reference.md               # backend codes 1..24 -> SDK states (table from Phase 2)
docs/ops-signing-keys-and-pins-runbook.md   # export PublicKeyPem, kid rotation, TLS pin rotation
docs/project-roadmap.md                     # phases + deferred agent + known gaps
docs/project-changelog.md
docs/code-standards.md                      # update (created Phase 1)
```

## Related Code Files (ownership: this phase; Phase 9 must not touch)
- Create: all files above except `docs/code-standards.md`
- Modify: `README.md`, `docs/code-standards.md`
- Read-only: `samples/**` (snippet source), `docs/security-threat-model.md`, `docs/release-*.md`

## Implementation Steps
1. `system-architecture.md` + lifecycle doc with Mermaid state diagram (states from `LicenseState.kt`).
2. Per-platform guides: install, config (baseUrl, appSalt, trustedSigningKeys, orgId?, pins), activate, listen, check, deactivate, offline activation via `importOfflineToken` + `deviceId()`; platform caveats (Android backup exclusion; iOS static framework note; Windows threading/FreeLibrary/proxy).
3. Error reference table + recommended UX per state.
4. Ops runbook: key export SQL/admin steps (coordinate with backend team), rotation ordering, pin rotation ordering.
5. Roadmap (incl. Phase 6 deferred, ARM64 gap, cross-repo recommendations) + changelog.
6. Doc review: one dev per platform follows guide from scratch against RC artifacts; fix gaps.

## Todo List
- [x] Architecture + lifecycle docs - `docs/system-architecture.md`, `docs/license-lifecycle-and-offline-grace.md` (with Mermaid state diagram), written from actual source (`LicenseState.kt`, `LicenseRecheckLogic.kt`, `OfflineGracePolicy.kt`, `ClockRollbackGuard.kt`)
- [x] Android / iOS / Windows guides - all three written; Android/iOS guides explicitly flag every snippet as unverified (no SDK/macOS host, no sample app to link real snippets from - unlike Windows); Windows guide links the real, CI-run `samples/windows-c-smoke/main.c`
- [x] Error reference - `docs/error-codes-reference.md`, cross-checked against `LicenseErrorMapping.kt`
- [x] Ops runbook - `docs/ops-signing-keys-and-pins-runbook.md` (signing-key rotation, pin rotation, both with the deploy order that avoids an outage)
- [x] Roadmap + changelog + README - `docs/project-roadmap.md`, `docs/project-changelog.md`, README.md updated with a docs index
- [ ] Dry-run integration by fresh dev per platform - not done; needs a real Android SDK, macOS host, and RC artifacts, none available this session

## Success Criteria
- [ ] 3 fresh-dev dry runs succeed in <= 1h each using RC artifacts - not attempted (no RC artifacts exist yet; Phase 9's RC rehearsal is itself still open).
- [ ] Every snippet maps to code in `samples/` - true for Windows (links `samples/windows-c-smoke/main.c`); **not true** for Android/iOS, since no sample app exists for either yet - documented honestly in each guide's status caveat rather than fabricating a link.
- [x] Platform support matrix states x86 (deferred) and ARM64 (unsupported) explicitly - in README.md, `docs/integration-guide-windows.md`, and `docs/project-roadmap.md`.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| Docs drift from API | M x M | Snippets from samples; API changes require doc update in same PR (code-standards rule) |
| Integrators misread offline grace as unlimited | M x H | Lifecycle doc + guide callout with numbers |

## Security Considerations
- Docs must not include real keys/URLs; use placeholders. Emphasize: never log tokens; keep `appSalt` stable (changing it changes device id -> consumes new slot).

## Rollback
Docs-only; revert PR.

## Next Steps
GA release (Phase 9 workflow) once docs merged. Phase 6 revisited only on user request.

## Consolidated Unresolved Questions (all phases)
All 12 resolved by user, 2026-10-12 - see `plan.md` "Confirmed Decisions" for the full list. Summary: package `com.dcorp.licensecore` + GitHub Actions; Android minSdk 24, iOS min 14, no `iosX64`; `orgId` known to apps; revocation = explicit re-activate only; iOS static, no SKIE; Windows = .NET Framework + .NET 6 -> NuGet confirmed; Windows pinning still undecided, default no-pinning fallback; dev/staging env owned by user; Maven = GitHub Packages, distribution private, signing certs still unconfirmed; no binary tamper resistance for v1; cross-repo skyapp-api gaps fixed post-launch, not blocking; docs English only.

Still-open micro-decisions (non-blocking, decide during their phase): Windows DPAPI scope + proxy config (Phase 5), Windows pinning approach (Phase 7), signing cert availability (Phase 9), multi-process Android consumers (Phase 3), TLS cert rotation ownership (Phase 7), Android instrumented test cadence (Phase 8), iOS duplicate-K/N-runtime risk (Phase 4).

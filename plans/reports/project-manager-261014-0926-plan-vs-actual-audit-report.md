# Plan vs Actual Audit - 261012-1520-license-core-sdk-kmp-implementation

Date: 2026-10-14 | Auditor: project-manager (report-only, no plan/code edits)
Plan: `plans/261012-1520-license-core-sdk-kmp-implementation/plan.md` (+ phase-01..10)

## Method and limits
- No Bash tool in this session: `git log`, `git diff`, `./gradlew tasks` could NOT run. Gradle NOT re-run.
- Evidence used: file existence/content (Glob/Grep/Read), `.git/logs/HEAD`, `.git/config`, `git status` snapshot from session start, existing build outputs (`core/build/reports`, `core/build/test-results`, `core/build/bin`), and GitHub Actions web pages (`github.com/ldnghia/skylicense-core-sdk/actions`).
- Commit subjects from session snapshot: 4b443ce (ini project), 16dcf37 (Phase 7/8), a232601 (Phase 9), 5d7b738 (Phase 10), f137ac0 (merge to main).

## Headline
1. **release.yml is broken.** Actions shows release.yml runs #1, #2, #3 all `Failure` (run IDs 38339976354, 38342593108, 38342603995). Run #3 annotation: `.github/workflows/release.yml line 44, col 13: Unrecognized named-value: 'secrets'`. Local line 44: `if: ${{ secrets.APPLE_DISTRIBUTION_IDENTITY != '' }}`. GitHub forbids `secrets` in step `if:`. Plan says "never run" - it ran and failed at parse time, 3 times. Phase 9 status understated.
2. **CI outcome unknown.** CI #1 (4b443ce, 1m08s) and CI #2 (f137ac0, 1m24s) on main. No status shown by web. Full 3-OS build took ~13 min on first run per Phase 5, so 1m24s suggests early failure - inference only, unconfirmed. Phases 1/2/5/8 all cite CI as pending.
3. **Local mingwX64 is green.** `core/build/reports/tests/mingwX64Test/index.html`: 64 tests, 0 failures. Plan cites 43 (P2), 53 (P5), 59 (P7) - all stale. `licensecore.dll` present at `core/build/bin/mingwX64/releaseShared/licensecore.dll`.
4. **Kover is wired.** Plan (P2, P8) says "not wired / not added". Actual: `core/build.gradle.kts:7` plugin, `:74-94` verify rule >=85% LINE, `.github/workflows/ci.yml:24` runs `:core:koverVerify`. Coverage value still unmeasured.

## Per-phase verdicts

| # | Plan status | Verdict | Evidence |
|---|---|---|---|
| 1 | Done | **Discrepancy (over-claim on CI)** | Build scaffold present: `settings.gradle.kts`, `core/build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, `gradle-wrapper.jar`, `.editorconfig`, `.gitignore`, `docs/code-standards.md`. Version-literal rule holds: Grep for `[0-9]+\.[0-9]+["']` in all `*.kts` = 0 hits. Unchecked CI criteria (P1 lines 87-88) and "needs a pushed repo" text is stale - repo is pushed (`.git/config` origin = ldnghia/skylicense-core-sdk). CI not proven green. Frontmatter `done` vs Overview `Status: pending` stale. |
| 2 | Done | **Discrepancy (Done with 1 open criterion)** | All listed files exist in `core/src/commonMain/.../` (LicenseClient, LicenseConfig, api/*, token/*, cache/*, policy/*, scheduler/*, device/*, platform/*). Zero `TODO(` in `core/src` (Grep). 52 commonTest `@Test` + mingw. Open: Kover line coverage >=85% not measured (P2 line 163). Kover text in P2 line 163 "no Kover plugin wired yet" is stale. |
| 3 | Partial (no SDK) | **Confirmed** | `androidMain`: `KeystoreSecureStorage.kt`, `AndroidDeviceIdProvider.kt` (ANDROID_ID + `9774d56d682e549c` fallback), `AndroidHttpEngineFactory.kt` (OkHttp `certificatePinner`), `PlatformModule.android.kt`. Missing vs plan: instrumented tests (`androidDeviceTest`/`androidInstrumentedTest` - none exist), `samples/android-sample/` absent. `consumer-rules.pro` not wired (`core/build.gradle.kts:22-26` comment). Unblock: CI `linux` job already runs `:core:compileAndroidMain :core:testAndroidHostTest` on GitHub runner (has Android SDK) - not yet verified. |
| 4 | Partial (no macOS) | **Confirmed, with naming drift** | `iosMain`: `KeychainSecureStorage.kt`, `IosDeviceIdProvider.kt` (Keychain UUID, IDFV fallback), `DarwinHttpEngineFactory.kt` (`CertificatePinner`, `handleChallenge`), `PlatformModule.ios.kt`. Script is `scripts/package-ios-xcframework.sh`, plan says `build-ios-xcframework.sh` (P4 line 54). `samples/ios-sample/` absent. `cryptography-provider-apple` in `libs.versions.toml:28` and `core/build.gradle.kts:65` - but toml is `M` (uncommitted) at session start. Unblock: CI `macos` job runs `:core:iosSimulatorArm64Test` - never confirmed. |
| 5 | Done | **Discrepancy (over-claim)** | 14 `@CName("lc_*")` exports match ABI in `CApiExports.kt:41-151`. `include/licensecore.h` present. `DpapiSecureStorage.kt`, `DpapiFileStore.kt`, `WindowsDeviceIdProvider.kt`, `WinHttpEngineFactory.kt` present. DLL built. C smoke wired into CI (`ci.yml:47-49`). Open per own criteria (P5 lines 131-133): C smoke in CI (not run), .NET sample (`samples/windows-dotnet-sample/` absent), copy-to-other-machine test. Plan says Done with 3 open criteria. Test count "53/53" stale (report shows 64). Also `samples/windows-smoke-test/` (smoke_test.c, build-and-run.sh) exists, not in any phase doc; its `.exe/.dll` are gitignored (`.gitignore:39-41`). |
| 6 | Deferred | **Confirmed** | No x86 / named-pipe code in repo. README:12 states deferred. |
| 7 | Partial | **Confirmed, minor drift** | `docs/security-threat-model.md` present. `LicenseConfig.kt:15` `certificatePins`; `:19-25` https enforcement. Android/Darwin pinning present. Windows: `WinHttpEngineFactory.kt:16-18` prints warning (P7 says "logs once" - it logs per engine creation). Tests present: `RevocationEdgeCasesTest.kt`, `ClockEdgeCasesTest.kt`, `api/LogRedactionTest.kt`. `gradle/verification-metadata.xml` exists but `M` (uncommitted) at session start. **Missing:** Dependabot/Renovate config (P7 Impl Step 8) - no `.github/dependabot.yml` or `renovate.json`; not listed in Todo, so silent gap. Plan.md Unresolved Q7 still says Windows pinning "still undecided" while P7 says "resolved definitively" - contradiction. |
| 8 | Partial | **Discrepancy (Todo vs Success criteria contradict; text stale)** | `integration-tests/build.gradle.kts` + 6 mingw ITs (`ActivateFlowIT`, `ErrorCodesIT`, `FindFlowIT`, `RevocationFlowIT`, `DeviceLimitIT`, `OfflineTransitionIT`) + `IntegrationClient.kt`. `ci.yml:54-78` integration job (main/schedule, `LICENSE_IT_*` secrets). `docs/release-e2e-checklist.md` present. Kover wired (see headline 4). P8 Todo `[x] CI matrix + coverage gate` contradicts P8 Success `[ ] Kover coverage gate - not added` and text "did NOT wire it into CI" (contradicted by `ci.yml:24`). Integration suite not run (no dev backend; secrets unknown). `GoldenResponses.kt` built from assumed, not captured, responses. |
| 9 | Partial | **Discrepancy (broken workflow; status understated)** | Files exist: `Package.swift` (placeholder `0.0.0` / zero checksum), `scripts/package-ios-xcframework.sh`, `scripts/package-windows-dll.ps1`, `packaging/windows/licensecore.def`, `packaging/nuget/LicenseCore.Native.nuspec`, `docs/release-process.md`. Maven publication `core/build.gradle.kts:96-135` (URL slug matches origin). **`release.yml` fails at parse** (headline 1). Design risks in same file: (a) iOS job `git push origin HEAD:main` from a tag checkout (detached HEAD) - fails on non-fast-forward or branch protection; (b) `Package.swift` is patched after the tag, so the tagged commit keeps placeholder checksum. Plan text "never run" understates - it ran 3x and failed. |
| 10 | Partial | **Confirmed (existence + honesty)** | All 10 doc files listed in P10 exist: `system-architecture.md`, `license-lifecycle-and-offline-grace.md`, `integration-guide-{android,ios,windows}.md`, `error-codes-reference.md`, `ops-signing-keys-and-pins-runbook.md`, `project-roadmap.md`, `project-changelog.md`, `code-standards.md`, root `README.md`. README:10-13 platform matrix (x86 deferred, ARM64 unsupported) and README:76 unverified caveat match plan. Contents not line-audited. Roadmap status table not cross-checked against plan.md (grep found no phase-status lines). |

## Cross-cutting discrepancies
- **Stale frontmatter/overview:** P1, P2, P5 frontmatter `done` but Overview `Status: pending`. P3 frontmatter `partial` but Overview `pending`. Same pattern P4, P7-P10.
- **plan.md `status: pending`** while phases show Done/Partial.
- **Uncommitted at session start (git status):** `gradle/libs.versions.toml` (M), `gradle/verification-metadata.xml` (M), `plan.md` (MM), `phase-08` (M staged). Phase 4 dependency addition and Phase 7 verification metadata may not be committed.
- **Stale test counts:** plan cites 43 / 53 / 59; actual 64 (mingwX64, 0 failures).

## Next actions (owner: main agent unless noted; the plan is not finished until these close)
1. **Fix `release.yml` line 44** - move the `secrets` check to a job-level `env` var, then test `if: env.APPLE_IDENTITY != ''`. Done = release.yml run starts (no parse error). Owner: main agent.
2. **Read CI result** - open `actions/runs/<id>` for CI #2 (f137ac0) and check per-job logs (or `gh run view --log-failed`). Done = each of linux/macos/windows/integration marked pass/fail in this report. Owner: user (Nghia LD) - the web tool can't read job logs.
3. **Fix `ci.yml` / `core` if CI red** - run failing job locally where possible (mingwX64 yes; Android/iOS via CI). Done = 3 jobs green on main.
4. **Commit or revert uncommitted changes** (`libs.versions.toml`, `verification-metadata.xml`, plan files). Done = `git status` clean. Owner: main agent.
5. **Correct plan.md + phase statuses** to match this audit: P5 to Partial or Done-with-deferred; P8 Todo `[x] coverage gate` to `[ ]`; P9 to note failed release runs; add Dependabot/Renovate as open item in P7; refresh test counts. Done = no contradictions between Todo and Success criteria. Owner: main agent (use `ck plan` CLI if available).
6. **Decide Windows pinning wording** (plan.md Q7 vs P7 "resolved"). Owner: user.

**IMPORTANT: Finish the plan.** Phases 3, 4, 9 and 10 are blocked on verification that only CI can give. Until CI is green and release.yml runs, the "Done" and "Partial" labels are unverified claims.

## Unresolved questions
1. What are CI #1/#2 job outcomes on main? (Web view shows no status; needs owner check.)
2. Was `cryptography-provider-apple` / `verification-metadata.xml` change committed? (`git diff` needed; not runnable here.)
3. Is the 64-test mingw report from the current source revision? (Report mtime not checked.)
4. Is there branch protection on `main`? Affects release.yml `git push origin HEAD:main`.
5. Was Dependabot/Renovate dropped deliberately (P7 Impl Step 8)?
6. Will Kover `>=85%` pass on the linux job alone (only androidHost tests run there)? Likely partial coverage, unverified.
7. Who owns GitHub Packages consumer auth (Maven/NuGet need token)? Not in any phase.
8. Windows pinning: "undecided" (plan.md) vs "resolved" (P7) - which is authoritative?
9. `samples/windows-smoke-test/` - intended? Not referenced by any phase.

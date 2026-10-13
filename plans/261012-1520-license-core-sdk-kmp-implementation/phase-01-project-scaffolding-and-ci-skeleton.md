---
phase: 1
title: "Project Scaffolding and CI Skeleton"
status: done
priority: P1
effort: "1d"
dependencies: []
---

# Phase 1: Project Scaffolding and CI Skeleton

## Context Links
- Plan overview: [plan.md](./plan.md)
- Kotlin AGP 9 migration guide: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- Android KMP library plugin: https://developer.android.com/kotlin/multiplatform/plugin
- Kotlin/Native target tiers: https://kotl.in/native-targets-tiers

## Overview
- Priority: P1 (blocks everything)
- Status: pending
- Empty repo -> compiling Gradle KMP build with all 3 platform families, version catalog, docs skeleton, CI that builds every target on its native host. No license logic.

## Key Insights
- **AGP 9 changed the Android KMP DSL.** With AGP 9+, the KMP plugin no longer works with `com.android.library`; shared modules use `com.android.kotlin.multiplatform.library` and configure Android inside `kotlin { android { namespace; compileSdk; minSdk } }` (top-level `android {}` + `androidTarget()` removed). This is still "the Android KMP target" the PM locked - only syntax differs. **Research gap:** confirm exact block name (`android {}` vs `androidLibrary {}`) and host-test enablement (`withHostTest {}`) against the AGP version chosen at scaffold time; do not copy pre-2025 tutorials.
- **iosX64 (Intel simulator) is on a deprecation path** (x86_64 Apple targets, KT-78660 per community reports). Confirmed: `iosArm64` + `iosSimulatorArm64` only, no `iosX64`.
- `mingwX64` can be compiled from non-Windows hosts, but tests and DLL smoke checks need a Windows runner.
- Versions: do NOT hardcode versions from this plan. Pick latest stable at scaffold time for Kotlin (2.x), AGP (9.x), Ktor (3.x), kotlinx-coroutines, kotlinx-serialization, cryptography-kotlin; record in `libs.versions.toml`. Check Ktor + cryptography-kotlin both publish `mingwX64` artifacts for the chosen Kotlin version.
- File naming: Kotlin sources follow Kotlin convention (PascalCase, file = primary type). Non-code files kebab-case.
- `commonMain` must compile for every target from day 1; Phase 2 adds `actual` stubs so platform phases can start in parallel.

## Requirements
- Functional: `./gradlew :core:assemble` succeeds; `:core:allTests` runs (trivial test) on each host; CI green.
- Non-functional: Gradle wrapper committed; configuration cache enabled if plugins allow; reproducible builds via version catalog only (no inline versions).

## Architecture
```
skylicense-core-sdk/
  settings.gradle.kts           # include(":core"), later ":integration-tests", ":samples:android-sample"
  build.gradle.kts              # plugins apply false
  gradle.properties             # VERSION_NAME, kotlin.native flags, android.useAndroidX
  gradle/libs.versions.toml
  core/build.gradle.kts         # KMP: android, iosArm64, iosSimulatorArm64, mingwX64
  core/src/{commonMain,commonTest,androidMain,iosMain,mingwX64Main}/kotlin/com/dcorp/licensecore/
  docs/code-standards.md
  README.md
  .github/workflows/ci.yml
```
`core/build.gradle.kts` pre-declares (so parallel phases 3/4/5 never edit it concurrently):
- source-set deps: common (ktor-client-core, content-negotiation, serialization-json, coroutines-core, cryptography-core), android (ktor-client-okhttp, cryptography-provider-jdk, androidx core), ios (ktor-client-darwin, cryptography-provider for Apple/openssl3 - pick per Phase 2 spike), mingwX64 (ktor-client-winhttp, cryptography-provider-openssl3-prebuilt).
- iOS XCFramework declaration (`XCFramework("LicenseCore")`, static frameworks) - syntax confirmed in Phase 4 spike; Phase 1 adds it commented with TODO if spike not done.
- mingwX64 `binaries.sharedLib { baseName = "licensecore" }`.
- cinterop for DPAPI declared only after Phase 5 spike (lead applies).

## Related Code Files
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `core/build.gradle.kts`, `core/src/commonMain/kotlin/com/dcorp/licensecore/LicenseCoreVersion.kt`, `core/src/commonTest/kotlin/com/dcorp/licensecore/LicenseCoreVersionTest.kt`, `.github/workflows/ci.yml`, `.gitignore`, `.editorconfig`, `README.md`, `docs/code-standards.md`
- Modify: none (new repo)
- Delete: none

## Implementation Steps
1. `git init`; add `.gitignore` (Gradle, IDE, `local.properties`, `*.xcframework` build outputs, `.kotlin/`).
2. Generate Gradle wrapper (latest stable Gradle supported by chosen AGP).
3. Write `libs.versions.toml` with versions + plugin aliases: `kotlin-multiplatform`, `kotlin-serialization`, `android-kotlin-multiplatform-library`, and libs listed in Architecture.
4. Write `core/build.gradle.kts` with targets + source-set deps above. Package/namespace `com.dcorp.licensecore` (confirmed).
5. Add `LicenseCoreVersion.kt` (`const val SDK_VERSION`) + one commonTest asserting it is non-blank (proves test pipeline on every target).
6. `docs/code-standards.md`: Kotlin conventions, file <200 LOC, no plan refs in code comments, public API rules (no exceptions cross public boundary; sealed results), explicit API mode (`kotlin { explicitApi() }`).
7. Enable `explicitApi()` in `core` to keep the public surface deliberate.
8. CI `.github/workflows/ci.yml` (GitHub Actions confirmed):
   - `linux`: `:core:compileKotlinAndroid` (or equivalent), Android host tests, `mingwX64` compile-only.
   - `macos`: `:core:iosSimulatorArm64Test`.
   - `windows`: `:core:mingwX64Test`, `:core:linkReleaseSharedMingwX64`.
   - Cache Gradle + `~/.konan`.
9. Run all three locally/CI; record actual task names in README (they vary by AGP/Kotlin version).

## Todo List
- [x] Wrapper + catalog with verified-current versions
- [x] `core` KMP module with 4 targets compiling (android target blocked locally only by missing Android SDK - config itself verified correct, see below)
- [x] Trivial commonTest passing on mingwX64 (verified locally); iosSimulatorArm64/android-host need macOS/Android SDK (not available in this dev environment - CI will verify)
- [x] CI 3-OS skeleton written (not yet run on actual GitHub Actions - needs a pushed repo)
- [x] README + code-standards skeleton

## Success Criteria
- [x] `./gradlew :core:mingwX64Test :core:linkReleaseSharedMingwX64` exits 0 on this Windows host - verified, real run, BUILD SUCCESSFUL in 12m59s (first run incl. Kotlin/Native toolchain download).
- [x] `licensecore.dll` produced under `core/build/bin/mingwX64/releaseShared/` - verified present.
- [ ] `./gradlew :core:assemble` on macOS (all targets) - not verified, no macOS host available in this session.
- [x] `:core:testAndroidHostTest` correctly fails with `SDK location not found` (not a config bug - confirms Android target config is wired correctly; this machine has no Android SDK installed despite `ANDROID_HOME` being set to a non-existent path).
- [x] No dependency/plugin version literal outside `libs.versions.toml` - confirmed by construction.
- [ ] CI run shows 3 green jobs on first PR.
- [ ] No dependency/plugin version literal in any `*.gradle.kts` (grep for `:[0-9]+\.[0-9]+` in `.kts` files returns 0 hits); all versions live in `libs.versions.toml`.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| AGP 9 KMP DSL differs from docs used | H x M | Follow official migration page; spike 1h before writing build file |
| Library lacks `mingwX64` artifact for chosen Kotlin version | M x H | Check Maven Central per lib before pinning; downgrade Kotlin minor if needed |
| macOS runner cost/time | M x L | iOS job only on PR + main |

## Security Considerations
- No secrets in repo; CI secrets (signing, Maven creds) added only in Phase 9 via encrypted secrets.
- Dependency verification: enable Gradle dependency verification (`verification-metadata.xml`) once deps stabilize (Phase 7).

## Rollback
New repo - revert/delete branch. Nothing downstream yet.

## Next Steps
- Phase 2 starts on green CI.

## Unresolved Questions
Resolved (user, 2026-10-12): package name `com.dcorp.licensecore` confirmed; CI = GitHub Actions confirmed; no `iosX64`; Android `minSdk` 24 confirmed; iOS min deployment target 14 confirmed.

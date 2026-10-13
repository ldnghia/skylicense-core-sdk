# License Core SDK

Kotlin Multiplatform client SDK for the `skyapp-api` License Engine (`/api/licenses/*`): activation, device binding, token caching/validation, offline grace, revocation.

## Platform support

| Platform | Status |
|---|---|
| Android | Supported (KMP `android {}` target, minSdk 24) |
| iOS (arm64 device + arm64 simulator) | Supported (static XCFramework, min iOS 14) |
| Windows x64 | Supported (`mingwX64` DLL, flat C ABI for P/Invoke) |
| Windows x86 | Not yet available - planned via a separate `license-agent.exe` helper process (deferred, not started) |
| Windows ARM64 | Not supported - Kotlin/Native has no Windows ARM64 compilation target |

## Repository layout

```
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
core/                  # the SDK itself: commonMain + androidMain + iosMain + mingwX64Main
docs/code-standards.md
.github/workflows/ci.yml
plans/                 # implementation plan (phase-01 .. phase-10)
```

## Building

Requires JDK 17+. Each target family needs its native host:

| Target | Host required |
|---|---|
| `android` | Any host + Android SDK installed (`ANDROID_HOME`/`local.properties`) |
| `iosArm64`, `iosSimulatorArm64` | macOS with Xcode |
| `mingwX64` | Any host (cross-compiles); DLL smoke tests need Windows |

```bash
./gradlew :core:assemble
./gradlew :core:allTests
```

Key per-target tasks (verified against this project's actual `./gradlew :core:tasks --all` output on Kotlin 2.4.21 / AGP 9.4.1 - re-check after a Kotlin/AGP upgrade):

| Task | What it does |
|---|---|
| `:core:compileAndroidMain` | Compile Android target |
| `:core:testAndroidHostTest` | Android unit tests (JVM host) |
| `:core:compileKotlinMingwX64` | Compile Windows x64 target |
| `:core:mingwX64Test` | Windows x64 unit tests |
| `:core:linkReleaseSharedMingwX64` | Produce `licensecore.dll` |
| `:core:iosSimulatorArm64Test` | iOS unit tests (macOS host only) |
| `:core:assembleLicenseCoreXCFramework` | Produce the iOS XCFramework (macOS host only) |

## Status

Phase 1 (project scaffolding) - see [plans/261012-1520-license-core-sdk-kmp-implementation/plan.md](plans/261012-1520-license-core-sdk-kmp-implementation/plan.md) for the full roadmap.

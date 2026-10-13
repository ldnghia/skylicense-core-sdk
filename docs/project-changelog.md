# Changelog

All notable changes to the LicenseCore SDK. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); this SDK has not had a tagged release yet, so
everything so far is under `[Unreleased]`.

## [Unreleased]

### Added
- Core license client: activation (`activate`, `activateForStore`), restore, periodic re-check,
  deactivation, admin-mediated offline token import.
- Local ES256 JWT verification against app-configured trusted signing keys (no backend JWKS
  endpoint exists yet - see `docs/ops-signing-keys-and-pins-runbook.md`).
- Offline grace bounded by the cached token's own expiry, with a clock-rollback guard.
- Platform targets: Android (KMP `android {}`, minSdk 24), iOS (static XCFramework, min iOS 14,
  arm64 device + simulator only), Windows x64 (`mingwX64` shared lib with a flat C ABI).
- Certificate pinning on Android (OkHttp) and iOS (Darwin); documented as unsupported on Windows
  (`docs/security-threat-model.md`).
- Packaging/distribution scaffolding: Maven (GitHub Packages), SwiftPM binary target, Windows
  zip + NuGet native package, tag-triggered release workflow.
- Full documentation set: architecture, lifecycle/offline-grace semantics, error-code reference,
  per-platform integration guides, ops runbook, security threat model, release process.

### Known limitations (see `docs/project-roadmap.md` for detail)
- Android and iOS targets are code-complete but unverified - no Android SDK or macOS host was
  available during development.
- Windows x86 support is deferred (planned via a separate helper process, not started). Windows
  ARM64 is not supported (no Kotlin/Native compilation target).
- No release has been tagged yet; the release workflow has never been run end-to-end.
- Integration tests against a real backend need fixture licenses and golden-response capture from
  a live `skyapp-api` instance - not yet done.

[Unreleased]: https://github.com/ldnghia/skylicense-core-sdk/commits/main

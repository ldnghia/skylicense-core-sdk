# Release Process

How a `git tag vX.Y.Z` turns into published artifacts on all three platforms (Phase 9 of the
build plan). This doc was written without ever running the release workflow for real - no CI
secrets, no signing certs, and this dev sandbox has no network access to Maven/NuGet/SwiftPM
registries to rehearse against. Treat the steps below as the intended procedure; the first real
tag push is the actual rehearsal (Phase 9's own "RC rehearsal" todo item, still open).

## Versioning

- SemVer (`MAJOR.MINOR.PATCH[-rc.N]`), single source of truth: `VERSION_NAME` in `gradle.properties`.
- The C ABI has its own, independent version: `lc_abi_version()` in `include/licensecore.h`,
  bumped only on a breaking ABI change (adding a function is not breaking; changing a signature or
  removing one is). Do not conflate the two - an SDK patch release does not require an ABI bump.
- Releases are immutable. Never re-tag, re-push, or re-upload assets for a version that's already
  been published - a broken release gets a new patch version plus a deprecation note (see
  Rollback below), not an overwrite. SwiftPM in particular caches checksums client-side; changing
  a published zip without changing the version breaks every consumer silently.

## What a tag publishes

| Channel | Artifact | Where |
|---|---|---|
| Maven (Android/KMP) | root metadata + `-android` AAR (+ native klibs, unused but harmless) | GitHub Packages (`com.dcorp.licensecore`) |
| iOS | `LicenseCore.xcframework.zip` + SwiftPM `binaryTarget` | GitHub Release asset + `Package.swift` (committed) |
| Windows | `licensecore-win-x64-X.Y.Z.zip` (dll + import lib + header + README) | GitHub Release asset |
| Windows (NuGet) | `LicenseCore.Native.win-x64` | GitHub Packages NuGet feed |
| All | `SHA256SUMS.txt` | GitHub Release asset |

Distribution is private/internal only (Confirmed Decision, 2026-10-12) - none of this goes to a
public registry (no Maven Central, no public GitHub Release visibility beyond the org).

## Steps (automated by `.github/workflows/release.yml` on `git push --tags`)

1. **Linux job**: `./gradlew :core:publish` - publishes the Maven KMP publication to GitHub
   Packages using `GITHUB_TOKEN` (ambient in Actions, needs `packages:write`).
2. **macOS job**: `./gradlew :core:assembleLicenseCoreReleaseXCFramework`, then
   `scripts/package-ios-xcframework.sh <version> [identity]` (codesigns if `APPLE_DISTRIBUTION_IDENTITY`
   secret is set, else ships unsigned with a logged warning), computes the SwiftPM checksum,
   patches `Package.swift`'s `version`/`checksum` constants, commits that change, and uploads the
   zip to the GitHub Release.
3. **Windows job**: `./gradlew.bat :core:linkReleaseSharedMingwX64`, then
   `scripts/package-windows-dll.ps1 -Version <version> [-SignToolThumbprint ...]` (Authenticode-signs
   if a cert thumbprint secret is set, else ships unsigned with a logged warning), produces the
   import lib via `lib.exe /def:packaging/windows/licensecore.def`, zips it, and separately
   `nuget pack packaging/nuget/LicenseCore.Native.nuspec -Version <version>` then pushes to GitHub
   Packages.
4. **Final job**: downloads all uploaded assets, computes `SHA256SUMS.txt`, attaches it, and
   writes GitHub Release notes (content owned by Phase 10's `docs/project-changelog.md`).

## Secrets this needs (CI only, never local)

| Secret | Used by | If absent |
|---|---|---|
| `GITHUB_TOKEN` | Maven/NuGet publish to GitHub Packages | Required - job fails without it (it's ambient in Actions, not something to provision) |
| `APPLE_DISTRIBUTION_IDENTITY` | `codesign` on the XCFramework | Ships unsigned (logged warning); OK for internal RC, block GA |
| `WINDOWS_CODESIGN_THUMBPRINT` (+ the cert itself, imported into the runner's cert store separately) | `signtool sign` on licensecore.dll | Ships unsigned (logged warning); OK for internal RC, block GA |

Signing-cert availability (Authenticode + Apple Distribution) was still unconfirmed as of Phase 9
planning (2026-10-12) - ship RC unsigned internally per the risk-acceptance there; GA is blocked
on confirming these.

## Rollback / yank

Published versions are immutable (see Versioning). To "unpublish" a bad release:
1. Mark the GitHub Release as deprecated (edit the release notes with a `**DEPRECATED - use
   vX.Y.Z+1**` banner) - do not delete it, SwiftPM/Maven consumers already pinned to it would
   break harder on a 404 than on a deprecation notice.
2. Cut a new patch version with the fix and point consumers at it.
3. If a signing key or credential was compromised (not just a functional bug), rotate it before
   the next release and note the rotation in this doc's history - out of scope to pre-plan further
   here since no cert exists yet to rotate.

## Key-rotation coordination (cross-reference, not solved here)

If `skyapp-api` rotates its token-signing key (see `docs/security-threat-model.md`'s "Key rotation
gap"), ship an SDK release with the new `kid` added to the sample/integrating app's
`trustedSigningKeys` config *before* the backend starts signing with it. This is a deploy-ordering
process, not a release-workflow mechanic - coordinate the timing with whoever owns the backend
deploy, this doc only says what the SDK side of "shipping a release" requires.

## RC rehearsal (still open)

Phase 9's own todo list has not yet rehearsed `v0.1.0-rc.1` end to end - this requires a real
GitHub Actions run (secrets, three OS runners, actual registries), none of which this dev
sandbox can reach (no Android SDK/macOS host locally, and this session's network policy blocks
even Google's Maven for an ordinary Gradle build - see the build plan's Phase 8/9 status notes).
Treat every artifact shape and script above as unverified until that first real run.

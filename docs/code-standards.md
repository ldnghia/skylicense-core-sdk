# Code Standards

## Kotlin conventions
- Standard Kotlin style: PascalCase types, camelCase members, file name = primary type name.
- One primary declaration per file where practical; keep files under 200 lines (split by concern, not by arbitrary line count).
- `core` module runs `kotlin { explicitApi() }` - every public declaration needs an explicit visibility modifier and, for public API, an explicit return type.

## Public API rules
- No exception crosses a public API boundary, except `kotlinx.coroutines.CancellationException`. Public functions return sealed result types (`LicenseResult`, `LicenseState`) instead of throwing.
- Platform-specific behavior sits behind `expect`/`actual` interfaces declared in `commonMain`; platform modules only implement interfaces, never redeclare the public surface.

## Comments
- Explain the *why* (a non-obvious constraint, a workaround, a trade-off), never the *what* (that's what the code already says).
- No references to plan phases, finding codes, or audit labels in code comments or commit messages - those belong in `plans/` and PR descriptions. The reason for a piece of code must be self-contained and not depend on an external document that can be renumbered or deleted.

## Versioning
- `VERSION_NAME` in `gradle.properties` is the single source of truth for the SDK version (SemVer).
- The C ABI exposed from the Windows `mingwX64` target has its own independent version (`lc_abi_version`), bumped only on a breaking ABI change - not tied to `VERSION_NAME`.

## Testing
- `commonTest` covers all shared logic; platform `*Test` source sets cover only platform-specific `actual` implementations.
- No fake data, mocks used as a substitute for real logic, or skipped/ignored failing tests merely to make CI pass.

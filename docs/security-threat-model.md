# Security Threat Model

## Asset and attackers

Asset: the ability to run the licensed app. Primary attacker: the device owner trying to run
without paying (piracy). Secondary attacker: a network man-in-the-middle.

## What TLS pinning actually protects (and what already covers the rest without it)

| Attack | Without pinning | Already mitigated by |
|---|---|---|
| Forge a valid license token | Impossible | ES256 signature verified against embedded trusted public keys + `did` claim match (Phase 2 `LicenseTokenVerifier`) |
| Fake `/token/introspect` response to hide revocation | Possible via a user-installed root CA + a MITM proxy | Bounded: token `exp` is at most `max(2*interval, 30)` minutes (`LicenseApiService.cs:384-388`); any refresh needs a real server-signed token, and the server itself refuses an inactive license (`:167-169`) |
| Fake `/api/licenses/settings` response to force `allowOffline=true` | Possible - this endpoint is anonymous and its response unsigned (`LicenseSettingController.cs:12`) | Offline use is still bounded by the cached token's own `exp` regardless of what `allowOffline` claims |
| Block all network to extend offline use indefinitely | Possible | Bounded by token `exp` + `ClockRollbackGuard` (rejects a backward clock jump > 5 min) |
| Copy the cache file/Keychain item/Keystore blob to another device | N/A (not a network attack) | DPAPI (Windows, user+machine scoped)/Keychain (`AfterFirstUnlockThisDeviceOnly`)/Keystore (Android, non-exportable key) binding, plus the token's own `did` claim rejecting a mismatched device |
| Roll the clock back while offline | N/A | `ClockRollbackGuard` (Phase 2) |

**Conclusion: pinning is defense-in-depth, not a primary control.** It narrows the
revocation-suppression window (a MITM attacker with a trusted root CA replaying a stale
`introspect` response) down from "bounded by token exp" to "effectively zero," but every attack
in the table above is already bounded without it. This phase does not over-invest in pinning
reaching every platform at any cost - see the Windows decision below.

## Certificate pinning: per-platform decision

`LicenseConfig.certificatePins: Map<String, List<String>>` (host -> SHA-256 SPKI pin list,
already present since Phase 1/2) is the config surface. Always ship >= 2 pins (current key +
backup) so a routine cert rotation doesn't need an app update in lockstep; pin the intermediate
CA or the leaf's own key, not the root. Empty pins = pinning off (useful for a dev/staging host
with a self-signed or frequently-rotated cert).

| Platform | Decision | Mechanism |
|---|---|---|
| Android | Pin when `certificatePins` is non-empty | Ktor OkHttp engine, `OkHttpConfig.config { certificatePinner(...) } ` |
| iOS | Pin when `certificatePins` is non-empty | Ktor Darwin engine, `handleChallenge` with a `CertificatePinner` |
| Windows | **No pinning in v1** - see below | N/A |

### Windows: confirmed, not guessed

The plan flagged two options and left the decision as "chưa rõ" (undecided) pending a spike.
That spike ran in this session, on this machine, with real network checks against Maven Central
and Ktor's own API docs - not a guess:

- **Option (a) - Ktor's Curl engine with a pinned public key**: `ktor-client-curl` (and its
  `-mingwx64` variant) **do exist** on Maven Central for this project's Kotlin/Ktor versions, so
  the engine itself is usable on `mingwX64`. However, `CurlClientEngineConfig`'s public API
  exposes only `sslVerify`, `caInfo`, and `caPath` - **there is no `CURLOPT_PINNEDPUBLICKEY`
  equivalent exposed anywhere in Ktor's Curl engine configuration.** Achieving real pinning this
  way would mean bypassing Ktor's engine abstraction entirely and writing direct `libcurl`
  cinterop (`curl_easy_setopt(..., CURLOPT_PINNEDPUBLICKEY, ...)`) - a materially bigger task than
  "add a config block," closer to a new platform integration than a config change.
- **Option (b) - no pinning, rely on the mitigations in the table above**: chosen for v1. The
  WinHttp engine (the one actually in use - see Phase 5) has no pinning hook either way
  (`sslVerify` toggle only), so this isn't a regression versus the alternative engine; it's simply
  the cost of not taking on a custom libcurl interop for defense-in-depth against an attack class
  that's already bounded by token expiry.

This can be revisited post-v1 if a customer's threat model specifically requires it - the cost is
now known precisely (custom libcurl cinterop), not an open question.

## Key rotation gap (cross-repo, not solved here)

`skyapp-api` has no public-key/JWKS endpoint (`TokenSigningService.cs:135-156`); signing keys are
provisioned to the SDK via `LicenseConfig.trustedSigningKeys` (kid -> PEM), set by the integrating
app at build/config time. Rotation runbook (ops-facing, Phase 10): ship the SDK/app update with
the new `kid` added to `trustedSigningKeys` *before* the backend starts signing with it, so there
is never a window where a freshly-issued token is unverifiable. This is a deploy-ordering
constraint, not something the SDK can enforce by itself.

## Storage review (per platform)

| Platform | Mechanism | Accessibility / scope | Backup exclusion |
|---|---|---|---|
| Android | Keystore AES-256-GCM key (non-exportable) + ciphertext in private `SharedPreferences` | App-private; key never leaves Keystore | Not yet verified - `android:allowBackup` / `fullBackupContent` exclusion for `licensecore_secure.xml` and `licensecore_device_id_fallback.xml` should be confirmed in the sample app's manifest (Phase 3 follow-up; not blocking since the `did` claim rejects a cache restored onto a different device anyway) |
| iOS | Keychain generic password, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | Not synced to iCloud, not migrated by a device backup/restore | Covered by the accessibility flag itself |
| Windows | DPAPI (user scope) + file in `%LOCALAPPDATA%\LicenseCore\<hash>\` | Bound to the Windows user account via DPAPI; file ACL is the OS per-user default (not widened) | N/A (no OS-level app backup concept on Windows desktop) |

In all three cases, even a successfully-exfiltrated cache is useless on another device: the
token's `did` claim is checked against the new device's own hashed id and rejected on mismatch.

## Residual risks (explicitly not solved in v1)

- **Binary tamper resistance** (obfuscation, integrity/anti-debug checks): out of scope per the
  user's own 2026-10-12 decision. A sufficiently motivated attacker can patch the compiled
  binary/DLL to bypass license checks entirely; no control in this SDK defends against that.
- **Anonymous backend admin endpoints** (`/{id}/devices`, `/devices/release`, `/token/revoke`,
  `POST /settings` all lack `[Authorize]`): a cross-repo `skyapp-api` finding, not fixed here. The
  SDK itself never calls `/{id}/devices` for exactly this reason (see Phase 2).
  Documented as a backend owner's responsibility.
- **No JWKS/public-key endpoint**: key rotation is a manual, deploy-ordered process (see above),
  not something the SDK can self-serve.

## Security scan (Phase 7, run 2026-10-13)

Manual scan of `core/src/**/kotlin` for secrets, dependency risk, logging redaction, crypto
verification correctness, insecure storage, and general OWASP-relevant issues (no automated SAST
tool available in this environment - network-restricted sandbox, no SDK/macOS). No
critical/high findings.

- **Secrets**: none hardcoded. The only match for key/token-shaped strings is the intentional test
  sentinel `"SECRET-LICENSE-KEY-SHOULD-NEVER-APPEAR-IN-LOGS"` in `LogRedactionTest.kt`.
- **Dependencies**: Kotlin 2.4.21, AGP 9.4.1, Ktor 3.6.0, kotlinx-coroutines/serialization 1.11.0,
  cryptography-kotlin 0.6.0, androidx-core-ktx 1.19.1 - all current stable releases, none
  abandoned/known-malicious.
- **Logging**: no call site logs `licenseKey`/`licenseToken`. `LogRedactionTest.kt` covers the
  `verify()` transport-error path for real (`LicenseApiService.kt`'s `e.message ?: "network error"`
  construction); `introspect`/`release`/`find` share the same helper so are covered too, but there
  is no test asserting `BusinessError.message` (server-controlled) never echoes a secret - low
  risk since the SDK never interpolates secrets into any string, but worth a follow-up test.
- **Crypto** (`LicenseTokenVerifier.kt`): algorithm is hardcoded to ES256 regardless of the JWT
  header's `alg` (the header is only read for `kid`) - no algorithm-confusion/`alg:none`
  acceptance. Signature is verified before any claim is trusted; unknown `kid` is rejected;
  issuer, `did`, `nbf`/`exp` (with bounded skew) are all checked. No findings.
- **Storage**: Android Keystore AES-256-GCM (non-exportable key, random IV, wipes on
  decrypt/crypto failure) and iOS Keychain (`...AfterFirstUnlockThisDeviceOnly`) both reviewed, no
  insecure flags. Windows `DpapiFileStore` (reviewed in this pass, previously not located):
  user-scope `CryptProtectData`/`CryptUnprotectData` (not machine-scope), `CRYPTPROTECT_UI_FORBIDDEN`
  set, entropy scoped to the app salt hash - no findings.
- **OWASP-relevant**: no SQL/shell/dynamic-code injection, no insecure deserialization (JSON only,
  `kotlinx.serialization` with `ignoreUnknownKeys`), no reflection-based instantiation from network
  data.
  - **Fixed in this pass**: `LicenseConfig.baseUrl` previously accepted any scheme with no
    enforcement - a misconfigured integration could silently send license/device traffic over
    plaintext HTTP. `LicenseConfig` now requires `https://` (loopback `http://localhost` /
    `http://127.0.0.1` still allowed, for local dev/integration-test use against a local backend).
  - **Tracked, not fixed here**: `core/build.gradle.kts`'s Android target doesn't yet wire
    `consumer-rules.pro` into the AGP 9 KMP `android {}` DSL (the right API wasn't found - no
    Android SDK/docs access in this session to confirm it) - R8 may strip
    `kotlinx.serialization`'s generated serializers in a release AAR until this is resolved.
    Functional/correctness risk on release builds, not an active vulnerability pre-release.

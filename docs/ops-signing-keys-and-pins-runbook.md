# Ops Runbook: Signing Keys and Certificate Pins

This is a process document for whoever operates `skyapp-api` alongside whoever ships SDK/app
updates - both steps below are **deploy-ordering problems**, not something the SDK can self-serve,
because there is no dynamic discovery endpoint for either (no JWKS endpoint for signing keys, no
remote pin-update mechanism for certificate pins).

## Signing-key rotation

### Why this needs coordination at all

`skyapp-api` has no public-key/JWKS endpoint (`TokenSigningService.cs` auto-bootstraps a signing
key per environment, but never publishes it for discovery). The SDK instead verifies tokens
locally against public keys shipped in app config: `LicenseConfig.trustedSigningKeys` (`kid ->
PEM`). If the backend starts signing with a new `kid` before any app has that `kid` in its
config, every token verification for that app fails locally (`Invalid(TokenUntrusted)`) - a
**self-inflicted outage**, not a security event.

### The order that avoids an outage

1. **Backend team**: generate the new signing key, but do **not** switch `skyapp-api` to signing
   with it yet. Export its public key as PEM (SubjectPublicKeyInfo format) and hand it to whoever
   maintains the integrating apps' config, along with its `kid`.
2. **App/SDK-integration team**: add the new `kid -> PEM` entry to `trustedSigningKeys` *in
   addition to* the current one (do not remove the old key yet) and ship that update to the
   fleet. `LicenseTokenVerifier` accepts any `kid` present in the map, so this is backward
   compatible with tokens still signed by the old key.
3. **Wait** until telemetry (or just elapsed time >= your longest-lived token's `exp`, see
   `docs/license-lifecycle-and-offline-grace.md`) confirms the fleet has the new key. There is no
   SDK-side signal for "what fraction of the fleet has picked this up" - this has to be tracked
   externally (app analytics, staged rollout percentages, etc.).
4. **Backend team**: switch `skyapp-api` to sign new tokens with the new key.
5. Once the old key is fully retired backend-side and you're confident no device still holds a
   token signed by it (wait at least one full token lifetime past step 4), the old `kid` entry can
   be removed from `trustedSigningKeys` in a later app update - this step is optional cleanup, not
   required for correctness.

**Never do step 4 before step 2-3 have reached the whole fleet you care about.** There is no
emergency remote override if you get this wrong - the only recovery is pushing an app update with
the corrected key, which takes as long as your normal release/rollout process does.

### Exporting the public key (coordinate the exact steps with your `skyapp-api` DBA/ops team)

This repo doesn't own `skyapp-api`'s database or signing-key storage - the exact export mechanism
(SQL query, admin CLI, etc.) is out of this SDK repo's scope. What the SDK needs from that export
is: the key's `kid` (matches the token header), and its public key in PEM, SubjectPublicKeyInfo
format (i.e. what you'd get from `openssl ec -pubout` on the key pair, or equivalent for however
`skyapp-api` generates/stores it - `TokenSigningService.cs` is the source of truth for the exact
key type/format on the backend side).

## Certificate pin rotation (Android/iOS only - Windows has no pinning, see below)

### Why at least 2 pins, always

`LicenseConfig.certificatePins` is `host -> list of SHA-256 SPKI pins`. **Always configure at
least 2**: the currently-active pin, plus a backup (either the next key already generated but not
yet deployed on the server, or a pin on the issuing intermediate CA rather than the leaf cert,
which survives a routine leaf-cert renewal without any SDK-side change at all). A single pin with
no backup means a routine TLS certificate renewal on the backend bricks every pinned app
instantly, with no remote fix - functionally identical to the signing-key outage above.

### The order that avoids an outage

1. **Backend/infra team**: before renewing the TLS certificate, generate the *next* certificate
   (or confirm the next renewal will reuse the same key, in which case the pin doesn't change at
   all - pinning the SPKI hash of a stable key rather than the full leaf cert is one way to avoid
   needing this dance on every renewal).
2. Compute that next certificate's (or its intermediate's) SPKI SHA-256 pin:
   `openssl x509 -in next-cert.pem -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`
3. **App/SDK-integration team**: add that pin to `certificatePins` *alongside* the current one,
   ship the update, wait for fleet rollout (same caveat as signing keys: no SDK-side rollout
   telemetry).
4. **Backend/infra team**: renew/rotate the TLS certificate.
5. Optionally retire the old pin from `certificatePins` once you're confident no device needs it,
   in a later update.

### Windows has no pinning in v1

Do not include a Windows deployment in a pin-rotation rollout plan - `certificatePins` is accepted
but ignored on Windows (logged once, not silently dropped - see
`docs/integration-guide-windows.md`). This is a documented, accepted gap
(`docs/security-threat-model.md`), not an oversight to work around here.

## If you get the order wrong anyway

Both failure modes above look the same from the integrating app's side: devices start reporting
`Invalid(TokenUntrusted)` (signing key) or a transport failure that looks like a network outage but
isn't (pin mismatch). The fix in both cases is the same: ship an app update with the corrected
config, there is no faster remote remediation. This is the direct cost of accepting "no
JWKS/dynamic discovery" for v1 (a cross-repo recommendation not yet acted on, see
`docs/security-threat-model.md`) - weigh that cost before skipping the staged rollout steps above.

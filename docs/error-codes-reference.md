# Error Codes Reference

Backend business-error codes (`error` field in the `{success, error, message, data}` envelope,
mapped by `LicenseErrorMapping.kt`) to the `LicenseState` the SDK produces. The SDK never exposes
the raw numeric code in its public API - integrating apps switch on `LicenseState`/`reason`, not
on backend codes; this table exists for debugging and support, not for app logic to branch on.

| Backend code | SDK state | `Invalid.Reason` (if `Invalid`) | Recommended UX |
|---|---|---|---|
| 1 | `Invalid` | `NotFound` | "License key not found" - check for typos |
| 12 | `Invalid` | `InvalidArgument` | Generic "invalid request" - this is almost always an SDK/integration bug (malformed request), not a user-facing scenario |
| 13 | `Invalid` | `Inactive` | "This license is not active" - contact support/billing |
| 14 | `Expired` | n/a | Prompt for renewal (see lifecycle doc) |
| 15 | `Invalid` | `InvalidArgument` | Same as 12 |
| 16 (on `activate`) | `DeviceLimitReached` | n/a | "No free device slot" - ask the user to release a device, or contact support for a limit increase |
| 16 (on a periodic re-check / refresh) | `Revoked` | n/a | The device was released elsewhere since the last check - see lifecycle doc's revocation section. **Same backend code, different SDK state** depending on context - this is deliberate (`mapBusinessError`'s `isRefresh` parameter), not a bug if you see it differ between an activate failure and a later re-check |
| 17 (only ever seen from `deactivate()`'s own `release` call) | *(not `Invalid` - treated as success)* | n/a | `DEVICE_NOT_FOUND` on release means "already released" - the SDK clears its cache and reports `Unlicensed`, same as a successful release |
| 18, 19, 20 | `Invalid` | `InvalidArgument` | Same as 12 |
| 21 | `Invalid` | `InvalidArgument` | Same as 12 |
| 22 | `Invalid` | `NoLicenseAvailable` | Relevant to `activateForStore()`'s `find` call: no eligible license for this store/app/product combination |
| 23, 24 | `Invalid` | `TokenUntrusted` | The license token itself failed trust checks server-side (distinct from the SDK's own *local* `TokenUntrusted`, below) |
| *(any other/unrecognized code)* | `Invalid` | `Unknown` | Treat conservatively - re-verify online before concluding the license is actually invalid |

## Locally-produced states (no backend code involved)

These never come from a backend error code - they're produced by the SDK's own local token
verification or network-layer decisions:

| State | When |
|---|---|
| `Invalid(TokenUntrusted)` | The cached or imported token's signature doesn't verify against `trustedSigningKeys`, its `kid` is unknown (e.g. after an un-synced backend key rotation - see the ops runbook), or its `did` claim doesn't match this device |
| `NetworkRequired` | Offline grace exhausted (token `exp` reached while still offline), a clock-rollback was detected, or `allowOffline=false` and the network is unreachable |
| `Unlicensed` | No cache exists (first run, or after a successful `deactivate()`) |

See `docs/license-lifecycle-and-offline-grace.md` for the full state-transition diagram and the
offline-grace timing rules.

/*
 * License Core SDK - Windows x64 C ABI (licensecore.dll)
 *
 * Every char* return is a UTF-8 JSON string and MUST be freed via lc_free_string - never
 * free() it yourself, the allocator is internal to the DLL.
 *
 * Shape of every lc_* JSON response:
 *   {
 *     "state": "VALID" | "VALID_CACHED" | "OFFLINE" | "REVOKED" | "EXPIRED" |
 *              "DEVICE_LIMIT_REACHED" | "INVALID" | "NETWORK_REQUIRED" | "UNLICENSED" | "ERROR",
 *     "info": { "licenseKey": "...", "statusText": "...", "activationDate": "...", ... } | null,
 *     "offlineGraceUntilEpochSeconds": 1234567890 | null,   // only when state == "OFFLINE"
 *     "error": { "code": "...", "message": "..." } | null
 *   }
 *
 * Threading: the Kotlin/Native runtime attaches to any calling thread automatically. lc_* calls
 * block on network I/O (activate can take ~45s worst case: 3 retries x 15s timeout) - never call
 * from a UI thread. lc_get_state is the only non-blocking call (reads cached state, no network).
 *
 * Lifetime: never FreeLibrary() this DLL - the Kotlin/Native runtime cannot be unloaded cleanly.
 * Load it once per process and keep it loaded for the process lifetime.
 *
 * ABI version: starts at 1 (lc_abi_version). Bumped only on a breaking ABI change - independent
 * of the SDK's own semantic version.
 */
#ifndef LICENSECORE_H
#define LICENSECORE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void* lc_handle;

/* Creates a client from a JSON config:
 *   {"baseUrl":"https://...","orgId":1,"appSalt":"...",
 *    "trustedSigningKeys":{"v1":"-----BEGIN PUBLIC KEY-----..."},
 *    "connectTimeoutMillis":10000,"requestTimeoutMillis":15000}
 * Returns NULL on failure - call lc_last_error() for why. */
lc_handle lc_create(const char* config_json);

/* Activates by license key (auto-binds this device). */
char* lc_activate(lc_handle h, const char* license_key);

/* Same as lc_activate but with an optional caller-chosen device label (e.g. "Counter 1") an admin
 * can use to recognize this device later; pass NULL to behave exactly like lc_activate. Added
 * after ABI v1 shipped as a separate export so existing lc_activate callers are unaffected. */
char* lc_activate_with_device_name(lc_handle h, const char* license_key, const char* device_name);

/* Activates without a known license key: {"storeId":1,"appId":2,"accProductId":3,"deviceName":"Counter 1"}
 * (appId and/or accProductId required, accProductId optional; deviceName optional). */
char* lc_activate_for_store(lc_handle h, const char* request_json);

/* Imports a token issued out-of-band (admin-mediated offline activation). */
char* lc_import_offline_token(lc_handle h, const char* jwt);

/* Forces an immediate re-check instead of waiting for the background loop. */
char* lc_check_now(lc_handle h);

/* Non-blocking: returns the last-published state without any network call. */
char* lc_get_state(lc_handle h);

/* Releases this device's slot and clears the local cache. */
char* lc_deactivate(lc_handle h);

/* This device's hashed id (for admin-mediated offline activation). */
char* lc_device_id(lc_handle h);

/* Starts the background periodic re-check loop. Returns 1 on success, 0 on failure. */
int lc_start(lc_handle h);

/* Stops the background loop. Returns 1 on success, 0 if the handle was invalid. */
int lc_stop(lc_handle h);

/* Detail for the most recent internal failure on this thread (empty string if none). */
char* lc_last_error(void);

/* Frees any char* returned by an lc_* function above. */
void lc_free_string(char* s);

/* Stops the background loop (if running) and disposes the handle. Do not use h after this. */
void lc_destroy(lc_handle h);

/* ABI version, starts at 1. */
int lc_abi_version(void);

#ifdef __cplusplus
}
#endif

#endif /* LICENSECORE_H */

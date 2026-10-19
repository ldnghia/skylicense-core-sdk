# Windows Integration Guide

This is the one platform target that has actually been built and tested in this project (see
`docs/release-e2e-checklist.md` and Phase 5 of the build plan) - the C smoke test below is a real,
working sample (`samples/windows-c-smoke/main.c`), not written-but-unverified like the Android/iOS
guides.

## Platform support

| Arch | Status |
|---|---|
| x64 | Supported - `licensecore.dll`, flat C ABI |
| x86 | Not yet available - planned via a separate `license-agent.exe` named-pipe helper process (deferred, not started; see `docs/project-roadmap.md`) |
| ARM64 | Not supported - Kotlin/Native has no Windows ARM64 compilation target. Running the x64 DLL inside an x64-emulated app on Windows-on-ARM64 is architecturally plausible but **untested** - do not promise this to customers |

## Install

Download `licensecore-win-x64-<version>.zip` from the GitHub Release (see
`docs/release-process.md`), or reference the `LicenseCore.Native.win-x64` NuGet package for .NET
projects. The zip's own `README-windows.txt` (`packaging/windows/README-windows.txt`) repeats the
linking instructions below plus the threading/lifetime rules - keep it alongside the DLL.

## The C ABI

Every exported function is declared in `include/licensecore.h`, which documents the exact JSON
shape of every response, threading rules, and lifetime rules in its own header comment - read
that file, this guide only summarizes it. The short version:

- Every `char*` return is a UTF-8 JSON string and **must be freed with `lc_free_string`** - never
  `free()` it yourself (the allocator is internal to the DLL).
- `lc_*` calls that touch the network (`lc_activate`, `lc_check_now`, `lc_deactivate`, ...) can
  block for up to ~45s worst case (3 retries x 15s timeout) - **never call them from a UI thread**.
  `lc_get_state` is the only non-blocking call (reads cached state, no network).
- **Never `FreeLibrary()` this DLL.** The embedded Kotlin/Native runtime cannot be unloaded
  cleanly. Load it once per process and keep it loaded for the process's lifetime.

```c
#include "licensecore.h"

const char *config_json =
    "{\"baseUrl\":\"https://your-skyapp-api-host\",\"orgId\":123,"
    "\"appSalt\":\"a-stable-per-app-secret\","
    "\"trustedSigningKeys\":{\"v1\":\"-----BEGIN PUBLIC KEY-----\\n...\\n-----END PUBLIC KEY-----\"}}";

lc_handle h = lc_create(config_json);
if (h == NULL) {
    char *err = lc_last_error();
    /* log err */
    lc_free_string(err);
    return;
}

char *state_json = lc_activate(h, "THE-LICENSE-KEY");
/* parse state_json (see licensecore.h's JSON shape), then: */
lc_free_string(state_json);

/* To also label this device for easier admin lookup, use lc_activate_with_device_name instead:
 *   char *state_json = lc_activate_with_device_name(h, "THE-LICENSE-KEY", "Counter 1");
 * Pass NULL as the last argument to behave exactly like lc_activate. */

lc_start(h);   /* starts the periodic background re-check loop */
/* ... app runs ... */
lc_stop(h);
lc_destroy(h); /* releases the handle - do not use h after this */
```

A complete, working example (`lc_create` -> `lc_device_id` -> `lc_get_state` -> `lc_destroy`) is
`samples/windows-c-smoke/main.c`, built and run by `samples/windows-c-smoke/build-c-smoke.ps1`
against a real `licensecore.dll` on every Windows CI run.

## .NET (Framework or 6+)

P/Invoke directly against `licensecore.dll` - no import library needed for .NET:

```csharp
using System.Runtime.InteropServices;

internal static class LicenseCore
{
    [DllImport("licensecore.dll")] internal static extern IntPtr lc_create(string configJson);
    [DllImport("licensecore.dll")] internal static extern IntPtr lc_activate(IntPtr h, string licenseKey);
    [DllImport("licensecore.dll")] internal static extern IntPtr lc_activate_with_device_name(IntPtr h, string licenseKey, string deviceName);
    [DllImport("licensecore.dll")] internal static extern IntPtr lc_get_state(IntPtr h);
    [DllImport("licensecore.dll")] internal static extern void lc_free_string(IntPtr s);
    [DllImport("licensecore.dll")] internal static extern void lc_destroy(IntPtr h);
    // ... the rest of include/licensecore.h's exports, same pattern
}
```

Marshal every returned `IntPtr` with `Marshal.PtrToStringUTF8` (or `Marshal.PtrToStringAnsi` on
older .NET Framework without UTF-8 marshaling support - verify for your target framework version)
and free it with `lc_free_string`, not any .NET-side free. `licensecore.dll` must be alongside your
executable or on `PATH` - the NuGet package places it there automatically via
`runtimes/win-x64/native/`.

## MSVC (C/C++)

Link against `licensecore.lib` (the import library shipped in the zip, generated from
`packaging/windows/licensecore.def` via `lib.exe /def:... /machine:x64`), and ship
`licensecore.dll` alongside your executable.

## MinGW/gcc

Link directly against `licensecore.dll` - no import lib needed (`dlltool` can generate one from
the Kotlin/Native-produced `.def` if you need one for some other reason, but it's not required for
gcc). See `samples/windows-c-smoke/build-c-smoke.ps1` for the exact `gcc` invocation.

## Platform-specific notes

- **Secure storage**: DPAPI (`CryptProtectData`/`CryptUnprotectData`, **user scope**, not
  machine-scope), with the app salt as additional entropy, in a file under
  `%LOCALAPPDATA%\LicenseCore\<hash-of-appSalt>\`. File ACL is the OS per-user default (not
  widened) - this is adequate for the single-user-per-Windows-account case; a shared-machine
  deployment where multiple Windows users should see the same license state is out of scope (each
  Windows user account gets its own, independent cache).
- **Device id**: `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid`, stable across reinstall/update,
  resets only on an OS reinstall. **Known limitation**: cloned VM images deployed without sysprep
  share a `MachineGuid` and therefore a device slot - not fixed in the SDK, document this for any
  VM-based deployment.
- **Certificate pinning**: **not supported** on Windows in v1. WinHttp (the engine actually used)
  exposes no pinning hook, and Ktor's Curl engine (the alternative considered) doesn't expose
  `CURLOPT_PINNEDPUBLICKEY` either - see `docs/security-threat-model.md` for the full rationale
  and why this is an accepted, bounded risk (every attack pinning would block is already bounded
  by the token's own expiry). Passing a non-empty `certificatePins` on Windows logs a one-time
  warning and is otherwise ignored, rather than silently doing nothing.
- **Proxy configuration**: WinHttp uses the system proxy configuration by default (no explicit
  proxy option exposed in `LicenseConfig` as of this writing) - if your deployment requires a
  specific proxy, configure it at the OS/WinHTTP level (`netsh winhttp set proxy`), not through
  this SDK. This was flagged as an open decision during Phase 5 and defaulted to "use the system
  setting" rather than adding SDK-level proxy config - revisit if a deployment needs per-app proxy
  override.
- **Signing**: `licensecore.dll` is Authenticode-signed only if a code-signing certificate was
  available for that release - check the release notes / `SHA256SUMS.txt` rather than assuming.
  Verify with `signtool verify /pa licensecore.dll`.

## Known gaps

- x86 and ARM64 are not available (see the table above) - do not commit to a delivery date for
  these without checking `docs/project-roadmap.md`'s current status first.

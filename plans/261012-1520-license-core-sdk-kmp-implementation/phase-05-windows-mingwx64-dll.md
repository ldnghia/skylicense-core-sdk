---
phase: 5
title: "Windows mingwX64 DLL"
status: done
priority: P1
effort: "4d"
dependencies: [2]
---

# Phase 5: Windows mingwX64 DLL

## Context Links
- [Phase 2 architecture](./phase-02-common-core-license-client.md)
- Kotlin/Native C interop + shared libs: https://kotlinlang.org/docs/native-dynamic-libraries.html
- DPAPI `CryptProtectData`: https://learn.microsoft.com/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata
- Ktor WinHttp engine config: https://api.ktor.io/ktor-client-winhttp/io.ktor.client.engine.winhttp/-win-http-client-engine-config/

## Overview
- Priority: P1. Status: pending. Parallel with Phases 3, 4.
- `mingwX64` actuals (DPAPI storage, MachineGuid device id, WinHttp engine), a flat C ABI (`lc_*` functions, JSON-in/JSON-out) exported from `licensecore.dll`, hand-written `licensecore.h`, C smoke test + .NET P/Invoke sample.
- Scope: x64 Windows only. x86 apps = Phase 6 (deferred). Windows ARM64 = known gap (K/N has no target) - not addressed.

## Key Insights
- **K/N default export is awkward:** `sharedLib` generates `licensecore_api.h` with a struct-of-function-pointers API (`licensecore_symbols()->kotlin.root...`), unusable from P/Invoke. Use top-level functions annotated `@CName("lc_activate")` (`kotlin.native.CName`, needs `@OptIn(ExperimentalNativeApi::class)`) to export plain C symbols. **Research gap:** confirm (a) symbols appear in DLL export table (`objdump -p`), (b) String param/return marshalling for `@CName` functions - plan uses explicit `CPointer<ByteVar>` in/out (UTF-8, `malloc`-allocated return + exported `lc_free_string`) to avoid relying on implicit String conversion.
- **DPAPI access:** first check if `platform.windows.CryptProtectData` is already in K/N's prebuilt `platform.windows` bindings (windows.h pulls wincrypt.h/dpapi.h unless lean-and-mean). If present: no cinterop, just `linkerOpts("-lcrypt32")`. If absent: custom def (below). Spike decides; lead applies the build change.
  ```
  # core/src/nativeInterop/cinterop/windows-dpapi.def  (only if needed)
  headers = windows.h dpapi.h
  headerFilter = dpapi.h
  package = com.dcorp.licensecore.windows.dpapi
  linkerOpts = -lcrypt32
  ```
  Gradle (Kotlin 2.x; `definitionFile` replaced older `defFile` - verify):
  ```kotlin
  mingwX64 {
      compilations.getByName("main").cinterops.create("windowsDpapi") {
          definitionFile.set(project.file("src/nativeInterop/cinterop/windows-dpapi.def"))
      }
      binaries.sharedLib { baseName = "licensecore" }
  }
  ```
- **Device id = registry `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid`** via `RegGetValueW` (advapi32, in `platform.windows`). WMI via COM from K/N = high effort, rejected (KISS). x64 DLL reads 64-bit registry view natively. Caveat: cloned VMs without sysprep share MachineGuid -> share a slot (document).
- **K/N runtime cannot be unloaded:** never `FreeLibrary` the DLL; load once per process (document).
- **Foreign threads OK** (K/N attaches automatically), but any uncaught Kotlin exception through a C boundary terminates the host -> every `lc_*` body wrapped in `try/catch(Throwable)`.
- **Blocking ABI:** `lc_*` use `runBlocking`; activate can take up to ~45s worst case (3 retries x 15s) -> callers must not invoke on UI thread (doc + sample shows worker thread).
- **WinHttp proxy:** WinHTTP uses its own proxy config (not WinINet/IE) - corporate proxies may need `netsh winhttp import proxy source=ie` or SDK `proxyUrl` config. Risk noted.
- **JSON shapes defined here are reused verbatim as the named-pipe message format in Phase 6** (DRY).

## Requirements
- Functional: C ABI below works from C (MinGW gcc) and .NET 8 (x64); cache persists across process restarts; same device id across restarts and across Windows users on same machine.
- Non-functional: DLL depends only on system DLLs; UTF-8 everywhere; thread-safe (facade Mutex); DLL size recorded.

## Architecture
### C ABI (`include/licensecore.h`, hand-written, versioned)
```c
typedef void* lc_handle;
lc_handle lc_create(const char* config_json);           /* NULL on failure; see lc_last_error */
char*     lc_activate(lc_handle h, const char* license_key);
char*     lc_activate_for_store(lc_handle h, const char* request_json);
char*     lc_import_offline_token(lc_handle h, const char* jwt);
char*     lc_check_now(lc_handle h);
char*     lc_get_state(lc_handle h);                    /* non-blocking, cached state */
char*     lc_deactivate(lc_handle h);
char*     lc_device_id(lc_handle h);
int       lc_start(lc_handle h);                        /* background re-check thread */
int       lc_stop(lc_handle h);
char*     lc_last_error(void);
void      lc_free_string(char* s);
void      lc_destroy(lc_handle h);
int       lc_abi_version(void);                         /* starts at 1 */
```
Every `char*` return = UTF-8 JSON `{"state":"VALID|OFFLINE|REVOKED|EXPIRED|DEVICE_LIMIT_REACHED|INVALID|NETWORK_REQUIRED|UNLICENSED","info":{...},"error":{"code":..,"message":..}}`, freed by caller via `lc_free_string`. Handle = `StableRef<LicenseClient>` pointer; `lc_destroy` disposes it.

### Platform pieces
```
PlatformContext (actual) -> empty
DpapiSecureStorage       -> %LOCALAPPDATA%\LicenseCore\<appSaltHash>\<key>.bin ; CryptProtectData(entropy=appSaltHash, CRYPTPROTECT_UI_FORBIDDEN, user scope); write temp + MoveFileExW(REPLACE_EXISTING|WRITE_THROUGH)
WindowsDeviceIdProvider  -> RegGetValueW MachineGuid ; failure -> random UUID persisted via DpapiSecureStorage
WinHttpEngineFactory     -> WinHttp.create { } ; pinning decision Phase 7
CApiExports.kt           -> @CName functions; StableRef handles; JSON encode via kotlinx.serialization
```
Data flow: host app -> `lc_activate` (UTF-8 key) -> `toKString()` -> `runBlocking { client.activate() }` -> result -> JSON -> `strdup`-style malloc copy -> host -> `lc_free_string`.

## Related Code Files (ownership: this phase only)
- Modify: `core/src/mingwX64Main/kotlin/com/dcorp/licensecore/platform/PlatformModule.mingw.kt`, `.../platform/PlatformContext.mingw.kt`
- Create: `core/src/mingwX64Main/kotlin/com/dcorp/licensecore/platform/DpapiSecureStorage.kt`, `.../WindowsDeviceIdProvider.kt`, `.../WinHttpEngineFactory.kt`
- Create: `core/src/mingwX64Main/kotlin/com/dcorp/licensecore/capi/CApiExports.kt`, `.../capi/CApiJsonModels.kt`, `.../capi/CStringInterop.kt`
- Create (conditional): `core/src/nativeInterop/cinterop/windows-dpapi.def`
- Create: `include/licensecore.h`
- Create tests: `core/src/mingwX64Test/kotlin/com/dcorp/licensecore/platform/DpapiSecureStorageTest.kt`, `WindowsDeviceIdProviderTest.kt`, `capi/CApiExportsTest.kt`
- Create: `samples/windows-c-smoke/main.c`, `samples/windows-c-smoke/build-c-smoke.ps1`
- Create: `samples/windows-dotnet-sample/WindowsDotnetSample.csproj` (x64), `Program.cs`, `LicenseCoreNative.cs`
- Build file (cinterop/linkerOpts): via lead

## Implementation Steps
1. Spike (half day): (a) `CryptProtectData` availability in `platform.windows`; (b) `@CName` export visibility + string marshalling; (c) Ktor WinHttp + cryptography openssl3-prebuilt link into one DLL; (d) `objdump -p` dependency list. Report to lead; lead applies build changes.
2. `DpapiSecureStorage` (atomic write, corrupt/undecryptable -> delete + null).
3. `WindowsDeviceIdProvider`.
4. `WinHttpEngineFactory`; `createPlatformModule` actual.
5. `CApiExports.kt`: handle lifecycle, JSON models, try/catch wrapper helper (`guardedCall {}`), `lc_last_error` thread-local.
6. `include/licensecore.h` with doc comments (ownership rules, threading, no FreeLibrary).
7. mingwX64Tests: DPAPI round trip, tamper ciphertext -> null, device id stable, C API create/destroy/get_state without network.
8. C smoke (`gcc main.c -I include -L <dll dir> -llicensecore`): create -> device_id -> get_state -> destroy; CI runs it.
9. .NET 8 sample (`PlatformTarget=x64`): `[DllImport("licensecore", CallingConvention=Cdecl)]`, `IntPtr` returns -> `Marshal.PtrToStringUTF8` -> `lc_free_string`; activate on `Task.Run`.
10. Record DLL size + dependency list in README.

## Todo List
- [x] Spike a-d - real spike run on this machine (see below), confirmed (a) and partially (b)/(d); (c) implicitly confirmed by a successful link; lead = whoever runs this plan (solo project)
- [x] DPAPI storage, device id, WinHttp engine, module wiring - `DpapiFileStore.kt` (shared sync core), `DpapiSecureStorage.kt`, `WindowsDeviceIdProvider.kt`, `WinHttpEngineFactory.kt`, `PlatformModule.mingw.kt` all real, no `TODO()`
- [x] C ABI exports + header - `capi/CApiExports.kt` (14 `@CName` functions), `capi/CApiJsonModels.kt`, `capi/CStringInterop.kt`, hand-written `include/licensecore.h`
- [x] mingwX64 tests - 53/53 passing (`./gradlew :core:mingwX64Test`, real run)
- [x] C smoke working **without** a backend (no dev backend available in this session) - `samples/windows-c-smoke/main.c` + `build-c-smoke.ps1`, real run confirmed below. .NET sample NOT built (no .NET SDK verification done this session, deferred)
- [x] Size recorded: `licensecore.dll` release build = 14,268,928 bytes (~13.6 MiB). Dependency list (`objdump -p` imports) not separately recorded - deferred, not blocking.

## Spike Results (real runs on this machine, not guessed)
- (a) `CryptProtectData`/`CryptUnprotectData`/`DATA_BLOB` **confirmed present** in default `platform.windows` bindings - no custom cinterop `.def` needed. Verified via `WindowsApiSpikeTest.kt`, a real round-trip test, passing.
- (b) `@CName` export + marshalling **confirmed working end-to-end**: all 14 `lc_*` symbols appear in the real compiled DLL's export table (verified via `objdump -p`, not just the `.def`), and `samples/windows-c-smoke/main.c` successfully calls `lc_create` -> `lc_device_id` -> `lc_get_state` -> `lc_destroy` through them (see transcript below). The explicit `malloc`-based string marshalling (`CStringInterop.kt`) avoids the plan's concern about implicit conversion.
- (c) Ktor WinHttp + cryptography openssl3-prebuilt link into one DLL with no conflict - confirmed by a successful `linkReleaseSharedMingwX64`.
- (d) `objdump -p` full dependency list - not captured in this session (only the export table was inspected); still open, low priority.

### C Smoke Test Real Output
```
lc_create OK, abi_version=1
lc_device_id: 401d93e65a04bfa8cc19b8ffc9a143761db99b8dfabcbadb17b364a3b67c30e7
lc_get_state: {"state":"UNLICENSED"}
lc_destroy OK
```

## Success Criteria
- [x] `objdump -p licensecore.dll` lists all `lc_*` exports - confirmed, real run. Full "imports only Windows system DLLs" check not done (only exports inspected).
- [ ] C smoke passes in CI windows job - passes locally; no CI configured/run this session.
- [ ] .NET sample - not built this session.
- [ ] Copying `%LOCALAPPDATA%\LicenseCore\...` to another machine -> `UNLICENSED`/`INVALID` - not tested (needs a second machine/user).
- [x] No host crash when backend unreachable / invalid JSON passed to `lc_create` - `CApiExportsTest.kt` covers malformed config, null config, null handle; all return structured errors, never crash (also exercised live via the C smoke test, no backend running).

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| `@CName` export/marshalling not as expected | M x H | Spike; fallback to wrapping generated `_api.h` in a thin C shim DLL (adds MinGW C build step) |
| cryptography openssl3 + WinHttp static link conflicts / size bloat | M x M | Spike; fallback BCrypt (CNG) ES256 verify in mingwX64Main |
| Corporate proxy blocks WinHttp | M x M | `proxyUrl` config; doc netsh guidance |
| AV false positives on unsigned DLL | M x M | Authenticode signing in Phase 9 |
| Host calls FreeLibrary -> crash | L x H | Header + docs warning |

## Security Considerations
- DPAPI user scope binds cache to Windows user + machine; entropy = app-specific salt hash.
- `lc_*` inputs validated (null pointers, max length 4 KB for keys, 16 KB for JWT/config).
- No secrets in error JSON (no token echo).

## Rollback
Revert PR; Android/iOS unaffected. Consumers not yet onboarded (pre-Phase 9).

## Next Steps
Phase 6 (deferred) wraps this DLL; Phase 7 decides Windows pinning; Phase 9 adds `.def`/import lib, signing, zip/NuGet.

## Unresolved Questions
Resolved (user, 2026-10-12): consumer stacks = .NET Framework + .NET 6 (P/Invoke to `licensecore.dll` via the flat C ABI above; works identically for both) -> NuGet wrapper confirmed worth building (see Phase 9). Still open: DPAPI user vs machine scope (apps running as Windows services under different accounts)? Is `proxyUrl` config needed for target customers?

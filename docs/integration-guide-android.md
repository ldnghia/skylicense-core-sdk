# Android Integration Guide

> **Status caveat**: this guide describes the public API as written in
> `core/src/androidMain/kotlin/com/dcorp/licensecore/`. The Android target has never been built or
> run on a real Android SDK/emulator in this project's development so far (no SDK was available in
> the environments this SDK was built in) - treat the shape of every call below as correct, but
> verify against your own build before shipping. There is no sample Android app in `samples/` yet
> to link concrete, compiled snippets from (unlike the Windows C samples); the snippets here are
> written directly from the public API source, not copy-pasted from a running app.

## Install (once published - see `docs/release-process.md`)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.pkg.github.com/ldnghia/skylicense-core-sdk") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.dcorp.licensecore:core-android:<version>")
}
```

GitHub Packages requires authentication even for read access on a private repo - configure
`gpr.user`/`gpr.key` (or equivalent) credentials per your org's existing GitHub Packages setup.

## Configure and create a client

```kotlin
import com.dcorp.licensecore.LicenseClientFactory
import com.dcorp.licensecore.LicenseConfig
import com.dcorp.licensecore.platform.PlatformContext

val config = LicenseConfig(
    baseUrl = "https://your-skyapp-api-host",   // must be https:// (LicenseConfig enforces this)
    orgId = 123,
    appSalt = "a-stable-per-app-secret",          // see "appSalt" warning below
    trustedSigningKeys = mapOf(
        "v1" to "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
    ),
    certificatePins = mapOf(
        "your-skyapp-api-host" to listOf("base64-spki-sha256-pin-1", "base64-spki-sha256-pin-2"),
    ), // optional - empty map (default) disables pinning
)

val client = LicenseClientFactory.create(PlatformContext(applicationContext), config)
```

**`appSalt` must never change** once set for a given install - it's mixed into the hashed device
id sent to the backend (`DeviceIdHasher`). Changing it makes the backend see what looks like a
brand-new device, consuming another `MaxDevice` slot.

## Lifecycle

```kotlin
// On app start, before anything else touches license state:
val restored = client.restore()   // loads cache, no network call - fast, synchronous-feeling

// Then start the periodic background re-check (survives app-process lifetime via your own scope):
client.start(applicationScope)    // no-op if already running; call stop() in onDestroy/onCleared if needed

// Activation (first run, or after Unlicensed/Invalid/Expired):
val state = client.activate(licenseKey)
// or, if the device doesn't know its license key up front:
val state = client.activateForStore(storeId = 42, appId = 7)

// Observe state changes (StateFlow - collect from a lifecycle-aware scope):
viewLifecycleOwner.lifecycleScope.launch {
    client.state.collect { state -> renderUi(state) }
}

// Force an immediate check (e.g. pull-to-refresh on a license screen):
client.checkNow()

// Deactivate (e.g. "sign out" / "transfer license to another device"):
client.deactivate()
```

See `docs/license-lifecycle-and-offline-grace.md` for what each `LicenseState` means and the exact
offline-grace timing, and `docs/error-codes-reference.md` for `Invalid.reason` -> UX mapping.

## Admin-mediated offline activation

For devices that need to activate without ever reaching the backend (coordinate the out-of-band
token issuance with whoever administers your `skyapp-api` instance):

```kotlin
val deviceId = client.deviceId()   // hand this to the admin tooling that issues the token
// ... later, once you have the JWT:
val state = client.importOfflineToken(jwt)
```

## Platform-specific notes

- **Secure storage**: `KeystoreSecureStorage` uses a non-exportable Android Keystore AES-256-GCM
  key; ciphertext lives in a private `SharedPreferences` file. This is app-private by construction,
  but if your app sets `android:allowBackup="true"` (or a custom `fullBackupContent`), check
  whether `licensecore_secure.xml` and `licensecore_device_id_fallback.xml` should be excluded from
  backup - **this has not been verified in this project** (tracked in
  `docs/security-threat-model.md`'s storage-review table as a non-blocking follow-up, since a
  restored cache on a different device is rejected anyway via the token's `did` claim).
- **Device id**: `Settings.Secure.ANDROID_ID` (stable across reinstall, resets on factory reset),
  falling back to a random UUID persisted in plain (unencrypted) `SharedPreferences` only when
  `ANDROID_ID` is null or the known-bad emulator literal `9774d56d682e549c`.
- **Certificate pinning**: wired via OkHttp's `CertificatePinner` when `certificatePins` is
  non-empty. Always configure at least 2 pins (current + backup key/intermediate) so a routine
  cert rotation doesn't lock out your fleet - see the ops runbook for rotation ordering.
- **Multi-process apps**: if your app runs license-checking code in more than one process, each
  process gets its own `LicenseClient` instance and its own in-memory state - the on-disk cache is
  shared (same `SharedPreferences` file), but there's no cross-process locking beyond the
  single-process `Mutex` inside `LicenseClient`. Untested; avoid activating from two processes
  concurrently until this is verified (open item, see the build plan's Phase 3).
- **Threading**: all public `LicenseClient` methods are `suspend` - call from a coroutine, never
  block the main thread waiting on them.

## Known gaps (be honest with yourself before shipping)

- R8/ProGuard: `core/src/androidMain/consumer-rules.pro` exists but is **not yet wired into the
  Android target's build config** (an AGP 9 KMP API question still open as of this writing - see
  `core/build.gradle.kts`'s own comment). Until resolved, R8 may strip
  `kotlinx.serialization`'s generated serializers in a release (minified) build - test your actual
  release build, don't assume consumer rules are applied.
- Nothing here has been run against a real Android SDK/emulator in this project. Budget time for a
  first-integration shakeout pass before depending on this for a release.

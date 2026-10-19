package com.dcorp.licensecore.platform

import com.dcorp.licensecore.LicenseConfig

public actual fun createPlatformModule(context: PlatformContext, config: LicenseConfig): PlatformModule {
    val store = DpapiFileStore(fnv1aHex(config.appSalt))
    return PlatformModule(
        storage = DpapiSecureStorage(store),
        deviceIdProvider = WindowsDeviceIdProvider(store),
        deviceAboutProvider = WindowsDeviceAboutProvider(),
        httpEngine = WinHttpEngineFactory.create(config.certificatePins),
    )
}

/** Folder-name-safe, deterministic identifier derived from the app salt - not a security
 *  boundary by itself, DPAPI user-scope + this value as entropy together are. */
private fun fnv1aHex(value: String): String {
    var hash = 0xcbf29ce484222325uL
    for (byte in value.encodeToByteArray()) {
        hash = hash xor byte.toULong()
        hash *= 0x100000001b3uL
    }
    return hash.toString(16)
}

package com.dcorp.licensecore.platform

import com.dcorp.licensecore.LicenseConfig
import java.security.MessageDigest

public actual fun createPlatformModule(context: PlatformContext, config: LicenseConfig): PlatformModule {
    val keyAlias = "licensecore.${sha256Hex(config.appSalt)}"
    return PlatformModule(
        storage = KeystoreSecureStorage(context.androidContext, keyAlias),
        deviceIdProvider = AndroidDeviceIdProvider(context.androidContext),
        deviceAboutProvider = AndroidDeviceAboutProvider(),
        httpEngine = AndroidHttpEngineFactory.create(config.certificatePins),
    )
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
}

package com.dcorp.licensecore.device

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Hashes a raw device identifier with the app salt before it ever leaves the device, so the
 * backend (whose `DeviceId` column caps at 255 chars - `LicenseDeviceBuilder.cs:18`) only ever
 * sees a stable, non-reversible 64-char hex string.
 */
public object DeviceIdHasher {
    public suspend fun hash(rawDeviceId: String, appSalt: String): String {
        val hasher = CryptographyProvider.Default.get(SHA256).hasher()
        val digest = hasher.hash((rawDeviceId + appSalt).encodeToByteArray())
        return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
    }
}

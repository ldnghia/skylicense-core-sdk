package com.dcorp.licensecore.platform

/** Platform-encrypted key-value storage (DPAPI on Windows, Keychain on iOS, EncryptedSharedPreferences-equivalent on Android). */
public interface SecureStorage {
    public suspend fun get(key: String): ByteArray?
    public suspend fun put(key: String, bytes: ByteArray)
    public suspend fun remove(key: String)
}

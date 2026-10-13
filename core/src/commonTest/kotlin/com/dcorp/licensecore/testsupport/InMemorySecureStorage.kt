package com.dcorp.licensecore.testsupport

import com.dcorp.licensecore.platform.SecureStorage

public class InMemorySecureStorage : SecureStorage {
    private val map = mutableMapOf<String, ByteArray>()

    override suspend fun get(key: String): ByteArray? = map[key]
    override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
    override suspend fun remove(key: String) { map.remove(key) }
}

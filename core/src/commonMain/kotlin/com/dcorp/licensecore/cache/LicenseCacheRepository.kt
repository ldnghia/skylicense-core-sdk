package com.dcorp.licensecore.cache

import com.dcorp.licensecore.platform.SecureStorage
import kotlinx.serialization.json.Json

internal class LicenseCacheRepository(private val storage: SecureStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): CachedLicenseRecord? {
        val bytes = storage.get(CACHE_KEY) ?: return null
        val record = runCatching { json.decodeFromString<CachedLicenseRecord>(bytes.decodeToString()) }.getOrNull()
            ?: return null
        return record.takeIf { it.schemaVersion == CachedLicenseRecord.CURRENT_SCHEMA_VERSION }
    }

    suspend fun save(record: CachedLicenseRecord) {
        storage.put(CACHE_KEY, json.encodeToString(record).encodeToByteArray())
    }

    suspend fun clear() {
        storage.remove(CACHE_KEY)
    }

    private companion object {
        const val CACHE_KEY = "com.dcorp.licensecore.cache.v1"
    }
}

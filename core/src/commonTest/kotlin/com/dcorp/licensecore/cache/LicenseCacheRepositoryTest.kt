package com.dcorp.licensecore.cache

import com.dcorp.licensecore.LicenseInfo
import com.dcorp.licensecore.testsupport.InMemorySecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicenseCacheRepositoryTest {
    private fun sampleRecord() = CachedLicenseRecord(
        schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
        token = "jwt-token",
        info = LicenseInfo("KEY-1", "Active", null, null, null, null, false),
        allowOffline = true,
        checkIntervalMinutes = 60,
        lastOnlineCheckEpochSeconds = 1000,
        maxObservedEpochSeconds = 1000,
    )

    @Test
    fun saveThenLoad_roundTrips() = runTest {
        val repo = LicenseCacheRepository(InMemorySecureStorage())
        repo.save(sampleRecord())
        assertEquals(sampleRecord(), repo.load())
    }

    @Test
    fun loadWithNothingSaved_returnsNull() = runTest {
        assertNull(LicenseCacheRepository(InMemorySecureStorage()).load())
    }

    @Test
    fun clear_removesTheRecord() = runTest {
        val repo = LicenseCacheRepository(InMemorySecureStorage())
        repo.save(sampleRecord())
        repo.clear()
        assertNull(repo.load())
    }

    @Test
    fun corruptBytes_loadAsNullInsteadOfThrowing() = runTest {
        val storage = InMemorySecureStorage()
        storage.put("com.dcorp.licensecore.cache.v1", "not-json".encodeToByteArray())
        assertNull(LicenseCacheRepository(storage).load())
    }

    @Test
    fun unknownSchemaVersion_loadAsNull() = runTest {
        val storage = InMemorySecureStorage()
        val repo = LicenseCacheRepository(storage)
        repo.save(sampleRecord().copy(schemaVersion = 999))
        assertNull(repo.load())
    }
}

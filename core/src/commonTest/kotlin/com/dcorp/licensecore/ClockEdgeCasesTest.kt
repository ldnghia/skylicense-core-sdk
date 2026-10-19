package com.dcorp.licensecore

import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.LicenseHttpClientFactory
import com.dcorp.licensecore.cache.CachedLicenseRecord
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.device.DeviceIdHasher
import com.dcorp.licensecore.testsupport.FixedDeviceAboutProvider
import com.dcorp.licensecore.testsupport.FixedDeviceIdProvider
import com.dcorp.licensecore.testsupport.InMemorySecureStorage
import com.dcorp.licensecore.testsupport.TestClock
import com.dcorp.licensecore.testsupport.TestTokenSigner
import com.dcorp.licensecore.token.LicenseTokenVerifier
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/** Phase 7, step 5: clock edge cases through the real [LicenseClient] recheck path, not just the
 *  pure [com.dcorp.licensecore.policy.ClockRollbackGuard] unit tests. */
class ClockEdgeCasesTest {
    private val rawId = "raw-fixed"
    private val appSalt = "salt"

    @Test
    fun clockRolledBackMoreThan5Minutes_whileOffline_requiresOnlineEvenThoughTokenStillValid() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val lastObserved = 1_700_000_000L
        val rolledBack = lastObserved - 6 * 60 // 6 minutes back, beyond the 5-minute tolerance
        val token = signer.sign(nowEpochSeconds = rolledBack, lifetimeSeconds = 7200, deviceId = id) // not expired

        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf(signer.kid to signer.publicKeyPem()))
        val cache = LicenseCacheRepository(InMemorySecureStorage())
        cache.save(
            CachedLicenseRecord(
                schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                token = token,
                info = LicenseInfo("K", "Active", null, null, null, null, false),
                allowOffline = true,
                checkIntervalMinutes = 60,
                lastOnlineCheckEpochSeconds = lastObserved,
                maxObservedEpochSeconds = lastObserved,
            ),
        )
        // Network unreachable - if the clock guard didn't trip, this would otherwise qualify for offline grace.
        val engine = MockEngine { throw RuntimeException("network down") }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            deviceAboutProvider = FixedDeviceAboutProvider(),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = cache,
            clock = TestClock(rolledBack),
        )

        assertIs<LicenseState.NetworkRequired>(client.checkNow())
    }

    @Test
    fun clockJumpsForward_whileOffline_stillHonorsOfflineGraceUntilTokenExpiry() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val lastObserved = 1_700_000_000L
        val jumpedForward = lastObserved + 3600 // 1 hour forward - not a rollback, no tamper signal
        val token = signer.sign(nowEpochSeconds = lastObserved, lifetimeSeconds = 7200, deviceId = id) // expires well after jumpedForward

        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf(signer.kid to signer.publicKeyPem()))
        val cache = LicenseCacheRepository(InMemorySecureStorage())
        cache.save(
            CachedLicenseRecord(
                schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                token = token,
                info = LicenseInfo("K", "Active", null, null, null, null, false),
                allowOffline = true,
                checkIntervalMinutes = 60,
                lastOnlineCheckEpochSeconds = lastObserved,
                maxObservedEpochSeconds = lastObserved,
            ),
        )
        val engine = MockEngine { throw RuntimeException("network down") }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            deviceAboutProvider = FixedDeviceAboutProvider(),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = cache,
            clock = TestClock(jumpedForward),
        )

        val state = client.checkNow()
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Offline>(valid.mode)
    }
}

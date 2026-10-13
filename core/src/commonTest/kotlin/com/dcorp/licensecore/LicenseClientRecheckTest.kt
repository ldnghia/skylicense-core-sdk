package com.dcorp.licensecore

import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.LicenseHttpClientFactory
import com.dcorp.licensecore.cache.CachedLicenseRecord
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.device.DeviceIdHasher
import com.dcorp.licensecore.testsupport.FixedDeviceIdProvider
import com.dcorp.licensecore.testsupport.InMemorySecureStorage
import com.dcorp.licensecore.testsupport.TestClock
import com.dcorp.licensecore.testsupport.TestTokenSigner
import com.dcorp.licensecore.token.LicenseTokenVerifier
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LicenseClientRecheckTest {
    private val now = 1_700_000_000L
    private val rawId = "raw-fixed"
    private val appSalt = "salt"

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(body: String) =
        respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun setup(
        signer: TestTokenSigner,
        token: String,
        checkIntervalMinutes: Int = 60,
        respondTo: (path: String) -> String,
    ): Pair<LicenseClient, InMemorySecureStorage> {
        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf(signer.kid to signer.publicKeyPem()))
        val storage = InMemorySecureStorage()
        val cache = LicenseCacheRepository(storage)
        cache.save(
            CachedLicenseRecord(
                schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                token = token,
                info = LicenseInfo("K", "Active", null, null, null, null, false),
                allowOffline = true,
                checkIntervalMinutes = checkIntervalMinutes,
                lastOnlineCheckEpochSeconds = now - 60,
                maxObservedEpochSeconds = now - 60,
            ),
        )
        val engine = MockEngine { request -> jsonResponse(respondTo(request.url.encodedPath)) }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = cache,
            clock = TestClock(now),
        )
        return client to storage
    }

    @Test
    fun recheck_revoked_clearsCacheAndPublishesRevoked() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val token = signer.sign(nowEpochSeconds = now - 60, lifetimeSeconds = 7200, deviceId = id)
        val (client, storage) = setup(signer, token) {
            """{"success":true,"data":{"isValid":false,"isRevoked":true,"isExpired":false}}"""
        }
        assertIs<LicenseState.Revoked>(client.checkNow())
        assertNull(LicenseCacheRepository(storage).load())
    }

    @Test
    fun recheck_networkDown_remainsValidOfflineWithinGrace() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val token = signer.sign(nowEpochSeconds = now - 60, lifetimeSeconds = 7200, deviceId = id)
        val (client, _) = setup(signer, token) { throw kotlin.RuntimeException("network down") }
        val state = client.checkNow()
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Offline>(valid.mode)
    }

    @Test
    fun recheck_networkDownAndTokenExpired_requiresOnline() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val token = signer.sign(nowEpochSeconds = now - 7200, lifetimeSeconds = 3600, deviceId = id)
        val (client, _) = setup(signer, token) { throw kotlin.RuntimeException("network down") }
        assertIs<LicenseState.NetworkRequired>(client.checkNow())
    }

    @Test
    fun recheck_remainingBelowInterval_triggersRefresh() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        // 5 minutes left, but checkIntervalMinutes = 60 -> remaining < interval -> refresh
        val oldToken = signer.sign(nowEpochSeconds = now, lifetimeSeconds = 300, deviceId = id)
        val newToken = signer.sign(nowEpochSeconds = now, lifetimeSeconds = 7200, deviceId = id)
        val (client, _) = setup(signer, oldToken, checkIntervalMinutes = 60) { path ->
            when {
                path.endsWith("/token/introspect") -> """{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}"""
                path.endsWith("/verify") -> """{"success":true,"data":{"isValid":true,"licenseKey":"K","licenseToken":"$newToken","status":1,"statusText":"Active","isTrial":false,"applicationRegisterId":1,"accProductId":1,"storeId":1}}"""
                path.endsWith("/settings") -> """{"success":true,"data":{"allowOffline":true,"licenseCheckIntervalMinutes":60}}"""
                else -> """{"success":false,"error":1,"message":"unexpected"}"""
            }
        }
        val state = client.checkNow()
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Online>(valid.mode)
    }

    @Test
    fun recheck_plentyRemaining_staysValidOnlineWithoutRefresh() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val token = signer.sign(nowEpochSeconds = now, lifetimeSeconds = 7200, deviceId = id)
        val (client, _) = setup(signer, token, checkIntervalMinutes = 60) { path ->
            if (path.endsWith("/token/introspect")) {
                """{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}"""
            } else {
                error("should not call $path when remaining time is plenty")
            }
        }
        val state = client.checkNow()
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Online>(valid.mode)
    }
}

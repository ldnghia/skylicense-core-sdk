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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Edge cases the data-flow matrix calls out explicitly (Phase 7, step 4). */
class RevocationEdgeCasesTest {
    private val now = 1_700_000_000L
    private val rawId = "raw-fixed"
    private val appSalt = "salt"

    private fun MockRequestHandleScope.json(body: String) =
        respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun tokenWithUnknownKidAfterKeyRotation_isTokenUntrusted() = runTest {
        val signerV1 = TestTokenSigner.create(kid = "v1")
        val id = DeviceIdHasher.hash(rawId, appSalt)
        // Token signed by a key the client no longer trusts (rotated out).
        val staleToken = signerV1.sign(nowEpochSeconds = now, deviceId = id)

        // Client only trusts v2 now.
        val signerV2 = TestTokenSigner.create(kid = "v2")
        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf("v2" to signerV2.publicKeyPem()))
        val storage = InMemorySecureStorage()
        val cache = LicenseCacheRepository(storage)
        cache.save(
            CachedLicenseRecord(
                schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                token = staleToken,
                info = LicenseInfo("K", "Active", null, null, null, null, false),
                allowOffline = true,
                checkIntervalMinutes = 60,
                lastOnlineCheckEpochSeconds = now - 60,
                maxObservedEpochSeconds = now - 60,
            ),
        )
        val engine = MockEngine { json("""{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}""") }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = cache,
            clock = TestClock(now),
        )

        val state = client.checkNow()
        val invalid = assertIs<LicenseState.Invalid>(state)
        assertEquals(LicenseState.Invalid.Reason.TokenUntrusted, invalid.reason)
    }

    @Test
    fun deviceReleasedElsewhere_refreshSeesDeviceLimitReached_mapsToRevoked() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val oldToken = signer.sign(nowEpochSeconds = now, lifetimeSeconds = 60, deviceId = id) // about to expire -> refresh
        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf(signer.kid to signer.publicKeyPem()))
        val storage = InMemorySecureStorage()
        val cache = LicenseCacheRepository(storage)
        cache.save(
            CachedLicenseRecord(
                schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                token = oldToken,
                info = LicenseInfo("K", "Active", null, null, null, null, false),
                allowOffline = true,
                checkIntervalMinutes = 60,
                lastOnlineCheckEpochSeconds = now - 60,
                maxObservedEpochSeconds = now - 60,
            ),
        )
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/token/introspect")) {
                json("""{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}""")
            } else {
                // Device was released on another device between introspect and this refresh.
                json("""{"success":false,"error":16,"message":"Device limit reached"}""")
            }
        }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = cache,
            clock = TestClock(now),
        )

        assertIs<LicenseState.Revoked>(client.checkNow())
    }

    @Test
    fun concurrentActivateCalls_areSerializedByMutex_noCorruption() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash(rawId, appSalt)
        val token = signer.sign(nowEpochSeconds = now, deviceId = id)
        val config = LicenseConfig("https://license.test", 1, appSalt, mapOf(signer.kid to signer.publicKeyPem()))
        var callCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/verify") -> {
                    callCount++
                    json("""{"success":true,"data":{"isValid":true,"licenseKey":"K","licenseToken":"$token","status":1,"statusText":"Active","isTrial":false,"applicationRegisterId":1,"accProductId":1,"storeId":1}}""")
                }
                request.url.encodedPath.endsWith("/settings") -> json("""{"success":true,"data":{"allowOffline":true,"licenseCheckIntervalMinutes":60}}""")
                else -> json("""{"success":false,"error":1,"message":"unexpected"}""")
            }
        }
        val client = LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider(rawId),
            api = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = LicenseCacheRepository(InMemorySecureStorage()),
            clock = TestClock(now),
        )

        val results = listOf(async { client.activate("K") }, async { client.activate("K") }).awaitAll()
        assertEquals(2, callCount) // both ran to completion, serialized - not interleaved/dropped
        for (state in results) assertIs<LicenseState.Valid>(state)
    }
}

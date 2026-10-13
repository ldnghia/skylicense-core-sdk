package com.dcorp.licensecore

import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.LicenseHttpClientFactory
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

class LicenseClientActivateTest {
    private val now = 1_700_000_000L
    private val deviceId = "fixed-device-hash"

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(body: String) =
        respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun buildClient(
        signer: TestTokenSigner,
        storage: InMemorySecureStorage = InMemorySecureStorage(),
        respondTo: (path: String) -> String,
    ): LicenseClient {
        val config = LicenseConfig("https://license.test", orgId = 1, appSalt = "salt", trustedSigningKeys = mapOf(signer.kid to signer.publicKeyPem()))
        val engine = MockEngine { request ->
            jsonResponse(respondTo(request.url.encodedPath))
        }
        val http = LicenseHttpClientFactory.create(engine, config)
        return LicenseClient(
            config = config,
            deviceIdProvider = FixedDeviceIdProvider("raw-fixed"),
            api = LicenseApiService(http, config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = LicenseCacheRepository(storage),
            clock = TestClock(now),
        )
    }

    @Test
    fun activate_validResponse_publishesValidOnline() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash("raw-fixed", "salt")
        val token = signer.sign(nowEpochSeconds = now, deviceId = id)
        val client = buildClient(signer) { path ->
            when {
                path.endsWith("/verify") -> """{"success":true,"data":{"isValid":true,"licenseKey":"K","licenseToken":"$token","status":1,"statusText":"Active","isTrial":false,"applicationRegisterId":1,"accProductId":1,"storeId":1}}"""
                path.endsWith("/settings") -> """{"success":true,"data":{"allowOffline":true,"licenseCheckIntervalMinutes":60}}"""
                else -> """{"success":false,"error":1,"message":"unexpected"}"""
            }
        }
        val state = client.activate("K")
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Online>(valid.mode)
        assertEquals(state, client.state.value)
    }

    @Test
    fun activate_licenseNotFound_publishesInvalidNotFound() = runTest {
        val signer = TestTokenSigner.create()
        val client = buildClient(signer) { """{"success":false,"error":1,"message":"License not found"}""" }
        val state = client.activate("UNKNOWN")
        val invalid = assertIs<LicenseState.Invalid>(state)
        assertEquals(LicenseState.Invalid.Reason.NotFound, invalid.reason)
    }

    @Test
    fun activate_deviceLimitReached_publishesDeviceLimitReached() = runTest {
        val signer = TestTokenSigner.create()
        val client = buildClient(signer) { """{"success":false,"error":16,"message":"Device limit reached"}""" }
        assertIs<LicenseState.DeviceLimitReached>(client.activate("K"))
    }

    @Test
    fun restore_afterActivate_returnsValidCached() = runTest {
        val signer = TestTokenSigner.create()
        val id = DeviceIdHasher.hash("raw-fixed", "salt")
        val token = signer.sign(nowEpochSeconds = now, deviceId = id)
        val storage = InMemorySecureStorage()
        val client = buildClient(signer, storage) { path ->
            when {
                path.endsWith("/verify") -> """{"success":true,"data":{"isValid":true,"licenseKey":"K","licenseToken":"$token","status":1,"statusText":"Active","isTrial":false,"applicationRegisterId":1,"accProductId":1,"storeId":1}}"""
                path.endsWith("/settings") -> """{"success":true,"data":{"allowOffline":true,"licenseCheckIntervalMinutes":60}}"""
                else -> """{"success":false,"error":1,"message":"unexpected"}"""
            }
        }
        client.activate("K")

        val restored = LicenseClient(
            config = LicenseConfig("https://license.test", 1, "salt", mapOf(signer.kid to signer.publicKeyPem())),
            deviceIdProvider = FixedDeviceIdProvider("raw-fixed"),
            api = LicenseApiService(LicenseHttpClientFactory.create(MockEngine { jsonResponse("{}") }, LicenseConfig("https://license.test", 1, "salt", emptyMap())), LicenseConfig("https://license.test", 1, "salt", emptyMap())),
            verifier = LicenseTokenVerifier(mapOf(signer.kid to signer.publicKeyPem())),
            cache = LicenseCacheRepository(storage),
            clock = TestClock(now),
        )
        val state = restored.restore()
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Cached>(valid.mode)
    }
}

package com.dcorp.licensecore.api

import com.dcorp.licensecore.LicenseConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class LicenseApiServiceTest {
    private fun config() = LicenseConfig(
        baseUrl = "https://license.test",
        orgId = 1,
        appSalt = "salt",
        trustedSigningKeys = emptyMap(),
    )

    private fun serviceWith(engine: MockEngine) =
        LicenseApiService(LicenseHttpClientFactory.create(engine, config()), config())

    @Test
    fun verify_success_returnsData() = runTest {
        val engine = MockEngine { respond(
            content = """{"success":true,"data":{"isValid":true,"licenseKey":"K","licenseToken":"T","status":1,"statusText":"Active","isTrial":false,"applicationRegisterId":1,"accProductId":1,"storeId":1}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }
        val result = serviceWith(engine).verify("K", "device-1")
        val success = assertIs<LicenseApiOutcome.Success<VerifyLicenseResponseDto>>(result)
        assertEquals("K", success.data.licenseKey)
    }

    @Test
    fun verify_businessError_returnsBusinessError() = runTest {
        val engine = MockEngine { respond(
            content = """{"success":false,"error":14,"message":"License has expired"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }
        val result = serviceWith(engine).verify("K", "device-1")
        val error = assertIs<LicenseApiOutcome.BusinessError>(result)
        assertEquals(14, error.code)
    }

    @Test
    fun verify_serverErrorExhaustsRetries_returnsTransportError() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts += 1
            respond(content = "server error", status = HttpStatusCode.InternalServerError)
        }
        val result = serviceWith(engine).verify("K", "device-1")
        assertIs<LicenseApiOutcome.TransportError>(result)
        assertEquals(4, attempts) // 1 original + 3 retries
    }

    @Test
    fun introspect_success_parsesFlags() = runTest {
        val engine = MockEngine { respond(
            content = """{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }
        val result = serviceWith(engine).introspect("jwt")
        val success = assertIs<LicenseApiOutcome.Success<TokenIntrospectionDto>>(result)
        assertEquals(true, success.data.isValid)
    }

    @Test
    fun release_success_returnsUnit() = runTest {
        val engine = MockEngine { respond(
            content = """{"success":true}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }
        assertIs<LicenseApiOutcome.Success<Unit>>(serviceWith(engine).release(1, "device-1"))
    }

    @Test
    fun release_deviceNotFound_returnsBusinessError17() = runTest {
        val engine = MockEngine { respond(
            content = """{"success":false,"error":17,"message":"Device not found for this license"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }
        val error = assertIs<LicenseApiOutcome.BusinessError>(serviceWith(engine).release(1, "device-1"))
        assertEquals(17, error.code)
    }
}

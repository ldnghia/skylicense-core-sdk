package com.dcorp.licensecore.api

import com.dcorp.licensecore.LicenseConfig
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Phase 7, step 7: a transport-failure message must never echo the secret it was sent with -
 *  callers (`CApiExports.lc_last_error`, future app-side logging) may surface this text as-is. */
class LogRedactionTest {
    private val secretLicenseKey = "SECRET-LICENSE-KEY-SHOULD-NEVER-APPEAR-IN-LOGS"

    @Test
    fun transportErrorOnVerify_neverConstructsAMessageContainingTheLicenseKey() = runTest {
        val config = LicenseConfig("https://license.test", 1, "salt", emptyMap())
        // A generic underlying failure with no mention of the key at all - if LicenseApiService's
        // own error-message construction ever starts interpolating the request body (e.g.
        // "Failed to verify $licenseKey"), this regresses and the test catches it.
        val engine = MockEngine { throw RuntimeException("connection timed out") }
        val service = LicenseApiService(LicenseHttpClientFactory.create(engine, config), config)

        val outcome = service.verify(secretLicenseKey, "device-1")
        val error = assertIs<LicenseApiOutcome.TransportError>(outcome)

        assertFalse(error.message.contains(secretLicenseKey), "SDK must never build an error message containing the raw license key")
    }
}

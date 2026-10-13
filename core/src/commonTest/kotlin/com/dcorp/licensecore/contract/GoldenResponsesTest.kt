package com.dcorp.licensecore.contract

import com.dcorp.licensecore.api.ApiEnvelope
import com.dcorp.licensecore.api.ApiEnvelopeAck
import com.dcorp.licensecore.api.LicenseSettingDto
import com.dcorp.licensecore.api.TokenIntrospectionDto
import com.dcorp.licensecore.api.VerifyLicenseResponseDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves the SDK's DTOs actually parse the backend's real response shapes - see
 *  `GoldenResponses.kt` for where each JSON string comes from. */
class GoldenResponsesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun verifySuccess_parsesAllFields() {
        val envelope = json.decodeFromString<ApiEnvelope<VerifyLicenseResponseDto>>(GoldenResponses.VERIFY_SUCCESS)
        assertTrue(envelope.success)
        val data = envelope.data!!
        assertEquals("LC-TEST-0001", data.licenseKey)
        assertEquals(1, data.status)
        assertEquals("Active", data.statusText)
        assertEquals(3, data.maxDevice)
        assertFalse(data.isTrial)
        assertEquals(12, data.applicationRegisterId)
    }

    @Test
    fun verifyBusinessErrors_parseCodeAndMessage() {
        for (jsonStr in listOf(
            GoldenResponses.VERIFY_LICENSE_NOT_FOUND to 1,
            GoldenResponses.VERIFY_STATUS_INVALID to 13,
            GoldenResponses.VERIFY_EXPIRED to 14,
            GoldenResponses.VERIFY_DEVICE_LIMIT_REACHED to 16,
            GoldenResponses.FIND_NO_LICENSE_AVAILABLE to 22,
        )) {
            val (body, expectedCode) = jsonStr
            val envelope = json.decodeFromString<ApiEnvelope<VerifyLicenseResponseDto>>(body)
            assertFalse(envelope.success)
            assertEquals(expectedCode, envelope.error)
        }
    }

    @Test
    fun introspectResponses_parseFlagsCorrectly() {
        fun parse(body: String) = json.decodeFromString<ApiEnvelope<TokenIntrospectionDto>>(body).data!!

        val valid = parse(GoldenResponses.INTROSPECT_VALID)
        assertTrue(valid.isValid); assertFalse(valid.isRevoked); assertFalse(valid.isExpired)

        val revoked = parse(GoldenResponses.INTROSPECT_REVOKED)
        assertTrue(revoked.isRevoked)

        val badSig = parse(GoldenResponses.INTROSPECT_BAD_SIGNATURE)
        assertFalse(badSig.isValid)
    }

    @Test
    fun releaseResponses_parseAsAck() {
        val success = json.decodeFromString<ApiEnvelopeAck>(GoldenResponses.RELEASE_SUCCESS)
        assertTrue(success.success)

        val notFound = json.decodeFromString<ApiEnvelopeAck>(GoldenResponses.RELEASE_DEVICE_NOT_FOUND)
        assertFalse(notFound.success)
        assertEquals(17, notFound.error)
    }

    @Test
    fun settingsResponse_ignoresOverrideFlagFields() {
        val envelope = json.decodeFromString<ApiEnvelope<LicenseSettingDto>>(GoldenResponses.SETTINGS_SUCCESS)
        val data = envelope.data!!
        assertTrue(data.allowOffline)
        assertEquals(60, data.licenseCheckIntervalMinutes)
    }
}

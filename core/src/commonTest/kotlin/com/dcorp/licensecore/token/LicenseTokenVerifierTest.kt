package com.dcorp.licensecore.token

import com.dcorp.licensecore.testsupport.TestTokenSigner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class LicenseTokenVerifierTest {
    private val now = 1_700_000_000L

    private suspend fun verifierFor(signer: TestTokenSigner) =
        LicenseTokenVerifier(mapOf(signer.kid to signer.publicKeyPem()))

    @Test
    fun validTokenVerifies() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now, deviceId = "device-1")
        val result = verifierFor(signer).verify(jwt, "device-1", now)
        assertIs<LicenseTokenResult.Valid>(result)
        assertEquals("TEST-KEY", result.claims.licenseKey)
    }

    @Test
    fun tamperedPayloadIsInvalid() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now, deviceId = "device-1")
        val parts = jwt.split(".")
        val tampered = "${parts[0]}.${parts[1]}X.${parts[2]}"
        assertIs<LicenseTokenResult.Invalid>(verifierFor(signer).verify(tampered, "device-1", now))
    }

    @Test
    fun unknownKidIsInvalid() = runTest {
        val signer = TestTokenSigner.create(kid = "kid-a")
        val jwt = signer.sign(nowEpochSeconds = now)
        val verifier = LicenseTokenVerifier(mapOf("kid-b" to signer.publicKeyPem()))
        assertIs<LicenseTokenResult.Invalid>(verifier.verify(jwt, "device-hash", now))
    }

    @Test
    fun issuerMismatchIsInvalid() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(issuer = "someone-else", nowEpochSeconds = now, deviceId = "device-1")
        assertIs<LicenseTokenResult.Invalid>(verifierFor(signer).verify(jwt, "device-1", now))
    }

    @Test
    fun deviceIdMismatchIsInvalid() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now, deviceId = "device-1")
        assertIs<LicenseTokenResult.Invalid>(verifierFor(signer).verify(jwt, "device-2", now))
    }

    @Test
    fun expiredTokenIsExpiredNotInvalid() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now - 7200, lifetimeSeconds = 3600, deviceId = "device-1")
        assertIs<LicenseTokenResult.Expired>(verifierFor(signer).verify(jwt, "device-1", now))
    }

    @Test
    fun notBeforeInTheFutureIsInvalid() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now, notBeforeOverride = now + 3600, deviceId = "device-1")
        assertIs<LicenseTokenResult.Invalid>(verifierFor(signer).verify(jwt, "device-1", now))
    }

    @Test
    fun skewBoundaryAllowsSlightlyExpired() = runTest {
        val signer = TestTokenSigner.create()
        val jwt = signer.sign(nowEpochSeconds = now, expiresOverride = now - 10, deviceId = "device-1")
        assertIs<LicenseTokenResult.Valid>(verifierFor(signer).verify(jwt, "device-1", now))
    }

    @Test
    fun malformedTokenIsInvalid() = runTest {
        val verifier = LicenseTokenVerifier(emptyMap())
        assertIs<LicenseTokenResult.Invalid>(verifier.verify("not-a-jwt", "device-1", now))
    }
}

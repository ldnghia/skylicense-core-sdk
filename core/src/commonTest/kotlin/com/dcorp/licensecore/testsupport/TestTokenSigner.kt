package com.dcorp.licensecore.testsupport

import com.dcorp.licensecore.token.LicenseTokenHeader
import com.dcorp.licensecore.token.LicenseTokenPayload
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Generates a real P-256 keypair and signs JWTs shaped exactly like `TokenSigningService.cs` -
 *  the one thing a MockEngine fixture can't fake, since the verifier checks a real signature. */
@OptIn(ExperimentalEncodingApi::class)
public class TestTokenSigner private constructor(
    private val signFn: suspend (ByteArray) -> ByteArray,
    private val publicKeyPemFn: suspend () -> String,
    public val kid: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    public suspend fun publicKeyPem(): String = publicKeyPemFn()

    public suspend fun sign(
        issuer: String = "skyapp-license-engine",
        nowEpochSeconds: Long,
        lifetimeSeconds: Long = 3600,
        jti: String = "test-jti",
        licenseId: Int = 1,
        licenseKey: String = "TEST-KEY",
        deviceId: String = "device-hash",
        status: String = "1",
        notBeforeOverride: Long? = null,
        expiresOverride: Long? = null,
        kidOverride: String? = null,
    ): String {
        val header = LicenseTokenHeader(alg = "ES256", kid = kidOverride ?: kid)
        val payload = LicenseTokenPayload(
            iss = issuer,
            nbf = notBeforeOverride ?: nowEpochSeconds,
            exp = expiresOverride ?: (nowEpochSeconds + lifetimeSeconds),
            jti = jti,
            lid = licenseId.toString(),
            lk = licenseKey,
            did = deviceId,
            status = status,
        )
        val headerSegment = encodeSegment(json.encodeToString(header))
        val payloadSegment = encodeSegment(json.encodeToString(payload))
        val signingInput = "$headerSegment.$payloadSegment".encodeToByteArray()

        val signature = signFn(signingInput)
        val signatureSegment = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(signature)

        return "$headerSegment.$payloadSegment.$signatureSegment"
    }

    private fun encodeSegment(text: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(text.encodeToByteArray())

    public companion object {
        public suspend fun create(kid: String = "test-kid"): TestTokenSigner {
            val ecdsa = CryptographyProvider.Default.get(ECDSA)
            val keyPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
            val signFn: suspend (ByteArray) -> ByteArray = { input ->
                keyPair.privateKey.signatureGenerator(digest = SHA256, format = ECDSA.SignatureFormat.RAW).generateSignature(input)
            }
            val publicKeyPemFn: suspend () -> String = {
                keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.PEM).decodeToString()
            }
            return TestTokenSigner(signFn, publicKeyPemFn, kid)
        }
    }
}

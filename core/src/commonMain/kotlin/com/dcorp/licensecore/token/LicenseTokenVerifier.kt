package com.dcorp.licensecore.token

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public sealed class LicenseTokenResult {
    public data class Valid(val claims: LicenseTokenClaims) : LicenseTokenResult()
    public data class Expired(val claims: LicenseTokenClaims) : LicenseTokenResult()
    public data object Invalid : LicenseTokenResult()
}

/**
 * Offline (no network) JWT ES256 verification against embedded trusted public keys - there is no
 * key-discovery endpoint (`TokenSigningService.cs:135-156`), so unknown `kid` is always untrusted.
 * `TRUSTED_ISSUER` and the 30s clock skew mirror the backend's own validation
 * (`TokenSigningService.cs:113,117`).
 */
public class LicenseTokenVerifier(private val trustedSigningKeys: Map<String, String>) {
    private companion object {
        const val TRUSTED_ISSUER = "skyapp-license-engine"
        const val SKEW_SECONDS = 30L
    }

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    public suspend fun verify(jwt: String, expectedDeviceId: String, nowEpochSeconds: Long): LicenseTokenResult {
        val parts = jwt.split(".")
        if (parts.size != 3) return LicenseTokenResult.Invalid

        val header = runCatching { json.decodeFromString<LicenseTokenHeader>(decodeSegment(parts[0])) }.getOrNull()
            ?: return LicenseTokenResult.Invalid
        val kid = header.kid ?: return LicenseTokenResult.Invalid
        val publicKeyPem = trustedSigningKeys[kid] ?: return LicenseTokenResult.Invalid

        val payload = runCatching { json.decodeFromString<LicenseTokenPayload>(decodeSegment(parts[1])) }.getOrNull()
            ?: return LicenseTokenResult.Invalid

        val signature = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(parts[2]) }.getOrNull()
            ?: return LicenseTokenResult.Invalid
        if (signature.size != 64) return LicenseTokenResult.Invalid

        val signingInput = "${parts[0]}.${parts[1]}".encodeToByteArray()
        val signatureValid = verifySignature(publicKeyPem, signingInput, signature)
        if (!signatureValid) return LicenseTokenResult.Invalid

        if (payload.iss != TRUSTED_ISSUER) return LicenseTokenResult.Invalid
        if (payload.did != expectedDeviceId) return LicenseTokenResult.Invalid
        val licenseId = payload.lid.toIntOrNull() ?: return LicenseTokenResult.Invalid
        if (nowEpochSeconds < payload.nbf - SKEW_SECONDS) return LicenseTokenResult.Invalid

        val claims = LicenseTokenClaims(
            issuer = payload.iss,
            notBeforeEpochSeconds = payload.nbf,
            expiresEpochSeconds = payload.exp,
            jti = payload.jti,
            licenseId = licenseId,
            licenseKey = payload.lk,
            deviceId = payload.did,
            status = payload.status,
        )

        return if (nowEpochSeconds > payload.exp + SKEW_SECONDS) {
            LicenseTokenResult.Expired(claims)
        } else {
            LicenseTokenResult.Valid(claims)
        }
    }

    private suspend fun verifySignature(publicKeyPem: String, signingInput: ByteArray, signature: ByteArray): Boolean {
        return runCatching {
            val ecdsa = CryptographyProvider.Default.get(ECDSA)
            val publicKey = ecdsa.publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.PEM, publicKeyPem.encodeToByteArray())
            publicKey.signatureVerifier(digest = SHA256, format = ECDSA.SignatureFormat.RAW)
                .tryVerifySignature(signingInput, signature)
        }.getOrDefault(false)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeSegment(segment: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(segment).decodeToString()
}

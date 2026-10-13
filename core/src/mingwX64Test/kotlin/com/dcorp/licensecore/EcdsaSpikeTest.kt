package com.dcorp.licensecore

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class EcdsaSpikeTest {
    @Test
    fun ecdsaP256RawSignatureRoundTripsOnMingwX64() = runTest {
        val ecdsa = CryptographyProvider.Default.get(ECDSA)
        val keyPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()

        val message = "licensecore-spike".encodeToByteArray()
        val signature = keyPair.privateKey
            .signatureGenerator(digest = SHA256, format = ECDSA.SignatureFormat.RAW)
            .generateSignature(message)

        assertTrue(signature.size == 64, "RAW P-256 signature should be 64 bytes (R||S), was ${signature.size}")

        val isValid = keyPair.publicKey
            .signatureVerifier(digest = SHA256, format = ECDSA.SignatureFormat.RAW)
            .tryVerifySignature(message, signature)
        assertTrue(isValid, "valid signature must verify")

        val tampered = signature.copyOf()
        tampered[0] = (tampered[0].toInt() xor 1).toByte()
        val isTamperedValid = keyPair.publicKey
            .signatureVerifier(digest = SHA256, format = ECDSA.SignatureFormat.RAW)
            .tryVerifySignature(message, tampered)
        assertTrue(!isTamperedValid, "tampered signature must not verify")
    }
}

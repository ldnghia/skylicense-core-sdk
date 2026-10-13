package com.dcorp.licensecore

/**
 * SDK configuration. [trustedSigningKeys] maps a token's `kid` header to that signing key's
 * public key in PEM (SubjectPublicKeyInfo) form - there is no public-key discovery endpoint on
 * the backend, so these must be shipped with the app and updated on key rotation.
 */
public class LicenseConfig(
    public val baseUrl: String,
    public val orgId: Int,
    public val appSalt: String,
    public val trustedSigningKeys: Map<String, String>,
    public val connectTimeoutMillis: Long = 10_000,
    public val requestTimeoutMillis: Long = 15_000,
    public val certificatePins: Map<String, List<String>> = emptyMap(),
) {
    public val normalizedBaseUrl: String = baseUrl.trimEnd('/')
}

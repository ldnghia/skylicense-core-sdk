package com.dcorp.licensecore.token

import kotlinx.serialization.Serializable

/** JWT header, just enough to pick the right trusted key (`TokenSigningService.cs:54`). */
@Serializable
internal data class LicenseTokenHeader(val alg: String, val kid: String? = null)

/** JWT payload as written by `TokenSigningService.cs:61-77` - custom claims plus the standard
 *  `iss`/`nbf`/`exp` (epoch seconds, unambiguous - unlike `expDate`, which can lack a UTC offset). */
@Serializable
internal data class LicenseTokenPayload(
    val iss: String,
    val nbf: Long,
    val exp: Long,
    val jti: String,
    val lid: String,
    val lk: String,
    val did: String,
    val status: String,
    @kotlinx.serialization.SerialName("exp_date") val expDate: String? = null,
    @kotlinx.serialization.SerialName("max_device") val maxDevice: String? = null,
)

/** Parsed, signature-verified token claims - the only form the rest of the SDK should see. */
public data class LicenseTokenClaims(
    public val issuer: String,
    public val notBeforeEpochSeconds: Long,
    public val expiresEpochSeconds: Long,
    public val jti: String,
    public val licenseId: Int,
    public val licenseKey: String,
    public val deviceId: String,
    public val status: String,
)

package com.dcorp.licensecore.api

import kotlinx.serialization.Serializable

/** Mirrors `Libraries/Core/ApiResponse.cs` - every endpoint returns HTTP 200 and signals business
 *  errors through [success]/[error], never through the HTTP status code. */
@Serializable
public data class ApiEnvelope<T>(
    val success: Boolean,
    val error: Int? = null,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
public data class VerifyLicenseRequestDto(val licenseKey: String, val deviceId: String)

@Serializable
public data class FindLicenseRequestDto(
    val orgId: Int,
    val appId: Int? = null,
    val accProductId: Int? = null,
    val storeId: Int,
    val deviceId: String,
)

@Serializable
public data class ReleaseDeviceRequestDto(val deviceId: String)

@Serializable
public data class IntrospectTokenRequestDto(val licenseToken: String)

@Serializable
public data class VerifyLicenseResponseDto(
    val isValid: Boolean,
    val licenseKey: String,
    val licenseToken: String,
    val status: Int,
    val statusText: String,
    val activationDate: String? = null,
    val expiryDate: String? = null,
    val daysRemaining: Int? = null,
    val maxDevice: Int? = null,
    val isTrial: Boolean,
    val applicationRegisterId: Int,
    val accProductId: Int,
    val storeId: Int,
)

@Serializable
public data class TokenIntrospectionDto(val isValid: Boolean, val isRevoked: Boolean, val isExpired: Boolean)

@Serializable
public data class LicenseSettingDto(val allowOffline: Boolean, val licenseCheckIntervalMinutes: Int)

/** For endpoints whose `data` payload is irrelevant to the SDK (e.g. release) - `data` is simply
 *  not declared, so `ignoreUnknownKeys` drops it regardless of its shape. */
@Serializable
public data class ApiEnvelopeAck(val success: Boolean, val error: Int? = null, val message: String? = null)

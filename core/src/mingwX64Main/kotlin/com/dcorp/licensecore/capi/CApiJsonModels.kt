package com.dcorp.licensecore.capi

import com.dcorp.licensecore.LicenseInfo
import com.dcorp.licensecore.LicenseState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CApiConfigDto(
    val baseUrl: String,
    val orgId: Int,
    val appSalt: String,
    val trustedSigningKeys: Map<String, String> = emptyMap(),
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 15_000,
)

@Serializable
internal data class CApiActivateForStoreRequestDto(val storeId: Int, val appId: Int? = null, val accProductId: Int? = null)

@Serializable
internal data class CApiErrorDto(val code: String, val message: String)

@Serializable
internal data class CApiResponseDto(
    val state: String,
    val info: LicenseInfo? = null,
    val offlineGraceUntilEpochSeconds: Long? = null,
    val error: CApiErrorDto? = null,
)

internal val capiJson = Json { encodeDefaults = false; ignoreUnknownKeys = true }

internal fun LicenseState.toCApiJson(): String = capiJson.encodeToString(toDto())

internal fun errorCApiJson(code: String, message: String): String =
    capiJson.encodeToString(CApiResponseDto(state = "ERROR", error = CApiErrorDto(code, message)))

private fun LicenseState.toDto(): CApiResponseDto = when (this) {
    is LicenseState.Unlicensed -> CApiResponseDto(state = "UNLICENSED")
    is LicenseState.Valid -> when (val mode = mode) {
        is LicenseState.Valid.Mode.Online -> CApiResponseDto(state = "VALID", info = info)
        is LicenseState.Valid.Mode.Cached -> CApiResponseDto(state = "VALID_CACHED", info = info)
        is LicenseState.Valid.Mode.Offline -> CApiResponseDto(
            state = "OFFLINE",
            info = info,
            offlineGraceUntilEpochSeconds = mode.graceUntilEpochSeconds,
        )
    }
    is LicenseState.Revoked -> CApiResponseDto(state = "REVOKED")
    is LicenseState.Expired -> CApiResponseDto(state = "EXPIRED")
    is LicenseState.DeviceLimitReached -> CApiResponseDto(state = "DEVICE_LIMIT_REACHED")
    is LicenseState.Invalid -> CApiResponseDto(
        state = "INVALID",
        error = CApiErrorDto(reason.name.uppercase(), detail ?: reason.name),
    )
    is LicenseState.NetworkRequired -> CApiResponseDto(state = "NETWORK_REQUIRED")
}

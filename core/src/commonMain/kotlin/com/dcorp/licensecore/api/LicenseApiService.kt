package com.dcorp.licensecore.api

import com.dcorp.licensecore.LicenseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Thin wrapper over the client-relevant subset of the `api/licenses` routes
 * (`LicenseController.cs:11,98,106,122,130`, `LicenseSettingController.cs:11`). Deliberately does
 * NOT wrap `/{id}/devices` (admin-mediated bind) or `/token/revoke` (release already revokes -
 * `LicenseApiService.cs:955`) - see Phase 2 plan, Key Insight 8.
 */
internal class LicenseApiService(private val http: HttpClient, private val config: LicenseConfig) {

    suspend fun verify(licenseKey: String, deviceId: String): LicenseApiOutcome<VerifyLicenseResponseDto> =
        post("verify", VerifyLicenseRequestDto(licenseKey, deviceId))

    suspend fun find(appId: Int?, accProductId: Int?, storeId: Int, deviceId: String): LicenseApiOutcome<VerifyLicenseResponseDto> =
        post("find", FindLicenseRequestDto(config.orgId, appId, accProductId, storeId, deviceId))

    suspend fun release(licenseId: Int, deviceId: String): LicenseApiOutcome<Unit> = transportSafe {
        val ack: ApiEnvelopeAck = http.post("${config.normalizedBaseUrl}/api/licenses/$licenseId/devices/release") {
            contentType(ContentType.Application.Json)
            setBody(ReleaseDeviceRequestDto(deviceId))
        }.body()
        if (ack.success) LicenseApiOutcome.Success(Unit) else LicenseApiOutcome.BusinessError(ack.error ?: -1, ack.message ?: "unknown error")
    }

    suspend fun introspect(licenseToken: String): LicenseApiOutcome<TokenIntrospectionDto> =
        post("token/introspect", IntrospectTokenRequestDto(licenseToken))

    suspend fun getSettings(storeId: Int): LicenseApiOutcome<LicenseSettingDto> = transportSafe {
        val envelope: ApiEnvelope<LicenseSettingDto> = http.get("${config.normalizedBaseUrl}/api/licenses/settings") {
            parameter("orgId", config.orgId)
            parameter("storeId", storeId)
        }.body()
        toOutcome(envelope)
    }

    private suspend inline fun <reified B, reified T> post(path: String, body: B): LicenseApiOutcome<T> = transportSafe {
        val envelope: ApiEnvelope<T> = http.post("${config.normalizedBaseUrl}/api/licenses/$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        toOutcome(envelope)
    }

    private fun <T> toOutcome(envelope: ApiEnvelope<T>): LicenseApiOutcome<T> {
        if (!envelope.success) {
            return LicenseApiOutcome.BusinessError(envelope.error ?: -1, envelope.message ?: "unknown error")
        }
        @Suppress("UNCHECKED_CAST")
        return LicenseApiOutcome.Success(envelope.data as T)
    }

    private suspend inline fun <T> transportSafe(block: suspend () -> LicenseApiOutcome<T>): LicenseApiOutcome<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LicenseApiOutcome.TransportError(e.message ?: "network error")
        }
    }
}

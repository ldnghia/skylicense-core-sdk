package com.dcorp.licensecore.internal

import com.dcorp.licensecore.LicenseClock
import com.dcorp.licensecore.LicenseInfo
import com.dcorp.licensecore.LicenseState
import com.dcorp.licensecore.api.LicenseApiOutcome
import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.VerifyLicenseResponseDto
import com.dcorp.licensecore.cache.CachedLicenseRecord
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.mapBusinessError
import com.dcorp.licensecore.clearsCache
import com.dcorp.licensecore.token.LicenseTokenResult
import com.dcorp.licensecore.token.LicenseTokenVerifier

/** Shared by `activate`, `activateForStore` and the recheck-triggered refresh - all three end in
 *  a server-issued [VerifyLicenseResponseDto] that needs the same verify-then-persist handling. */
internal class LicenseVerifyHandler(
    private val api: LicenseApiService,
    private val verifier: LicenseTokenVerifier,
    private val cache: LicenseCacheRepository,
    private val clock: LicenseClock,
) {
    suspend fun handle(outcome: LicenseApiOutcome<VerifyLicenseResponseDto>, deviceId: String, isRefresh: Boolean): LicenseState {
        return when (outcome) {
            is LicenseApiOutcome.Success -> handleSuccess(outcome.data, deviceId)
            is LicenseApiOutcome.BusinessError -> {
                val state = mapBusinessError(outcome.code, outcome.message, isRefresh)
                if (state.clearsCache()) cache.clear()
                state
            }
            is LicenseApiOutcome.TransportError -> LicenseState.NetworkRequired(outcome.message)
        }
    }

    private suspend fun handleSuccess(dto: VerifyLicenseResponseDto, deviceId: String): LicenseState {
        return when (val result = verifier.verify(dto.licenseToken, deviceId, clock.nowEpochSeconds())) {
            is LicenseTokenResult.Valid -> {
                val info = LicenseInfo(dto.licenseKey, dto.statusText, dto.activationDate, dto.expiryDate, dto.daysRemaining, dto.maxDevice, dto.isTrial)
                val (allowOffline, intervalMinutes) = loadSettings(dto.storeId)
                val now = clock.nowEpochSeconds()
                cache.save(
                    CachedLicenseRecord(
                        schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                        token = dto.licenseToken,
                        info = info,
                        allowOffline = allowOffline,
                        checkIntervalMinutes = intervalMinutes,
                        lastOnlineCheckEpochSeconds = now,
                        maxObservedEpochSeconds = now,
                    ),
                )
                LicenseState.Valid(info, LicenseState.Valid.Mode.Online)
            }
            is LicenseTokenResult.Expired -> {
                cache.clear()
                LicenseState.Expired
            }
            LicenseTokenResult.Invalid -> {
                cache.clear()
                LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted)
            }
        }
    }

    private suspend fun loadSettings(storeId: Int): Pair<Boolean, Int> {
        val outcome = api.getSettings(storeId)
        return if (outcome is LicenseApiOutcome.Success) {
            outcome.data.allowOffline to outcome.data.licenseCheckIntervalMinutes
        } else {
            // Backend defaults (`LicenseSettings.cs:15,21`) - verify already succeeded, don't fail
            // activation just because the follow-up settings fetch did.
            true to 60
        }
    }
}

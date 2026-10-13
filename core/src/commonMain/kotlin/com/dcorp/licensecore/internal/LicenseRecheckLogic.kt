package com.dcorp.licensecore.internal

import com.dcorp.licensecore.LicenseClock
import com.dcorp.licensecore.LicenseState
import com.dcorp.licensecore.api.LicenseApiOutcome
import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.TokenIntrospectionDto
import com.dcorp.licensecore.cache.CachedLicenseRecord
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.clearsCache
import com.dcorp.licensecore.mapBusinessError
import com.dcorp.licensecore.policy.ClockRollbackGuard
import com.dcorp.licensecore.policy.OfflineGracePolicy
import com.dcorp.licensecore.token.LicenseTokenClaims
import com.dcorp.licensecore.token.LicenseTokenResult
import com.dcorp.licensecore.token.LicenseTokenVerifier

/**
 * The periodic re-check: introspect first (so a revoked device stops immediately -
 * `LicenseApiService.cs:955` vs `:187-196`), then either keep the cached token, refresh it, or
 * fall back to bounded offline grace.
 */
internal class LicenseRecheckLogic(
    private val api: LicenseApiService,
    private val verifier: LicenseTokenVerifier,
    private val cache: LicenseCacheRepository,
    private val clock: LicenseClock,
    private val verifyHandler: LicenseVerifyHandler,
) {
    suspend fun recheck(record: CachedLicenseRecord, deviceId: String): LicenseState {
        val now = clock.nowEpochSeconds()
        val tampered = ClockRollbackGuard.isTampered(now, record.maxObservedEpochSeconds)
        val nextMaxObserved = ClockRollbackGuard.nextMaxObserved(now, record.maxObservedEpochSeconds)

        val localResult = verifier.verify(record.token, deviceId, now)
        val claims = when (localResult) {
            is LicenseTokenResult.Valid -> localResult.claims
            is LicenseTokenResult.Expired -> localResult.claims
            LicenseTokenResult.Invalid -> {
                cache.clear()
                return LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted)
            }
        }

        return when (val outcome = api.introspect(record.token)) {
            is LicenseApiOutcome.Success -> handleIntrospectSuccess(outcome.data, record, claims, deviceId, now, nextMaxObserved)
            is LicenseApiOutcome.BusinessError -> {
                val state = mapBusinessError(outcome.code, outcome.message, isRefresh = true)
                if (state.clearsCache()) cache.clear()
                state
            }
            is LicenseApiOutcome.TransportError -> handleOffline(record, claims, tampered, now, nextMaxObserved)
        }
    }

    private suspend fun handleIntrospectSuccess(
        introspection: TokenIntrospectionDto,
        record: CachedLicenseRecord,
        claims: LicenseTokenClaims,
        deviceId: String,
        now: Long,
        nextMaxObserved: Long,
    ): LicenseState {
        if (introspection.isRevoked) {
            cache.clear()
            return LicenseState.Revoked
        }
        if (!introspection.isValid && !introspection.isExpired) {
            cache.clear()
            return LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted)
        }

        val remainingSeconds = claims.expiresEpochSeconds - now
        val needsRefresh = introspection.isExpired || remainingSeconds < record.checkIntervalMinutes * 60L
        if (needsRefresh) {
            val outcome = api.verify(claims.licenseKey, deviceId)
            return verifyHandler.handle(outcome, deviceId, isRefresh = true)
        }

        cache.save(record.copy(lastOnlineCheckEpochSeconds = now, maxObservedEpochSeconds = nextMaxObserved))
        return LicenseState.Valid(record.info, LicenseState.Valid.Mode.Online)
    }

    private suspend fun handleOffline(
        record: CachedLicenseRecord,
        claims: LicenseTokenClaims,
        tampered: Boolean,
        now: Long,
        nextMaxObserved: Long,
    ): LicenseState {
        if (tampered) return LicenseState.NetworkRequired

        return when (val decision = OfflineGracePolicy.decide(record.allowOffline, now, claims.expiresEpochSeconds)) {
            is OfflineGracePolicy.Decision.RemainOffline -> {
                cache.save(record.copy(maxObservedEpochSeconds = nextMaxObserved))
                LicenseState.Valid(record.info, LicenseState.Valid.Mode.Offline(decision.graceUntilEpochSeconds))
            }
            OfflineGracePolicy.Decision.RequireOnline -> LicenseState.NetworkRequired
        }
    }
}

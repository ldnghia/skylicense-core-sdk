package com.dcorp.licensecore

import com.dcorp.licensecore.api.LicenseApiOutcome
import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.cache.CachedLicenseRecord
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.device.DeviceIdHasher
import com.dcorp.licensecore.internal.LicenseRecheckLogic
import com.dcorp.licensecore.internal.LicenseVerifyHandler
import com.dcorp.licensecore.platform.DeviceAboutProvider
import com.dcorp.licensecore.platform.DeviceIdProvider
import com.dcorp.licensecore.scheduler.LicenseRecheckScheduler
import com.dcorp.licensecore.token.LicenseTokenResult
import com.dcorp.licensecore.token.LicenseTokenVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Public facade. Activation, periodic re-check and deactivation are serialized through [mutex]
 * so they never interleave (e.g. a background recheck racing a user-initiated deactivate).
 */
public class LicenseClient internal constructor(
    private val config: LicenseConfig,
    private val deviceIdProvider: DeviceIdProvider,
    private val deviceAboutProvider: DeviceAboutProvider,
    private val api: LicenseApiService,
    private val verifier: LicenseTokenVerifier,
    private val cache: LicenseCacheRepository,
    private val clock: LicenseClock = SystemLicenseClock,
) {
    private val mutex = Mutex()
    private val verifyHandler = LicenseVerifyHandler(api, verifier, cache, clock)
    private val recheckLogic = LicenseRecheckLogic(api, verifier, cache, clock, verifyHandler, deviceAboutProvider)
    private val scheduler = LicenseRecheckScheduler(
        intervalMinutes = { currentIntervalMinutes },
        isNetworkRequired = { _state.value is LicenseState.NetworkRequired },
        onTick = { checkNow() },
    )

    private var currentIntervalMinutes: Int = 60
    private val _state = MutableStateFlow<LicenseState>(LicenseState.Unlicensed)

    /** Current license state; starts at [LicenseState.Unlicensed] until [restore] or [activate]
     *  publishes something else. */
    public val state: StateFlow<LicenseState> get() = _state
    private val listeners = mutableListOf<LicenseStateListener>()

    /** Hashed device id (with the app salt) sent to the backend and embedded for admin-mediated
     *  offline activation - never the raw platform identifier. */
    public suspend fun deviceId(): String = DeviceIdHasher.hash(deviceIdProvider.rawDeviceId(), config.appSalt)

    /** Activates by license key: calls `verify` (which auto-binds this device), verifies the
     *  returned token locally, and persists it on success. [deviceName] is an optional
     *  caller-chosen label (e.g. "Quầy thu ngân 1") the SDK never fills in on its own - purely for
     *  an admin to recognize this device later; a technical `deviceAbout` (OS, model...) is always
     *  collected automatically alongside it. */
    public suspend fun activate(licenseKey: String, deviceName: String? = null): LicenseState = mutex.withLock {
        val id = deviceId()
        val state = publish(verifyHandler.handle(api.verify(licenseKey, id, deviceName, deviceAboutProvider.deviceAbout()), id, isRefresh = false))
        syncIntervalFromCache()
        state
    }

    /** Activates without a known license key: calls `find`, which claims a free slot on the
     *  soonest-expiring eligible license for this store/app/product. See [activate] for
     *  [deviceName]'s meaning. */
    public suspend fun activateForStore(storeId: Int, appId: Int? = null, accProductId: Int? = null, deviceName: String? = null): LicenseState = mutex.withLock {
        val id = deviceId()
        val state = publish(verifyHandler.handle(api.find(appId, accProductId, storeId, id, deviceName, deviceAboutProvider.deviceAbout()), id, isRefresh = false))
        syncIntervalFromCache()
        state
    }

    /** Imports a token issued out-of-band (admin-mediated offline activation) without calling the
     *  backend - the token's own signature and `did` claim are the only trust anchor. */
    public suspend fun importOfflineToken(jwt: String): LicenseState = mutex.withLock {
        val id = deviceId()
        when (val result = verifier.verify(jwt, id, clock.nowEpochSeconds())) {
            is LicenseTokenResult.Valid -> {
                val now = clock.nowEpochSeconds()
                val info = LicenseInfo(result.claims.licenseKey, result.claims.status, null, null, null, null, false)
                cache.save(
                    CachedLicenseRecord(
                        schemaVersion = CachedLicenseRecord.CURRENT_SCHEMA_VERSION,
                        token = jwt,
                        info = info,
                        allowOffline = true,
                        checkIntervalMinutes = currentIntervalMinutes,
                        lastOnlineCheckEpochSeconds = now,
                        maxObservedEpochSeconds = now,
                    ),
                )
                publish(LicenseState.Valid(info, LicenseState.Valid.Mode.Cached))
            }
            is LicenseTokenResult.Expired -> publish(LicenseState.Expired)
            LicenseTokenResult.Invalid ->
                publish(LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted))
        }
    }

    /** Loads the cached token (no network call) and republishes its state - call once at
     *  startup, before [start], to restore last-known state immediately. */
    public suspend fun restore(): LicenseState = mutex.withLock {
        val record = cache.load() ?: return@withLock publish(LicenseState.Unlicensed)
        currentIntervalMinutes = record.checkIntervalMinutes
        val id = deviceId()
        when (val result = verifier.verify(record.token, id, clock.nowEpochSeconds())) {
            is LicenseTokenResult.Valid ->
                publish(LicenseState.Valid(record.info, LicenseState.Valid.Mode.Cached))
            is LicenseTokenResult.Expired -> {
                cache.clear()
                publish(LicenseState.Expired)
            }
            LicenseTokenResult.Invalid -> {
                cache.clear()
                publish(LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted))
            }
        }
    }

    /** Forces an immediate re-check instead of waiting for the scheduler's next tick - also what
     *  [start] calls on each interval. */
    public suspend fun checkNow(): LicenseState = mutex.withLock {
        val record = cache.load() ?: return@withLock publish(LicenseState.Unlicensed)
        currentIntervalMinutes = record.checkIntervalMinutes
        val state = publish(recheckLogic.recheck(record, deviceId()))
        // recheck() may have refreshed the token with a new checkIntervalMinutes (settings changed
        // server-side) - re-sync so the scheduler's *next* tick uses it immediately instead of
        // waiting one more full interval for the staleness to self-correct.
        syncIntervalFromCache()
        state
    }

    /** Releases this device's slot and clears the cache. A transport failure leaves the current
     *  state and cache untouched so the caller can retry. */
    public suspend fun deactivate(): LicenseState = mutex.withLock {
        val record = cache.load() ?: return@withLock publish(LicenseState.Unlicensed)
        val id = deviceId()
        val claims = when (val result = verifier.verify(record.token, id, clock.nowEpochSeconds())) {
            is LicenseTokenResult.Valid -> result.claims
            is LicenseTokenResult.Expired -> result.claims
            LicenseTokenResult.Invalid -> null
        } ?: return@withLock run {
            cache.clear()
            scheduler.stop()
            publish(LicenseState.Unlicensed)
        }

        when (val outcome = api.release(claims.licenseId, id)) {
            is LicenseApiOutcome.Success -> {
                cache.clear()
                scheduler.stop()
                publish(LicenseState.Unlicensed)
            }
            is LicenseApiOutcome.BusinessError ->
                if (outcome.code == 17) {
                    // DEVICE_NOT_FOUND - already released elsewhere; treat as success.
                    cache.clear()
                    scheduler.stop()
                    publish(LicenseState.Unlicensed)
                } else {
                    publish(mapBusinessError(outcome.code, outcome.message, isRefresh = false))
                }
            is LicenseApiOutcome.TransportError -> _state.value // unchanged; caller may retry
        }
    }

    /** Starts the periodic re-check loop on [scope]; a no-op if already running. */
    public fun start(scope: CoroutineScope) {
        scheduler.start(scope)
    }

    /** Stops the periodic re-check loop; safe to call even if it was never started. */
    public fun stop() {
        scheduler.stop()
    }

    /** Registers [listener] for every future state change; call [Closeable.close] on the
     *  returned handle to unregister. Prefer [state] where a `StateFlow` is convenient to use. */
    public fun addStateListener(listener: LicenseStateListener): Closeable {
        listeners.add(listener)
        return Closeable { listeners.remove(listener) }
    }

    private fun publish(state: LicenseState): LicenseState {
        _state.value = state
        listeners.forEach { it.onStateChanged(state) }
        return state
    }

    /** Re-reads [currentIntervalMinutes] from the just-saved cache record - without this, a fresh
     *  [activate]/[activateForStore] leaves the scheduler on its [currentIntervalMinutes] default
     *  (60) until the next [restore] or [checkNow], ignoring whatever checkIntervalMinutes the
     *  server just returned for up to a full default interval. */
    private suspend fun syncIntervalFromCache() {
        cache.load()?.let { currentIntervalMinutes = it.checkIntervalMinutes }
    }
}

package com.dcorp.licensecore.cache

import com.dcorp.licensecore.LicenseInfo
import kotlinx.serialization.Serializable

/** Persisted across restarts. `schemaVersion` lets a future format change discard old records
 *  instead of crashing - the server is always the source of truth, so losing the cache just means
 *  one extra online re-verify. */
@Serializable
public data class CachedLicenseRecord(
    val schemaVersion: Int,
    val token: String,
    val info: LicenseInfo,
    val allowOffline: Boolean,
    val checkIntervalMinutes: Int,
    val lastOnlineCheckEpochSeconds: Long,
    val maxObservedEpochSeconds: Long,
) {
    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

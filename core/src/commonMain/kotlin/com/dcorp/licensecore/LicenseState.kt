package com.dcorp.licensecore

import kotlinx.serialization.Serializable

/**
 * Current license state as observed by this device. Mirrors the sealed-result convention used
 * throughout the public API: no exception ever crosses the boundary, callers switch on this type.
 */
public sealed class LicenseState {
    /** No activation attempted yet, or the last one failed before producing another state. */
    public data object Unlicensed : LicenseState()

    /** License confirmed usable right now; [mode] says how recently/how that was confirmed. */
    public data class Valid(
        public val info: LicenseInfo,
        public val mode: Mode,
    ) : LicenseState() {
        public sealed class Mode {
            /** Confirmed by a successful call just now. */
            public data object Online : Mode()
            /** Confirmed by loading the cache at startup, before any network call this session. */
            public data object Cached : Mode()
            /** Server unreachable; valid only until [graceUntilEpochSeconds] (the cached token's own `exp`). */
            public data class Offline(public val graceUntilEpochSeconds: Long) : Mode()
        }
    }

    /** This device's token was revoked server-side (e.g. released from another device/admin). */
    public data object Revoked : LicenseState()
    /** The license itself (not just the token) has expired. */
    public data object Expired : LicenseState()
    /** No free device slot was available on `activate`. */
    public data object DeviceLimitReached : LicenseState()

    /** Backend rejected the request or the token failed local verification; see [reason]. */
    public data class Invalid(public val reason: Reason, public val detail: String? = null) : LicenseState() {
        public enum class Reason { NotFound, Inactive, TokenUntrusted, InvalidArgument, NoLicenseAvailable, Unknown }
    }

    /** Online check is required to make progress (e.g. offline grace exhausted). */
    public data object NetworkRequired : LicenseState()
}

/** Opaque, display-only license fields copied verbatim from the backend's verify/find response. */
@Serializable
public data class LicenseInfo(
    public val licenseKey: String,
    public val statusText: String,
    public val activationDate: String?,
    public val expiryDate: String?,
    public val daysRemaining: Int?,
    public val maxDevice: Int?,
    public val isTrial: Boolean,
)

package com.dcorp.licensecore

/** Maps backend error codes (`LicenseDefaults.cs:15-65`) to [LicenseState]. [isRefresh] matters
 *  only for code 16: on a fresh `activate`, device-limit means no slot; on a periodic refresh of
 *  an already-bound device, it means the device was released elsewhere since the last check. */
internal fun mapBusinessError(code: Int, message: String, isRefresh: Boolean): LicenseState = when (code) {
    1 -> LicenseState.Invalid(LicenseState.Invalid.Reason.NotFound, message)
    12, 15, 18, 19, 20 -> LicenseState.Invalid(LicenseState.Invalid.Reason.InvalidArgument, message)
    13 -> LicenseState.Invalid(LicenseState.Invalid.Reason.Inactive, message)
    14 -> LicenseState.Expired
    16 -> if (isRefresh) LicenseState.Revoked else LicenseState.DeviceLimitReached
    21 -> LicenseState.Invalid(LicenseState.Invalid.Reason.InvalidArgument, message)
    22 -> LicenseState.Invalid(LicenseState.Invalid.Reason.NoLicenseAvailable, message)
    23, 24 -> LicenseState.Invalid(LicenseState.Invalid.Reason.TokenUntrusted, message)
    else -> LicenseState.Invalid(LicenseState.Invalid.Reason.Unknown, message)
}

/** Cache should be cleared for these states - everything else keeps whatever was cached before. */
internal fun LicenseState.clearsCache(): Boolean = this is LicenseState.Invalid ||
    this is LicenseState.Expired || this is LicenseState.Revoked || this is LicenseState.Unlicensed

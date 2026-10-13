package com.dcorp.licensecore.api

/** Result of one HTTP call to the License Engine, before any SDK-level error mapping. */
public sealed class LicenseApiOutcome<out T> {
    public data class Success<T>(val data: T) : LicenseApiOutcome<T>()
    public data class BusinessError(val code: Int, val message: String) : LicenseApiOutcome<Nothing>()
    public data class TransportError(val message: String) : LicenseApiOutcome<Nothing>()
}

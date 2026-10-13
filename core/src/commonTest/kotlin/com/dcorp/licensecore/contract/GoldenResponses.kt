package com.dcorp.licensecore.contract

/**
 * Hand-built from reading the actual backend source (`LicenseController.cs`, `LicenseModel.cs`,
 * `LicenseApiService.cs`, `LicenseDefaults.cs`, `ApiResponse.cs` - not guessed), since no live
 * dev/staging `skyapp-api` instance was reachable in this session (Phase 8's own plan calls for
 * capturing these from a live instance once one exists - this is the fallback until then).
 * `GoldenResponsesTest.kt` parses every one of these with the SDK's real DTOs.
 */
internal object GoldenResponses {
    // LicenseController.cs:98 verify, success path - LicenseApiService.cs:392-399 BuildVerifyResponse.
    val VERIFY_SUCCESS = """
        {"success":true,"data":{"isValid":true,"licenseKey":"LC-TEST-0001","licenseToken":"eyJhbGciOiJFUzI1NiIsImtpZCI6InYxIn0.eyJpc3MiOiJza3lhcHAtbGljZW5zZS1lbmdpbmUifQ.c2ln",
        "status":1,"statusText":"Active","activationDate":"2026-01-15T00:00:00","expiryDate":"2027-01-15T00:00:00",
        "daysRemaining":95,"maxDevice":3,"isTrial":false,"applicationRegisterId":12,"accProductId":7,"storeId":4}}
    """.trimIndent()

    // LicenseDefaults.cs:18 LICENSE_NOTFOUND = 1.
    val VERIFY_LICENSE_NOT_FOUND = """{"success":false,"error":1,"message":"License not found"}"""

    // LicenseDefaults.cs:42 LICENSE_STATUS_INVALID = 13.
    val VERIFY_STATUS_INVALID = """{"success":false,"error":13,"message":"License is not active"}"""

    // LicenseDefaults.cs:44 LICENSE_EXPIRED = 14.
    val VERIFY_EXPIRED = """{"success":false,"error":14,"message":"License has expired"}"""

    // LicenseDefaults.cs:48 DEVICE_LIMIT_REACHED = 16.
    val VERIFY_DEVICE_LIMIT_REACHED = """{"success":false,"error":16,"message":"Device limit reached for this license"}"""

    // LicenseDefaults.cs:60 NO_LICENSE_AVAILABLE = 22 (from /find).
    val FIND_NO_LICENSE_AVAILABLE = """{"success":false,"error":22,"message":"No available license found for this org/store/app"}"""

    // LicenseApiService.cs:992-996 IntrospectToken success.
    val INTROSPECT_VALID = """{"success":true,"data":{"isValid":true,"isRevoked":false,"isExpired":false}}"""
    val INTROSPECT_REVOKED = """{"success":true,"data":{"isValid":true,"isRevoked":true,"isExpired":false}}"""
    val INTROSPECT_BAD_SIGNATURE = """{"success":true,"data":{"isValid":false,"isRevoked":false,"isExpired":false}}"""

    // ReleaseDevice success - LicenseApiService.cs:970, empty ApiResponse<License>.
    val RELEASE_SUCCESS = """{"success":true}"""

    // LicenseDefaults.cs:50 DEVICE_NOT_FOUND = 17 (release, "already gone" - SDK treats as success).
    val RELEASE_DEVICE_NOT_FOUND = """{"success":false,"error":17,"message":"Device not found for this license"}"""

    // LicenseSettingApiService.cs:33-56 - note the two *_OverrideForStore fields the SDK's
    // LicenseSettingDto does NOT declare; ignoreUnknownKeys must drop them without error.
    val SETTINGS_SUCCESS = """
        {"success":true,"data":{"allowOffline":true,"allowOffline_OverrideForStore":false,
        "licenseCheckIntervalMinutes":60,"licenseCheckIntervalMinutes_OverrideForStore":false}}
    """.trimIndent()
}

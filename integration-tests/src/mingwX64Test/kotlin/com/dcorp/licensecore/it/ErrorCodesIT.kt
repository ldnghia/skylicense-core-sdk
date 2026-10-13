package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/** Backend error codes 1 (not found), 13 (inactive), 14 (expired) - see `ActivateFlowIT` for the
 *  skip/fail rule and the device-id gap note. */
class ErrorCodesIT {
    @Test
    fun unknownLicenseKey_isInvalidNotFound() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val state = integrationClient().activate("DOES-NOT-EXIST-${IntegrationEnv.runId}")
        val invalid = assertIs<LicenseState.Invalid>(state)
        assertIs<LicenseState.Invalid.Reason>(invalid.reason) // NotFound specifically, once a fixture confirms code 1
    }

    @Test
    fun inactiveLicense_isInvalidInactive() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val key = IntegrationEnv.inactiveLicenseKey ?: return@runTest
        val state = integrationClient().activate(key)
        assertIs<LicenseState.Invalid>(state)
    }

    @Test
    fun expiredLicense_isExpired() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val key = IntegrationEnv.expiredLicenseKey ?: return@runTest
        val state = integrationClient().activate(key)
        assertIs<LicenseState.Expired>(state)
    }
}

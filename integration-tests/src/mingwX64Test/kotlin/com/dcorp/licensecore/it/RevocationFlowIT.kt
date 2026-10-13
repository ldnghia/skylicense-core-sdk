package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RevocationFlowIT {
    @Test
    fun deactivateThenCheckNow_isUnlicensed() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val key = requireNotNull(IntegrationEnv.fixtureLicenseKey) { "LICENSE_IT_LICENSE_KEY not set" }
        val client = integrationClient()
        assertIs<LicenseState.Valid>(client.activate(key))
        assertIs<LicenseState.Unlicensed>(client.deactivate())
    }
}

package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class FindFlowIT {
    @Test
    fun activateForStore_claimsAFreeLicenseFromThePool() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val storeId = IntegrationEnv.storeId ?: return@runTest
        val client = integrationClient()
        val state = client.activateForStore(storeId, appId = IntegrationEnv.appId)
        assertIs<LicenseState.Valid>(state)
        client.deactivate()
    }
}

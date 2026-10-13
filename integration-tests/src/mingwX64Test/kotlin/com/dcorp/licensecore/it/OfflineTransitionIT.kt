package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseClientFactory
import com.dcorp.licensecore.LicenseConfig
import com.dcorp.licensecore.LicenseState
import com.dcorp.licensecore.platform.PlatformContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Activate online, then force the next check through an unreachable backend to exercise the
 * offline-grace path (`LicenseRecheckLogic.handleOffline`). Real network faults (not a mock
 * engine) per the plan - there is no public engine-override hook on [LicenseConfig]/
 * [LicenseClientFactory] to fault-inject a fake transport, so this instead points a *second*
 * client at an unroutable loopback port while reusing the first client's `appSalt` (and therefore
 * its on-disk cache directory, which is keyed only by the salt, not the base URL - see
 * `DpapiFileStore`) so the second client picks up the still-valid cached token and then fails to
 * reach the (bogus) backend on its next check, falling into [LicenseState.Valid.Mode.Offline].
 * Crossing the token's `exp` while offline is already covered by `ClockEdgeCasesTest` with a
 * virtual clock - no real 30+ minute wait here.
 */
class OfflineTransitionIT {
    @Test
    fun introspectUnreachable_fallsBackToOfflineGrace() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val licenseKey = requireNotNull(IntegrationEnv.fixtureLicenseKey) { "LICENSE_IT_LICENSE_KEY not set" }

        val online = integrationClient()
        val onlineState = assertIs<LicenseState.Valid>(online.activate(licenseKey))
        assertIs<LicenseState.Valid.Mode.Online>(onlineState.mode)

        val offlineConfig = LicenseConfig(
            baseUrl = "https://127.0.0.1:65500", // unroutable on this port - connection refused
            orgId = requireNotNull(IntegrationEnv.orgId) { "LICENSE_IT_ORG_ID not set" },
            appSalt = "integration-test-salt", // same salt -> same cache dir as `online` above
            trustedSigningKeys = buildMap {
                val kid = IntegrationEnv.signingKeyKid
                val pem = IntegrationEnv.signingKeyPem
                if (kid != null && pem != null) put(kid, pem)
            },
        )
        val offline = LicenseClientFactory.create(PlatformContext(), offlineConfig)
        val checkedState = offline.checkNow()
        val offlineState = assertIs<LicenseState.Valid>(checkedState)
        assertIs<LicenseState.Valid.Mode.Offline>(offlineState.mode)

        online.deactivate() // best-effort cleanup via the real (reachable) client
    }
}

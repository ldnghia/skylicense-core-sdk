package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Against a real dev/staging `skyapp-api` - requires `LICENSE_IT_*` env vars; skips (doesn't
 * fail) when not configured, per Phase 8's own rule: skip only when not configured, fail when
 * configured yet unreachable. No such environment was available in this session.
 *
 * KNOWN GAP vs. the plan's architecture: the plan calls for a unique synthetic device id per
 * run (`it-<runId>-<target>`) so parallel/repeated CI runs don't collide on `MaxDevice` slots.
 * [LicenseClientFactory] is the SDK's only public entry point and always derives the device id
 * from the real platform [com.dcorp.licensecore.platform.DeviceIdProvider] (by design - keeping
 * that out of the public API was a deliberate v1 choice) - there is no public way to inject a
 * synthetic id from this module. Until the SDK adds a test-only override, IT runs against the
 * SAME CI machine will reuse the same real device id across runs; fixture licenses used here
 * should tolerate that (e.g. `deactivate()` at the end of each test, as done below).
 */
class ActivateFlowIT {
    @Test
    fun activateWithFixtureKey_resultsInValidOnline() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val licenseKey = requireNotNull(IntegrationEnv.fixtureLicenseKey) { "LICENSE_IT_LICENSE_KEY not set" }

        val client = integrationClient()
        val state = client.activate(licenseKey)
        val valid = assertIs<LicenseState.Valid>(state)
        assertIs<LicenseState.Valid.Mode.Online>(valid.mode)

        client.deactivate() // best-effort cleanup - releases the synthetic device slot
    }
}

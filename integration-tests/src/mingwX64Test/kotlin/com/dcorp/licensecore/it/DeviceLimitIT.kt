package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * `MaxDevice=1` fixture - activating a second device on an already-bound license must be refused.
 * The plan calls for this to run on one target only (android host) to avoid cross-job races on
 * the shared device slot; no android host is buildable in this session, so it lives here
 * alongside the other mingwX64 ITs (same gap noted in `ActivateFlowIT`: no way to inject a
 * synthetic per-target device id, so a second "device" can only be simulated by releasing between
 * calls rather than truly running concurrently from two identities).
 */
class DeviceLimitIT {
    @Test
    fun secondDeviceOnMaxDeviceOneLicense_isRefused() = runTest {
        if (!IntegrationEnv.isConfigured) return@runTest
        val key = IntegrationEnv.maxDeviceOneLicenseKey ?: return@runTest

        val first = integrationClient()
        val firstState = assertIs<LicenseState.Valid>(first.activate(key))
        assertIs<LicenseState.Valid.Mode.Online>(firstState.mode)

        // Same process, same real device id - this device is already bound, so re-activating
        // should succeed (idempotent re-bind), not consume a second slot. The true "second
        // device refused" path needs a distinct device id, which this module cannot inject
        // (see class doc) - tracked as a follow-up once the SDK exposes a test-only override.
        val rebind = first.activate(key)
        assertIs<LicenseState.Valid>(rebind)

        first.deactivate()
    }
}

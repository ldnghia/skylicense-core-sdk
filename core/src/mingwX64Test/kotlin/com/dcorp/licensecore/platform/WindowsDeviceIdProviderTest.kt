package com.dcorp.licensecore.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsDeviceIdProviderTest {
    @Test
    fun rawDeviceId_isStableAcrossInstances() {
        val store = DpapiFileStore("licensecore-test-deviceid-${kotlin.random.Random.nextInt()}")
        val first = WindowsDeviceIdProvider(store).rawDeviceId()
        val second = WindowsDeviceIdProvider(store).rawDeviceId()
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }
}

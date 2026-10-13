package com.dcorp.licensecore.testsupport

import com.dcorp.licensecore.platform.DeviceIdProvider

public class FixedDeviceIdProvider(private val rawId: String = "fixed-raw-device-id") : DeviceIdProvider {
    override fun rawDeviceId(): String = rawId
}

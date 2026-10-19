package com.dcorp.licensecore.testsupport

import com.dcorp.licensecore.platform.DeviceAboutProvider

public class FixedDeviceAboutProvider(private val about: String = "fixed-device-about") : DeviceAboutProvider {
    override fun deviceAbout(): String = about
}

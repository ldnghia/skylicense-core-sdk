package com.dcorp.licensecore.platform

import platform.UIKit.UIDevice

/** UNVERIFIED (see IosDeviceIdProvider.kt - same caveat: never built until the fixes earlier this
 *  session, and even those only cover iosArm64/iosSimulatorArm64 compile, not a real run). */
internal class IosDeviceAboutProvider : DeviceAboutProvider {
    override fun deviceAbout(): String {
        val device = UIDevice.currentDevice
        return "${device.model}, ${device.systemName} ${device.systemVersion}"
    }
}

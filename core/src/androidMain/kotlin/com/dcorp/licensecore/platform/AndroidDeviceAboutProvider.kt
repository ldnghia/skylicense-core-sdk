package com.dcorp.licensecore.platform

import android.os.Build

/** UNVERIFIED (see DeviceIdProvider.android.kt - same caveat: never built, no SDK available). */
internal class AndroidDeviceAboutProvider : DeviceAboutProvider {
    override fun deviceAbout(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
}

package com.dcorp.licensecore.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Built from environment variables only (no registry/WinAPI struct calls needed) - `OS`,
 * `PROCESSOR_ARCHITECTURE` and `COMPUTERNAME` are set by the OS itself for every process, not
 * something an app can unset. Best-effort: missing vars are just omitted, never an error.
 */
@OptIn(ExperimentalForeignApi::class)
internal class WindowsDeviceAboutProvider : DeviceAboutProvider {
    override fun deviceAbout(): String {
        val parts = listOfNotNull(
            getenv("OS")?.toKString(),
            getenv("PROCESSOR_ARCHITECTURE")?.toKString(),
            getenv("COMPUTERNAME")?.toKString()?.let { "($it)" },
        )
        return parts.joinToString(" ")
    }
}

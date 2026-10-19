package com.dcorp.licensecore.platform

/**
 * Auto-collected, human-readable technical description of this device (OS, model...) - sent
 * alongside the caller-supplied `deviceName` so an admin searching for a device to release has a
 * second identification aid even when nobody set a name. See `LicenseDevice.DeviceAbout` on the
 * backend. Best-effort only: never throws, returns an empty string if nothing could be determined.
 */
public interface DeviceAboutProvider {
    public fun deviceAbout(): String
}

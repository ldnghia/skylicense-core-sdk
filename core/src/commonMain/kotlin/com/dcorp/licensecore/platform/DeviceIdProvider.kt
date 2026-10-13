package com.dcorp.licensecore.platform

/** Supplies a raw, platform-specific device identifier. Never sent to the backend directly -
 *  [com.dcorp.licensecore.device.DeviceIdHasher] hashes it with the app salt first. */
public interface DeviceIdProvider {
    public fun rawDeviceId(): String
}

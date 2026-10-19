package com.dcorp.licensecore.platform

import com.dcorp.licensecore.LicenseConfig

public actual fun createPlatformModule(context: PlatformContext, config: LicenseConfig): PlatformModule {
    // Unlike the Android Keystore alias, a Keychain kSecAttrService string has no charset
    // restriction worth hashing around - the app salt is not sensitive as a plain identifier.
    val service = "com.dcorp.licensecore.${config.appSalt}"
    return PlatformModule(
        storage = KeychainSecureStorage(service),
        deviceIdProvider = IosDeviceIdProvider(service),
        deviceAboutProvider = IosDeviceAboutProvider(),
        httpEngine = DarwinHttpEngineFactory.create(config.certificatePins),
    )
}

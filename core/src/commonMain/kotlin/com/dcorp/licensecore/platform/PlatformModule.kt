package com.dcorp.licensecore.platform

import com.dcorp.licensecore.LicenseConfig
import io.ktor.client.engine.HttpClientEngine

/** Everything that differs per platform, bundled behind one factory function. */
public class PlatformModule(
    public val storage: SecureStorage,
    public val deviceIdProvider: DeviceIdProvider,
    public val httpEngine: HttpClientEngine,
)

public expect fun createPlatformModule(context: PlatformContext, config: LicenseConfig): PlatformModule

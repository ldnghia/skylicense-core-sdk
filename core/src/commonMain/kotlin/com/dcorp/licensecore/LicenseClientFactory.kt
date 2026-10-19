package com.dcorp.licensecore

import com.dcorp.licensecore.api.LicenseApiService
import com.dcorp.licensecore.api.LicenseHttpClientFactory
import com.dcorp.licensecore.cache.LicenseCacheRepository
import com.dcorp.licensecore.platform.PlatformContext
import com.dcorp.licensecore.platform.createPlatformModule
import com.dcorp.licensecore.token.LicenseTokenVerifier

public object LicenseClientFactory {
    public fun create(context: PlatformContext, config: LicenseConfig): LicenseClient {
        val platform = createPlatformModule(context, config)
        val http = LicenseHttpClientFactory.create(platform.httpEngine, config)
        return LicenseClient(
            config = config,
            deviceIdProvider = platform.deviceIdProvider,
            deviceAboutProvider = platform.deviceAboutProvider,
            api = LicenseApiService(http, config),
            verifier = LicenseTokenVerifier(config.trustedSigningKeys),
            cache = LicenseCacheRepository(platform.storage),
        )
    }
}

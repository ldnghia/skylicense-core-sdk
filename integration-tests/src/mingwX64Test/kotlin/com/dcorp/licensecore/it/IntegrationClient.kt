package com.dcorp.licensecore.it

import com.dcorp.licensecore.LicenseClient
import com.dcorp.licensecore.LicenseClientFactory
import com.dcorp.licensecore.LicenseConfig
import com.dcorp.licensecore.platform.PlatformContext

/** Builds a real [LicenseClient] against [IntegrationEnv.baseUrl] - only usable when
 *  [IntegrationEnv.isConfigured] (callers must check that first and skip otherwise). */
internal fun integrationClient(): LicenseClient {
    val config = LicenseConfig(
        baseUrl = requireNotNull(IntegrationEnv.baseUrl) { "LICENSE_IT_BASE_URL not set" },
        orgId = requireNotNull(IntegrationEnv.orgId) { "LICENSE_IT_ORG_ID not set" },
        appSalt = "integration-test-salt",
        trustedSigningKeys = buildMap {
            val kid = IntegrationEnv.signingKeyKid
            val pem = IntegrationEnv.signingKeyPem
            if (kid != null && pem != null) put(kid, pem)
        },
    )
    return LicenseClientFactory.create(PlatformContext(), config)
}

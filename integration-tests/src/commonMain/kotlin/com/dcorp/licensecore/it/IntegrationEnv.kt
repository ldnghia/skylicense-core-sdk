package com.dcorp.licensecore.it

/** Platform-specific environment variable read - iOS simulator test binaries only see
 *  `SIMCTL_CHILD_`-prefixed vars from the host shell (unverified in this session, no macOS). */
internal expect fun readEnv(name: String): String?

/**
 * Integration-test configuration from the environment. [isConfigured] is false when
 * [LICENSE_IT_BASE_URL] isn't set - the plan's own rule: skip (don't fail) when not configured
 * (local dev), but fail (don't skip) when configured yet unreachable (CI).
 */
internal object IntegrationEnv {
    val baseUrl: String? = readEnv("LICENSE_IT_BASE_URL")
    val orgId: Int? = readEnv("LICENSE_IT_ORG_ID")?.toIntOrNull()
    val storeId: Int? = readEnv("LICENSE_IT_STORE_ID")?.toIntOrNull()
    val appId: Int? = readEnv("LICENSE_IT_APP_ID")?.toIntOrNull()
    val fixtureLicenseKey: String? = readEnv("LICENSE_IT_LICENSE_KEY")
    val inactiveLicenseKey: String? = readEnv("LICENSE_IT_INACTIVE_LICENSE_KEY")
    val expiredLicenseKey: String? = readEnv("LICENSE_IT_EXPIRED_LICENSE_KEY")
    val maxDeviceOneLicenseKey: String? = readEnv("LICENSE_IT_MAX_DEVICE_ONE_LICENSE_KEY")
    val signingKeyKid: String? = readEnv("LICENSE_IT_SIGNING_KEY_KID")
    val signingKeyPem: String? = readEnv("LICENSE_IT_SIGNING_KEY_PEM")
    val runId: String = readEnv("LICENSE_IT_RUN_ID") ?: "local"

    val isConfigured: Boolean get() = baseUrl != null

    /** `it-<runId>-<target>` per the plan's device-id convention, so cleanup can find them. */
    fun syntheticDeviceId(target: String): String = "it-$runId-$target"
}

package com.dcorp.licensecore.policy

/**
 * Decides what to do when the periodic re-check can't reach the server. Offline grace is bounded
 * by the cached token's own `exp` - the SDK never extends it (`LicenseApiService.cs:384-388`);
 * long offline use is an admin-mediated bind with a longer `TokenLifetimeMinutes` instead.
 */
public object OfflineGracePolicy {
    public sealed class Decision {
        public data class RemainOffline(public val graceUntilEpochSeconds: Long) : Decision()
        public data object RequireOnline : Decision()
    }

    public fun decide(allowOffline: Boolean, nowEpochSeconds: Long, tokenExpiresEpochSeconds: Long): Decision {
        if (!allowOffline) return Decision.RequireOnline
        if (nowEpochSeconds >= tokenExpiresEpochSeconds) return Decision.RequireOnline
        return Decision.RemainOffline(tokenExpiresEpochSeconds)
    }
}

package com.dcorp.licensecore.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfflineGracePolicyTest {
    @Test
    fun allowOfflineAndNotYetExpired_remainsOffline() {
        val decision = OfflineGracePolicy.decide(allowOffline = true, nowEpochSeconds = 100, tokenExpiresEpochSeconds = 200)
        val remain = assertIs<OfflineGracePolicy.Decision.RemainOffline>(decision)
        assertEquals(200, remain.graceUntilEpochSeconds)
    }

    @Test
    fun allowOfflineButExpired_requiresOnline() {
        val decision = OfflineGracePolicy.decide(allowOffline = true, nowEpochSeconds = 250, tokenExpiresEpochSeconds = 200)
        assertIs<OfflineGracePolicy.Decision.RequireOnline>(decision)
    }

    @Test
    fun offlineNotAllowed_requiresOnlineEvenIfTokenStillValid() {
        val decision = OfflineGracePolicy.decide(allowOffline = false, nowEpochSeconds = 100, tokenExpiresEpochSeconds = 200)
        assertIs<OfflineGracePolicy.Decision.RequireOnline>(decision)
    }

    @Test
    fun exactlyAtExpiry_requiresOnline() {
        val decision = OfflineGracePolicy.decide(allowOffline = true, nowEpochSeconds = 200, tokenExpiresEpochSeconds = 200)
        assertIs<OfflineGracePolicy.Decision.RequireOnline>(decision)
    }
}

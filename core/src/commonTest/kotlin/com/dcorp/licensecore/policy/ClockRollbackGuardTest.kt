package com.dcorp.licensecore.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockRollbackGuardTest {
    @Test
    fun clockMovingForward_isNotTampered() {
        assertFalse(ClockRollbackGuard.isTampered(nowEpochSeconds = 1000, maxObservedEpochSeconds = 900))
    }

    @Test
    fun smallBackwardJumpWithinTolerance_isNotTampered() {
        assertFalse(ClockRollbackGuard.isTampered(nowEpochSeconds = 900, maxObservedEpochSeconds = 1000))
    }

    @Test
    fun bigBackwardJumpBeyondTolerance_isTampered() {
        assertTrue(ClockRollbackGuard.isTampered(nowEpochSeconds = 900, maxObservedEpochSeconds = 1000 + 5 * 60 + 1))
    }

    @Test
    fun nextMaxObserved_keepsTheHigherValue() {
        assertEquals(1000L, ClockRollbackGuard.nextMaxObserved(nowEpochSeconds = 900, maxObservedEpochSeconds = 1000))
        assertEquals(1100L, ClockRollbackGuard.nextMaxObserved(nowEpochSeconds = 1100, maxObservedEpochSeconds = 1000))
    }
}

package com.dcorp.licensecore.policy

/**
 * Detects a backward system-clock jump of more than 5 minutes versus the highest time this
 * device has ever observed - the cheapest defense against "turn the clock back to dodge
 * expiry" while offline. Once tripped, offline grace is disabled until the next successful
 * online check (handled by the caller, not this pure function).
 */
public object ClockRollbackGuard {
    private const val TOLERANCE_SECONDS = 5 * 60L

    public fun isTampered(nowEpochSeconds: Long, maxObservedEpochSeconds: Long): Boolean =
        nowEpochSeconds < maxObservedEpochSeconds - TOLERANCE_SECONDS

    public fun nextMaxObserved(nowEpochSeconds: Long, maxObservedEpochSeconds: Long): Long =
        maxOf(nowEpochSeconds, maxObservedEpochSeconds)
}

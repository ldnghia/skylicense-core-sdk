package com.dcorp.licensecore

/** Seam for injecting a fake clock in tests; production code uses [SystemLicenseClock]. */
public interface LicenseClock {
    public fun nowEpochSeconds(): Long
}

public object SystemLicenseClock : LicenseClock {
    override fun nowEpochSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds
}

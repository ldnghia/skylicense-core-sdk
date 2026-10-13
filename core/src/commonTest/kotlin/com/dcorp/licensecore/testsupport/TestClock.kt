package com.dcorp.licensecore.testsupport

import com.dcorp.licensecore.LicenseClock

public class TestClock(public var currentEpochSeconds: Long = 1_700_000_000L) : LicenseClock {
    override fun nowEpochSeconds(): Long = currentEpochSeconds
}

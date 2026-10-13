package com.dcorp.licensecore

/**
 * Plain callback interface for consumers that can't easily collect a `StateFlow` (Swift without
 * SKIE, C via the Windows ABI). [LicenseClient.addStateListener] returns a [Closeable] to detach.
 */
public fun interface LicenseStateListener {
    public fun onStateChanged(state: LicenseState)
}

public fun interface Closeable {
    public fun close()
}

package com.dcorp.licensecore.it

import platform.Foundation.NSProcessInfo

/** UNVERIFIED (no macOS host in this session): iOS simulator XCTest hosts only forward
 *  `SIMCTL_CHILD_`-prefixed env vars from the launching shell - try that prefix first, then the
 *  bare name for a device/CI runner that doesn't go through simctl. */
internal actual fun readEnv(name: String): String? {
    val env = NSProcessInfo.processInfo.environment
    return (env["SIMCTL_CHILD_$name"] ?: env[name]) as? String
}

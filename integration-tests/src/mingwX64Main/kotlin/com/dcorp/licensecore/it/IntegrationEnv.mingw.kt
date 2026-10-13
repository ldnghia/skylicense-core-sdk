package com.dcorp.licensecore.it

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun readEnv(name: String): String? = getenv(name)?.toKString()

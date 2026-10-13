package com.dcorp.licensecore.it

internal actual fun readEnv(name: String): String? = System.getenv(name)

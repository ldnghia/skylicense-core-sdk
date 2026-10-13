package com.dcorp.licensecore

import kotlin.test.Test
import kotlin.test.assertTrue

class LicenseCoreVersionTest {
    @Test
    fun versionIsNotBlank() {
        assertTrue(LICENSE_CORE_SDK_VERSION.isNotBlank())
    }
}

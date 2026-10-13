@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.dcorp.licensecore.capi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CApiExportsTest {
    @Test
    fun createGetStateDestroy_withoutNetwork_doesNotCrash() {
        val config = """{"baseUrl":"https://license.invalid","orgId":1,"appSalt":"capi-test-salt"}"""
        val handle = lcCreate(config.toCStringMalloc())
        assertNotNull(handle)

        val statePtr = lcGetState(handle)
        assertNotNull(statePtr)
        val state = statePtr.toKotlinStringOrNull()
        assertEquals("""{"state":"UNLICENSED"}""", state)
        lcFreeString(statePtr)

        val idPtr = lcDeviceId(handle)
        val id = idPtr.toKotlinStringOrNull()
        assertNotNull(id)
        assertEquals(64, id!!.length)
        lcFreeString(idPtr)

        lcDestroy(handle)
    }

    @Test
    fun create_withMalformedConfig_returnsNullAndSetsLastError() {
        val handle = lcCreate("not valid json".toCStringMalloc())
        assertNull(handle)
        val err = lcLastError()
        assertTrue(err.toKotlinStringOrNull()!!.isNotEmpty())
        lcFreeString(err)
    }

    @Test
    fun create_withNullConfig_returnsNull() {
        assertNull(lcCreate(null))
    }

    @Test
    fun getState_withNullHandle_returnsErrorJsonNotCrash() {
        val result = lcGetState(null)
        assertNotNull(result)
        val json = result.toKotlinStringOrNull()!!
        assertTrue(json.contains("INVALID_HANDLE"))
        lcFreeString(result)
    }

    @Test
    fun abiVersion_isOne() {
        assertEquals(1, lcAbiVersion())
    }
}

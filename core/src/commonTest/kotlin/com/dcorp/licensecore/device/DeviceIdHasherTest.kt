package com.dcorp.licensecore.device

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeviceIdHasherTest {
    @Test
    fun hashIs64LowercaseHexChars() = runTest {
        val hash = DeviceIdHasher.hash("raw-device-id", "app-salt")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun sameInputsProduceSameHash() = runTest {
        assertEquals(DeviceIdHasher.hash("raw", "salt"), DeviceIdHasher.hash("raw", "salt"))
    }

    @Test
    fun differentSaltsProduceDifferentHashes() = runTest {
        assertNotEquals(DeviceIdHasher.hash("raw", "salt-a"), DeviceIdHasher.hash("raw", "salt-b"))
    }
}

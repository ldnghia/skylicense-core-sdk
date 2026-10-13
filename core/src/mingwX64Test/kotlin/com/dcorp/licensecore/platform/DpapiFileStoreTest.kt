package com.dcorp.licensecore.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class DpapiFileStoreTest {
    private val store = DpapiFileStore("licensecore-test-${kotlin.random.Random.nextInt()}")

    @AfterTest
    fun cleanup() {
        store.delete(FILE)
    }

    @Test
    fun writeThenRead_roundTrips() {
        val original = "dpapi-file-store-test".encodeToByteArray()
        store.write(FILE, original)
        assertContentEquals(original, store.read(FILE))
    }

    @Test
    fun readWithNothingWritten_returnsNull() {
        assertNull(store.read("never-written.bin"))
    }

    @Test
    fun deleteThenRead_returnsNull() {
        store.write(FILE, "to-be-deleted".encodeToByteArray())
        store.delete(FILE)
        assertNull(store.read(FILE))
    }

    @Test
    fun overwrite_replacesPreviousContent() {
        store.write(FILE, "first".encodeToByteArray())
        store.write(FILE, "second".encodeToByteArray())
        assertContentEquals("second".encodeToByteArray(), store.read(FILE))
    }

    private companion object {
        const val FILE = "roundtrip.bin"
    }
}

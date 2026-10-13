package com.dcorp.licensecore.platform

import kotlinx.cinterop.*
import platform.windows.CRYPTPROTECT_UI_FORBIDDEN
import platform.windows.CryptProtectData
import platform.windows.CryptUnprotectData
import platform.windows.DATA_BLOB
import platform.windows.LocalFree
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class WindowsApiSpikeTest {
    @Test
    fun cryptProtectDataRoundTripsViaPlatformWindows(): Unit = memScoped {
        val plaintext = "licensecore-dpapi-spike".encodeToByteArray()
        val inBuf = allocArray<ByteVar>(plaintext.size)
        for (i in plaintext.indices) inBuf[i] = plaintext[i]

        val inBlob = alloc<DATA_BLOB>().apply {
            cbData = plaintext.size.toUInt()
            pbData = inBuf.reinterpret()
        }
        val outBlob = alloc<DATA_BLOB>()

        val protectOk = CryptProtectData(inBlob.ptr, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), outBlob.ptr)
        assertTrue(protectOk != 0, "CryptProtectData should succeed")

        val decryptedBlob = alloc<DATA_BLOB>()
        val unprotectOk = CryptUnprotectData(outBlob.ptr, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), decryptedBlob.ptr)
        assertTrue(unprotectOk != 0, "CryptUnprotectData should succeed")

        val decrypted = ByteArray(decryptedBlob.cbData.toInt())
        val decryptedPtr = decryptedBlob.pbData!!.reinterpret<ByteVar>()
        for (i in decrypted.indices) decrypted[i] = decryptedPtr[i]

        assertContentEquals(plaintext, decrypted)

        LocalFree(outBlob.pbData)
        LocalFree(decryptedBlob.pbData)
    }
}

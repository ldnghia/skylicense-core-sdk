package com.dcorp.licensecore.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.getenv
import platform.windows.CREATE_ALWAYS
import platform.windows.CRYPTPROTECT_UI_FORBIDDEN
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.CryptProtectData
import platform.windows.CryptUnprotectData
import platform.windows.DATA_BLOB
import platform.windows.DWORDVar
import platform.windows.DeleteFileW
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetFileSize
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LocalFree
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MOVEFILE_WRITE_THROUGH
import platform.windows.MoveFileExW
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.WriteFile

/**
 * Low-level, synchronous DPAPI (user scope) + atomic-file-write helper shared by
 * [DpapiSecureStorage] (suspend wrapper for the cache) and [WindowsDeviceIdProvider] (sync, no
 * suspend seam available on [DeviceIdProvider]). `entropy` scopes decryption to this app salt so
 * a different app on the same machine/user can't decrypt this one's data.
 *
 * Any read/decrypt failure (corrupt file, DPAPI blob from a different user/machine) deletes the
 * file and returns null - callers treat that as "nothing cached".
 */
@OptIn(ExperimentalForeignApi::class)
internal class DpapiFileStore(appSaltHash: String) {
    private val dir = "${localAppData()}\\LicenseCore\\$appSaltHash"
    private val entropy = appSaltHash.encodeToByteArray()

    init {
        ensureDirectory(dir)
    }

    fun read(fileName: String): ByteArray? {
        val path = filePath(fileName)
        val raw = readFile(path) ?: return null
        val plain = runCatching { crypt(raw, encrypt = false) }.getOrNull()
        if (plain == null) DeleteFileW(path)
        return plain
    }

    fun write(fileName: String, bytes: ByteArray) {
        val protected = crypt(bytes, encrypt = true)
        val path = filePath(fileName)
        val tempPath = "$path.tmp"
        writeFile(tempPath, protected)
        MoveFileExW(tempPath, path, (MOVEFILE_REPLACE_EXISTING or MOVEFILE_WRITE_THROUGH).toUInt())
    }

    fun delete(fileName: String) {
        DeleteFileW(filePath(fileName))
    }

    private fun filePath(fileName: String) = "$dir\\$fileName"

    private fun crypt(data: ByteArray, encrypt: Boolean): ByteArray = memScoped {
        val inBlob = dataBlobOf(data)
        val entropyBlob = dataBlobOf(entropy)
        val outBlob = alloc<DATA_BLOB>()
        val ok = if (encrypt) {
            CryptProtectData(inBlob.ptr, null, entropyBlob.ptr, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), outBlob.ptr)
        } else {
            CryptUnprotectData(inBlob.ptr, null, entropyBlob.ptr, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), outBlob.ptr)
        }
        if (ok == 0) error("DPAPI ${if (encrypt) "protect" else "unprotect"} failed: ${GetLastError()}")
        val outPtr = outBlob.pbData!!
        val result = ByteArray(outBlob.cbData.toInt()) { i -> outPtr[i].toByte() }
        LocalFree(outBlob.pbData)
        result
    }

    private fun kotlinx.cinterop.MemScope.dataBlobOf(bytes: ByteArray) = alloc<DATA_BLOB>().apply {
        val buf = allocArray<ByteVar>(bytes.size)
        for (i in bytes.indices) buf[i] = bytes[i]
        cbData = bytes.size.toUInt()
        pbData = buf.reinterpret()
    }

    private fun readFile(path: String): ByteArray? = memScoped {
        val handle = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ.toUInt(), null, OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
        if (handle == INVALID_HANDLE_VALUE) return@memScoped null
        try {
            val size = GetFileSize(handle, null)
            if (size == 0xFFFFFFFFu) return@memScoped null
            val buffer = allocArray<ByteVar>(size.toInt())
            val bytesRead = alloc<DWORDVar>()
            val ok = ReadFile(handle, buffer, size, bytesRead.ptr, null)
            if (ok == 0) return@memScoped null
            ByteArray(bytesRead.value.toInt()) { i -> buffer[i] }
        } finally {
            CloseHandle(handle)
        }
    }

    private fun writeFile(path: String, bytes: ByteArray) {
        val handle = CreateFileW(path, GENERIC_WRITE.toUInt(), 0u, null, CREATE_ALWAYS.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
        if (handle == INVALID_HANDLE_VALUE) error("CreateFileW failed for $path: ${GetLastError()}")
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(bytes.size)
                for (i in bytes.indices) buffer[i] = bytes[i]
                val written = alloc<DWORDVar>()
                WriteFile(handle, buffer, bytes.size.toUInt(), written.ptr, null)
            }
        } finally {
            CloseHandle(handle)
        }
    }

    private fun ensureDirectory(path: String) {
        var current = ""
        for (part in path.split("\\")) {
            current = if (current.isEmpty()) part else "$current\\$part"
            if (current.endsWith(":")) continue
            CreateDirectoryW(current, null)
        }
    }

    private companion object {
        fun localAppData(): String = getenv("LOCALAPPDATA")?.toKString() ?: error("LOCALAPPDATA not set")
    }
}

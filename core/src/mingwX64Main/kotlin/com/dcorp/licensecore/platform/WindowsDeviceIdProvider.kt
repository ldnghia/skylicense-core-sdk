package com.dcorp.licensecore.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.windows.DWORDVar
import platform.windows.ERROR_SUCCESS
import platform.windows.HKEY_LOCAL_MACHINE
import platform.windows.RRF_RT_REG_SZ
import platform.windows.RegGetValueW
import kotlin.random.Random

/**
 * `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid` - stable across this machine's Windows
 * users, survives app reinstall/update, resets only on OS reinstall. Cloned VMs deployed without
 * sysprep share a MachineGuid (and so a device slot) - documented limitation, not fixed here.
 * Falls back to a random id persisted via DPAPI if the registry read fails for any reason.
 */
@OptIn(ExperimentalForeignApi::class)
internal class WindowsDeviceIdProvider(private val store: DpapiFileStore) : DeviceIdProvider {
    override fun rawDeviceId(): String {
        readMachineGuid()?.let { return it }

        store.read(FALLBACK_FILE)?.let { return it.decodeToString() }
        val generated = randomId()
        store.write(FALLBACK_FILE, generated.encodeToByteArray())
        return generated
    }

    private fun readMachineGuid(): String? = memScoped {
        val bufferChars = 64
        val buffer = allocArray<UShortVar>(bufferChars)
        val size = alloc<DWORDVar>().apply { value = (bufferChars * 2).toUInt() }
        val status = RegGetValueW(
            HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Microsoft\\Cryptography",
            "MachineGuid",
            RRF_RT_REG_SZ.toUInt(),
            null,
            buffer,
            size.ptr,
        )
        if (status != ERROR_SUCCESS) return@memScoped null

        val sb = StringBuilder()
        var i = 0
        while (i < bufferChars) {
            val unit = buffer[i]
            if (unit == 0.toUShort()) break
            sb.append(unit.toInt().toChar())
            i++
        }
        sb.toString().ifBlank { null }
    }

    private fun randomId(): String {
        val bytes = Random.nextBytes(16)
        return bytes.joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
    }

    private companion object {
        const val FALLBACK_FILE = "device-id-fallback.bin"
    }
}

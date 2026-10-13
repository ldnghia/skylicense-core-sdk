package com.dcorp.licensecore.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Generic-password Keychain item per key, accessibility `AfterFirstUnlockThisDeviceOnly` (not
 * synced to iCloud, not migrated by a device backup/restore onto a different device - matches
 * the `did` claim check in `LicenseTokenVerifier`, which would reject a copied token anyway).
 *
 * UNVERIFIED: Kotlin/Native iOS targets cannot be compiled on Windows under any configuration -
 * this file has never been built. It follows the well-established `NSMutableDictionary` +
 * `CFBridgingRelease` Keychain wrapper pattern used by other Kotlin Multiplatform libraries, but
 * exact `kSecXxx` constant types are a known source of first-try compile errors in Kotlin/Native
 * Security interop - expect to fix this up on a real macOS host (Phase 4's own plan already
 * flags a "spike" step for exactly this).
 */
internal class KeychainSecureStorage(private val service: String) : SecureStorage {

    override suspend fun get(key: String): ByteArray? = memScoped {
        val query = baseQuery(key).apply {
            setObject(kSecReturnData, forKey = kSecReturnData)
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        data.toByteArray()
    }

    override suspend fun put(key: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        val found = SecItemCopyMatching(baseQuery(key), null) == errSecSuccess
        if (found) {
            val update = NSMutableDictionary().apply { setObject(data, forKey = kSecValueData) }
            SecItemUpdate(baseQuery(key), update)
        } else {
            val insert = baseQuery(key).apply {
                setObject(data, forKey = kSecValueData)
                setObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly, forKey = kSecAttrAccessible)
            }
            SecItemAdd(insert, null)
        }
    }

    override suspend fun remove(key: String) {
        SecItemDelete(baseQuery(key))
    }

    private fun baseQuery(key: String): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, forKey = kSecClass)
        setObject(service, forKey = kSecAttrService)
        setObject(key, forKey = kSecAttrAccount)
    }

    private fun ByteArray.toNSData(): NSData = memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytesPtr = bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
        return ByteArray(size) { i -> bytesPtr[i] }
    }
}

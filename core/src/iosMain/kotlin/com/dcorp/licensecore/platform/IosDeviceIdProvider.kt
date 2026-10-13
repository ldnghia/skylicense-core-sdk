package com.dcorp.licensecore.platform

import kotlinx.cinterop.memScoped
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
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
import platform.UIKit.UIDevice

/**
 * `identifierForVendor` changes when every app from this vendor is uninstalled, which would burn
 * a `MaxDevice` slot on reinstall - instead store a random UUID in the Keychain (survives
 * reinstall in practice), falling back to `identifierForVendor` only if the Keychain write fails.
 *
 * UNVERIFIED (see `KeychainSecureStorage` - same caveat: never built, no macOS host available).
 */
internal class IosDeviceIdProvider(private val service: String) : DeviceIdProvider {
    override fun rawDeviceId(): String {
        readUuid()?.let { return it }

        val generated = NSUUID().UUIDString()
        return if (writeUuid(generated)) generated else (UIDevice.currentDevice.identifierForVendor?.UUIDString() ?: generated)
    }

    private fun readUuid(): String? = memScoped {
        val query = baseQuery().apply {
            setObject(kSecReturnData, forKey = kSecReturnData)
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    private fun writeUuid(uuid: String): Boolean {
        val data = (uuid as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val insert = baseQuery().apply {
            setObject(data, forKey = kSecValueData)
            setObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly, forKey = kSecAttrAccessible)
        }
        return SecItemAdd(insert, null) == errSecSuccess
    }

    private fun baseQuery(): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, forKey = kSecClass)
        setObject(service, forKey = kSecAttrService)
        setObject(ACCOUNT, forKey = kSecAttrAccount)
    }

    private companion object {
        const val ACCOUNT = "device-uuid"
    }
}

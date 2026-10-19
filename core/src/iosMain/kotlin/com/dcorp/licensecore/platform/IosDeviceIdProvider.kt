package com.dcorp.licensecore.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSUUID
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
 * Bridges `NSMutableDictionary` <-> `CFDictionaryRef` at the `SecItemXxx` boundary and the UUID
 * string via plain `ByteArray` (see [CfBridging]) - no `NSString` cast, which Kotlin/Native's
 * compiler statically rejects as always-failing (`uuid as NSString` is not the same object graph
 * as `kotlin.String` on this compiler version). Built and verified against a real Kotlin/Native
 * iOS compile on 2026-10-14.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosDeviceIdProvider(private val service: String) : DeviceIdProvider {
    override fun rawDeviceId(): String {
        readUuid()?.let { return it }

        val generated = NSUUID().UUIDString()
        return if (writeUuid(generated)) generated else (UIDevice.currentDevice.identifierForVendor?.UUIDString() ?: generated)
    }

    private fun readUuid(): String? = memScoped {
        val query = baseQuery().apply {
            setObject(kCFBooleanTrue.asNSNumber(), forKey = kSecReturnData.asNSString())
            setObject(kSecMatchLimitOne.asNSString(), forKey = kSecMatchLimit.asNSString())
        }
        val cfQuery = query.asCFDictionary()
        try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(cfQuery, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            data.toByteArray().decodeToString()
        } finally {
            CFRelease(cfQuery)
        }
    }

    private fun writeUuid(uuid: String): Boolean {
        val data = uuid.encodeToByteArray().toNSData()
        val insert = baseQuery().apply {
            setObject(data, forKey = kSecValueData.asNSString())
            setObject(
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.asNSString(),
                forKey = kSecAttrAccessible.asNSString(),
            )
        }
        val cfInsert = insert.asCFDictionary()
        try {
            return SecItemAdd(cfInsert, null) == errSecSuccess
        } finally {
            CFRelease(cfInsert)
        }
    }

    private fun baseQuery(): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
        setObject(service, forKey = kSecAttrService.asNSString())
        setObject(ACCOUNT, forKey = kSecAttrAccount.asNSString())
    }

    private companion object {
        const val ACCOUNT = "device-uuid"
    }
}

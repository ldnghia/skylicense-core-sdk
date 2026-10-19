package com.dcorp.licensecore.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
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
 * Builds queries as `NSMutableDictionary` for ergonomics, bridges to `CFDictionaryRef` only at
 * the `SecItemXxx` call boundary (see [CfBridging]). Built and verified against a real Kotlin/Native
 * iOS compile on 2026-10-14.
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainSecureStorage(private val service: String) : SecureStorage {

    override suspend fun get(key: String): ByteArray? = memScoped {
        val query = baseQuery(key).apply {
            setObject(kCFBooleanTrue.asNSNumber(), forKey = kSecReturnData.asNSString())
            setObject(kSecMatchLimitOne.asNSString(), forKey = kSecMatchLimit.asNSString())
        }
        val cfQuery = query.asCFDictionary()
        try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(cfQuery, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            data.toByteArray()
        } finally {
            CFRelease(cfQuery)
        }
    }

    override suspend fun put(key: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        val found = withCfDictionary(baseQuery(key)) { SecItemCopyMatching(it, null) } == errSecSuccess
        if (found) {
            val update = NSMutableDictionary().apply { setObject(data, forKey = kSecValueData.asNSString()) }
            withCfDictionary(baseQuery(key)) { query -> withCfDictionary(update) { SecItemUpdate(query, it) } }
        } else {
            val insert = baseQuery(key).apply {
                setObject(data, forKey = kSecValueData.asNSString())
                setObject(
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.asNSString(),
                    forKey = kSecAttrAccessible.asNSString(),
                )
            }
            withCfDictionary(insert) { SecItemAdd(it, null) }
        }
    }

    override suspend fun remove(key: String) {
        withCfDictionary(baseQuery(key)) { SecItemDelete(it) }
    }

    private fun baseQuery(key: String): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
        setObject(service, forKey = kSecAttrService.asNSString())
        setObject(key, forKey = kSecAttrAccount.asNSString())
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withCfDictionary(dict: NSMutableDictionary, block: (CFDictionaryRef) -> T): T {
    val cfDict = dict.asCFDictionary()
    try {
        return block(cfDict)
    } finally {
        CFRelease(cfDict)
    }
}

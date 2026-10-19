package com.dcorp.licensecore.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * Security framework constants (`kSecXxx`) come through cinterop as raw `CFStringRef`
 * (`CPointer<__CFString>?`), not `NSString` - unlike Swift/ObjC, Kotlin/Native does not toll-free
 * bridge them automatically, so `NSMutableDictionary.setObject(forKey:)` (which wants
 * `NSCopyingProtocol`) rejects them at compile time. These are process-lifetime singletons, so an
 * unbalanced `CFBridgingRelease` is safe (nothing ever frees them).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<*>?.asNSString(): NSString = CFBridgingRelease(this) as NSString

/** `kCFBooleanTrue`/`kCFBooleanFalse` bridge to `NSNumber`, not `NSString`. */
@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<*>?.asNSNumber(): NSNumber = CFBridgingRelease(this) as NSNumber

/**
 * `SecItemXxx` C functions want `CFDictionaryRef`, not `NSDictionary`. `CFBridgingRetain` takes a
 * +1 CF retain on the underlying object; call sites release it with `CFRelease` once the C call
 * returns to avoid leaking the query dictionary.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSMutableDictionary.asCFDictionary(): CFDictionaryRef =
    CFBridgingRetain(this)!!.reinterpret()

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytesPtr = bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
    return ByteArray(size) { i -> bytesPtr[i] }
}

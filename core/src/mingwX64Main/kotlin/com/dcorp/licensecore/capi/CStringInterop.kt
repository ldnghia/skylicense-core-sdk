package com.dcorp.licensecore.capi

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.free
import platform.posix.malloc

/**
 * Explicit `malloc`-allocated UTF-8 strings crossing the C boundary - avoids relying on Kotlin/
 * Native's implicit `String`<->`CPointer<ByteVar>` conversion at `@CName` boundaries (plan's
 * Key Insight: the default struct-of-function-pointers export has known marshalling quirks;
 * being explicit here sidesteps them). The caller must free the result via `lc_free_string`.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun String.toCStringMalloc(): CPointer<ByteVar> {
    val bytes = encodeToByteArray()
    val buffer = malloc((bytes.size + 1).toULong())!!.reinterpret<ByteVar>()
    for (i in bytes.indices) buffer[i] = bytes[i]
    buffer[bytes.size] = 0
    return buffer
}

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<ByteVar>?.toKotlinStringOrNull(): String? = this?.toKString()

@OptIn(ExperimentalForeignApi::class)
internal fun freeCString(pointer: CPointer<ByteVar>?) {
    if (pointer != null) free(pointer)
}

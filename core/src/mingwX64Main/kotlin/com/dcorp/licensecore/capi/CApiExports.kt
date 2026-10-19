@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.dcorp.licensecore.capi

import com.dcorp.licensecore.LicenseClient
import com.dcorp.licensecore.LicenseClientFactory
import com.dcorp.licensecore.LicenseConfig
import com.dcorp.licensecore.platform.PlatformContext
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.CName

/**
 * Flat C ABI for `licensecore.dll` (not the default K/N struct-of-function-pointers export - see
 * Phase 5's plan, which found the default unusable from plain P/Invoke). Every `lc_*` body is
 * wrapped so no Kotlin exception ever crosses the C boundary (an uncaught one would terminate the
 * host process); on an internal failure functions return an `{"state":"ERROR",...}` JSON string
 * (never a crash) and [lc_last_error] gets the detail.
 *
 * `lc_handle` = a [StableRef] to [ClientHandle], disposed by [lc_destroy]. Never `FreeLibrary`
 * this DLL - the Kotlin/Native runtime cannot be unloaded.
 */
private class ClientHandle(val client: LicenseClient) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@ThreadLocal
private var lastError: String = ""

@OptIn(ExperimentalForeignApi::class)
@CName("lc_create")
public fun lcCreate(configJson: CPointer<ByteVar>?): COpaquePointer? = try {
    val json = configJson.toKotlinStringOrNull() ?: error("config_json is null")
    val dto = capiJson.decodeFromString<CApiConfigDto>(json)
    val config = LicenseConfig(
        baseUrl = dto.baseUrl,
        orgId = dto.orgId,
        appSalt = dto.appSalt,
        trustedSigningKeys = dto.trustedSigningKeys,
        connectTimeoutMillis = dto.connectTimeoutMillis,
        requestTimeoutMillis = dto.requestTimeoutMillis,
    )
    val client = LicenseClientFactory.create(PlatformContext(), config)
    StableRef.create(ClientHandle(client)).asCPointer()
} catch (e: Throwable) {
    lastError = e.message ?: e.toString()
    null
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_activate")
public fun lcActivate(handle: COpaquePointer?, licenseKey: CPointer<ByteVar>?): CPointer<ByteVar>? = guarded(handle) { client ->
    val key = licenseKey.toKotlinStringOrNull() ?: error("license_key is null")
    runBlocking { client.activate(key) }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_activate_for_store")
public fun lcActivateForStore(handle: COpaquePointer?, requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? = guarded(handle) { client ->
    val json = requestJson.toKotlinStringOrNull() ?: error("request_json is null")
    val req = capiJson.decodeFromString<CApiActivateForStoreRequestDto>(json)
    runBlocking { client.activateForStore(req.storeId, req.appId, req.accProductId, req.deviceName) }
}

/** Same as [lcActivate] but with an optional caller-chosen device label (added after v1 of the ABI
 *  shipped - kept as a separate export rather than changing [lcActivate]'s signature so existing
 *  consumers built against ABI v1 keep working unmodified). */
@OptIn(ExperimentalForeignApi::class)
@CName("lc_activate_with_device_name")
public fun lcActivateWithDeviceName(handle: COpaquePointer?, licenseKey: CPointer<ByteVar>?, deviceName: CPointer<ByteVar>?): CPointer<ByteVar>? = guarded(handle) { client ->
    val key = licenseKey.toKotlinStringOrNull() ?: error("license_key is null")
    val name = deviceName.toKotlinStringOrNull()
    runBlocking { client.activate(key, name) }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_import_offline_token")
public fun lcImportOfflineToken(handle: COpaquePointer?, jwt: CPointer<ByteVar>?): CPointer<ByteVar>? = guarded(handle) { client ->
    val token = jwt.toKotlinStringOrNull() ?: error("jwt is null")
    runBlocking { client.importOfflineToken(token) }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_check_now")
public fun lcCheckNow(handle: COpaquePointer?): CPointer<ByteVar>? = guarded(handle) { client ->
    runBlocking { client.checkNow() }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_get_state")
public fun lcGetState(handle: COpaquePointer?): CPointer<ByteVar>? = guarded(handle) { client ->
    client.state.value
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_deactivate")
public fun lcDeactivate(handle: COpaquePointer?): CPointer<ByteVar>? = guarded(handle) { client ->
    runBlocking { client.deactivate() }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_device_id")
public fun lcDeviceId(handle: COpaquePointer?): CPointer<ByteVar>? {
    val h = handle?.asStableRef<ClientHandle>()?.get() ?: return errorCApiJson("INVALID_HANDLE", "null handle").toCStringMalloc()
    return try {
        runBlocking { h.client.deviceId() }.toCStringMalloc()
    } catch (e: Throwable) {
        lastError = e.message ?: e.toString()
        errorCApiJson("INTERNAL", lastError).toCStringMalloc()
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_start")
public fun lcStart(handle: COpaquePointer?): Int {
    val h = handle?.asStableRef<ClientHandle>()?.get() ?: return 0
    return try {
        h.client.start(h.scope)
        1
    } catch (e: Throwable) {
        lastError = e.message ?: e.toString()
        0
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_stop")
public fun lcStop(handle: COpaquePointer?): Int {
    val h = handle?.asStableRef<ClientHandle>()?.get() ?: return 0
    h.client.stop()
    return 1
}

@CName("lc_last_error")
public fun lcLastError(): CPointer<ByteVar> = lastError.toCStringMalloc()

@OptIn(ExperimentalForeignApi::class)
@CName("lc_free_string")
public fun lcFreeString(s: CPointer<ByteVar>?) {
    freeCString(s)
}

@OptIn(ExperimentalForeignApi::class)
@CName("lc_destroy")
public fun lcDestroy(handle: COpaquePointer?) {
    handle?.asStableRef<ClientHandle>()?.let {
        it.get().client.stop()
        it.dispose()
    }
}

@CName("lc_abi_version")
public fun lcAbiVersion(): Int = 1

@OptIn(ExperimentalForeignApi::class)
private inline fun guarded(handle: COpaquePointer?, block: (LicenseClient) -> com.dcorp.licensecore.LicenseState): CPointer<ByteVar> {
    val h = handle?.asStableRef<ClientHandle>()?.get()
        ?: return errorCApiJson("INVALID_HANDLE", "null handle").toCStringMalloc()
    return try {
        block(h.client).toCApiJson().toCStringMalloc()
    } catch (e: Throwable) {
        lastError = e.message ?: e.toString()
        errorCApiJson("INTERNAL", lastError).toCStringMalloc()
    }
}

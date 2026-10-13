package com.dcorp.licensecore.platform

/** Suspend [SecureStorage] facade over the synchronous [DpapiFileStore] (DPAPI calls and file I/O
 *  are quick and local; this just keeps the interface consistent with the other platforms). */
internal class DpapiSecureStorage(private val store: DpapiFileStore) : SecureStorage {
    override suspend fun get(key: String): ByteArray? = store.read("$key.bin")
    override suspend fun put(key: String, bytes: ByteArray) = store.write("$key.bin", bytes)
    override suspend fun remove(key: String) = store.delete("$key.bin")
}

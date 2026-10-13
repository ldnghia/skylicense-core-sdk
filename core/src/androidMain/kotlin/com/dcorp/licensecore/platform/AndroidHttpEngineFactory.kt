package com.dcorp.licensecore.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner

/** Pins when [certificatePins] is non-empty (host -> SHA-256 SPKI pins); unpinned otherwise.
 *  UNVERIFIED like the rest of Phase 3 - no Android SDK available to build/run this. */
internal object AndroidHttpEngineFactory {
    fun create(certificatePins: Map<String, List<String>>): HttpClientEngine = OkHttp.create {
        if (certificatePins.isNotEmpty()) {
            val builder = CertificatePinner.Builder()
            for ((host, pins) in certificatePins) {
                for (pin in pins) builder.add(host, "sha256/$pin")
            }
            config { certificatePinner(builder.build()) }
        }
    }
}

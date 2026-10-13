package com.dcorp.licensecore.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.certificates.CertificatePinner

/** Pins when [certificatePins] is non-empty (host -> SHA-256 SPKI pins); unpinned otherwise.
 *  UNVERIFIED like the rest of Phase 4 - no macOS host available to build/run this. */
internal object DarwinHttpEngineFactory {
    fun create(certificatePins: Map<String, List<String>>): HttpClientEngine = Darwin.create {
        if (certificatePins.isNotEmpty()) {
            val builder = CertificatePinner.Builder()
            for ((host, pins) in certificatePins) {
                for (pin in pins) builder.add(host, "sha256/$pin")
            }
            handleChallenge(builder.build())
        }
    }
}

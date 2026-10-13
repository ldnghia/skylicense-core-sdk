package com.dcorp.licensecore.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.winhttp.WinHttp

/**
 * No certificate pinning on Windows in v1 - confirmed, not guessed: WinHttp exposes no pinning
 * hook, and a real spike in this session found Ktor's Curl engine (the plan's option (a))
 * doesn't expose `CURLOPT_PINNEDPUBLICKEY` either, only `sslVerify`/`caInfo`/`caPath` - achieving
 * pinning there would need direct libcurl cinterop, not a config change. See
 * `docs/security-threat-model.md` for the full rationale and why this is acceptable
 * (pinning is defense-in-depth; every attack it would block is already bounded by token exp).
 */
internal object WinHttpEngineFactory {
    fun create(certificatePins: Map<String, List<String>>): HttpClientEngine {
        if (certificatePins.isNotEmpty()) {
            println("licensecore: certificatePins configured but Windows has no pinning support in v1 - ignoring. See docs/security-threat-model.md.")
        }
        return WinHttp.create {}
    }
}

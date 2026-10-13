package com.dcorp.licensecore.api

import com.dcorp.licensecore.LicenseConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object LicenseHttpClientFactory {
    fun create(engine: HttpClientEngine, config: LicenseConfig): HttpClient {
        return HttpClient(engine) {
            expectSuccess = false

            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }

            install(HttpTimeout) {
                connectTimeoutMillis = config.connectTimeoutMillis
                requestTimeoutMillis = config.requestTimeoutMillis
            }

            install(HttpRequestRetry) {
                maxRetries = 3
                retryIf { _, response ->
                    response.status.value in 500..599 ||
                        response.status == HttpStatusCode.TooManyRequests ||
                        response.status == HttpStatusCode.RequestTimeout
                }
                retryOnExceptionIf { _, cause -> cause !is kotlinx.coroutines.CancellationException }
                exponentialDelay()
            }
        }
    }
}

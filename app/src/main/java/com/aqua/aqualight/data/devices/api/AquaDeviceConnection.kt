package com.aqua.aqualight.data.devices.api

data class AquaDeviceConnection(
    val host: String,
    val port: Int = DEFAULT_HTTP_PORT,
    val useHttps: Boolean = false,
    val apiToken: String = "",
    val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) {
    val baseUrl: String
        get() {
            val scheme = if (useHttps) {
                "https"
            } else {
                "http"
            }

            return "$scheme://$host:$port"
        }

    companion object {
        const val DEFAULT_HTTP_PORT = 80
        const val DEFAULT_TIMEOUT_MILLIS = 3_500
    }
}

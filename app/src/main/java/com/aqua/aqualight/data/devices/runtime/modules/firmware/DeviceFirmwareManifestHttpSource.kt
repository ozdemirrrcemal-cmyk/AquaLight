package com.aqua.aqualight.data.devices.runtime.modules.firmware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

open class DeviceFirmwareManifestHttpSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val signatureVerifier: DeviceFirmwareManifestSignatureVerifier =
        DeviceFirmwareManifestSignatureVerifier()
) {
    open suspend fun load(url: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val sourceUrl = requireOfficialFirmwareManifestUrl(url)

            val text = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(sourceUrl)
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.use { value ->
                    require(value.isSuccessful) { "Manifest request failed: HTTP ${value.code}." }
                    value.body?.string() ?: error("Manifest response body is empty.")
                }
            }

            signatureVerifier.verifyAndParse(text).getOrThrow()
        }
    }
}

internal fun requireOfficialFirmwareManifestUrl(url: String): String {
    val sourceUrl = url.trim()
    require(sourceUrl.startsWith("https://")) { "Manifest URL must use HTTPS." }
    require(
        sourceUrl.startsWith(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX) ||
            sourceUrl.startsWith(
                DeviceFirmwareRuntimeContract.OFFICIAL_LATEST_RELEASE_URL_PREFIX
            )
    ) {
        "Manifest URL must use the AquaLight release source."
    }
    require(sourceUrl.endsWith(".json")) { "Manifest URL must be a JSON asset." }
    return sourceUrl
}

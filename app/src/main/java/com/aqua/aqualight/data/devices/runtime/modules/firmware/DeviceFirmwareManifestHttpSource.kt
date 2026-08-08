package com.aqua.aqualight.data.devices.runtime.modules.firmware

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A typed, non-error signal that no official latest OTA manifest has been published yet. */
internal class DeviceFirmwareManifestNotPublishedException(
    val statusCode: Int
) : IOException("No official AquaLight OTA manifest is published yet (HTTP $statusCode).")

/** Preserves non-success HTTP status without conflating it with an unpublished release. */
internal class DeviceFirmwareManifestHttpException(
    val statusCode: Int
) : IOException("Manifest request failed: HTTP $statusCode.")

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
                    when {
                        value.code == HTTP_NOT_FOUND ->
                            throw DeviceFirmwareManifestNotPublishedException(value.code)
                        !value.isSuccessful ->
                            throw DeviceFirmwareManifestHttpException(value.code)
                        else -> value.body?.string()
                            ?: error("Manifest response body is empty.")
                    }
                }
            }

            signatureVerifier.verifyAndParse(text).getOrThrow()
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
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

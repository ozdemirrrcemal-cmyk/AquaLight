package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A typed, non-error signal that this product's official channel manifest is not published. */
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

            val manifest = signatureVerifier.verifyAndParse(text).getOrThrow()
            requireFirmwareManifestMatchesUrl(sourceUrl, manifest)
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

internal fun requireFirmwareManifestMatchesUrl(
    url: String,
    manifest: DeviceFirmwareManifest
): DeviceFirmwareManifest {
    val sourceUrl = requireOfficialFirmwareManifestUrl(url)
    val artifactEnvironment = manifest.artifacts.singleOrNull()?.env
        ?: throw IllegalArgumentException(
            "Product-scoped OTA manifest must contain exactly one artifact."
        )
    val channelPath = sourceUrl.removePrefix(
        DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX
    ).takeIf { it != sourceUrl }
    val channelMatch = channelPath?.let(PRODUCT_CHANNEL_MANIFEST_PATH::matchEntire)
    if (channelMatch != null) {
        require(channelMatch.groupValues[1] == manifest.channel) {
            "OTA channel manifest URL and signed manifest channel differ."
        }
        require(channelMatch.groupValues[2] == artifactEnvironment) {
            "OTA channel manifest URL and signed manifest product differ."
        }
        return manifest
    }

    val releasePath = sourceUrl.removePrefix(
        DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX
    )
    val immutableMatch = requireNotNull(PRODUCT_VERSION_MANIFEST_PATH.matchEntire(releasePath)) {
        "OTA immutable manifest URL is malformed."
    }
    require(immutableMatch.groupValues[1] == artifactEnvironment) {
        "OTA immutable manifest URL and signed manifest product differ."
    }
    require("${immutableMatch.groupValues[1]}-${immutableMatch.groupValues[2]}" == manifest.tag) {
        "OTA immutable manifest URL and signed manifest tag differ."
    }
    return manifest
}

internal fun requireOfficialFirmwareManifestUrl(url: String): String {
    val sourceUrl = url.trim()
    require(sourceUrl == url) { "Manifest URL must not contain surrounding whitespace." }
    require(sourceUrl.startsWith("https://")) { "Manifest URL must use HTTPS." }
    val isChannelManifest = sourceUrl.startsWith(
        DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX
    )
    val isImmutableManifest = sourceUrl.startsWith(
        DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX
    )
    require(isChannelManifest || isImmutableManifest) {
        "Manifest URL must use an official AquaLight OTA source."
    }
    val channelMatch = if (isChannelManifest) {
        PRODUCT_CHANNEL_MANIFEST_PATH.matchEntire(
            sourceUrl.removePrefix(
                DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX
            )
        )
    } else {
        null
    }
    val immutableMatch = if (isImmutableManifest) {
        PRODUCT_VERSION_MANIFEST_PATH.matchEntire(
            sourceUrl.removePrefix(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX)
        )
    } else {
        null
    }
    val environment = channelMatch?.groupValues?.get(2)
        ?: immutableMatch?.groupValues?.get(1)
    require(
        environment != null &&
            environment in DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS
    ) {
        "Manifest URL must identify one exact commercial product release."
    }
    return sourceUrl
}

private val PRODUCT_CHANNEL_MANIFEST_PATH = Regex(
    "^(stable|beta|dev)/([a-z0-9_]+)/manifest-\\1\\.json$"
)

private val PRODUCT_VERSION_MANIFEST_PATH = Regex(
    "^([a-z0-9_]+)-(v(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*))/" +
        "manifest-\\1-\\2\\.json$"
)

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

open class DeviceFirmwareManifestHttpSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val signatureVerifier: DeviceFirmwareManifestSignatureVerifier =
        DeviceFirmwareManifestSignatureVerifier()
) {
    open suspend fun load(url: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val location = requireOfficialFirmwareChannelManifestUrl(url)

            val text = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(location.url)
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.use { value ->
                    require(value.isSuccessful) { "Manifest request failed: HTTP ${value.code}." }
                    value.body?.string() ?: error("Manifest response body is empty.")
                }
            }

            val manifest = signatureVerifier.verifyAndParse(text).getOrThrow()
            require(manifest.channel == location.channel.wireValue) {
                "Signed OTA manifest channel differs from its official channel path."
            }
            require(manifest.artifacts.size == 1) {
                "A product OTA channel manifest must contain exactly one artifact."
            }
            require(manifest.artifacts.single().env == location.environment) {
                "Signed OTA manifest product differs from its official channel path."
            }
            manifest
        }
    }
}

internal data class OfficialFirmwareChannelManifestLocation(
    val url: String,
    val channel: DeviceFirmwareChannel,
    val environment: String
)

internal fun requireOfficialFirmwareManifestUrl(url: String): String =
    requireOfficialFirmwareChannelManifestUrl(url).url

internal fun requireOfficialFirmwareChannelManifestUrl(
    url: String
): OfficialFirmwareChannelManifestLocation {
    val sourceUrl = url.trim()
    val parsed = sourceUrl.toHttpUrl()
    require(parsed.scheme == "https") { "Manifest URL must use HTTPS." }
    require(parsed.host == OFFICIAL_CHANNEL_HOST) {
        "Manifest URL must use the AquaLight product channel source."
    }
    require(parsed.port == HTTPS_PORT && parsed.username.isEmpty() && parsed.password.isEmpty()) {
        "Manifest URL authority is invalid."
    }
    require(parsed.query == null && parsed.fragment == null) {
        "Manifest URL must not contain a query or fragment."
    }

    val segments = parsed.pathSegments
    require(segments.size == EXPECTED_PATH_SEGMENT_COUNT) {
        "Manifest URL path must identify one exact AquaLight product channel."
    }
    require(
        segments[OWNER_SEGMENT_INDEX] == OFFICIAL_OWNER &&
            segments[REPOSITORY_SEGMENT_INDEX] == OFFICIAL_REPOSITORY
    ) {
        "Manifest URL repository is not the official AquaLight OTA repository."
    }
    require(
        segments[BRANCH_SEGMENT_INDEX] == OFFICIAL_BRANCH &&
            segments[CHANNELS_DIRECTORY_SEGMENT_INDEX] == CHANNELS_DIRECTORY
    ) {
        "Manifest URL must use the official product channel namespace."
    }

    val channel = DeviceFirmwareChannel.values().singleOrNull { candidate ->
        candidate.wireValue == segments[CHANNEL_SEGMENT_INDEX]
    } ?: throw IllegalArgumentException("Manifest URL contains an unsupported OTA channel.")

    val filename = segments[FILENAME_SEGMENT_INDEX]
    require(filename.endsWith(JSON_SUFFIX)) { "Manifest URL must be a JSON asset." }
    val environment = filename.removeSuffix(JSON_SUFFIX)
    require(ENVIRONMENT_PATTERN.matches(environment)) {
        "Manifest URL contains an invalid product environment."
    }

    val expectedPath =
        "/$OFFICIAL_OWNER/$OFFICIAL_REPOSITORY/$OFFICIAL_BRANCH/$CHANNELS_DIRECTORY/" +
            "${channel.wireValue}/$environment$JSON_SUFFIX"
    require(parsed.encodedPath == expectedPath) {
        "Manifest URL path is not canonical."
    }

    return OfficialFirmwareChannelManifestLocation(
        url = parsed.toString(),
        channel = channel,
        environment = environment
    )
}

private const val OFFICIAL_CHANNEL_HOST = "raw.githubusercontent.com"
private const val OFFICIAL_OWNER = "ozdemirrrcemal-cmyk"
private const val OFFICIAL_REPOSITORY = "AquaLight-OTA-Releases"
private const val OFFICIAL_BRANCH = "main"
private const val CHANNELS_DIRECTORY = "channels"
private const val HTTPS_PORT = 443
private const val EXPECTED_PATH_SEGMENT_COUNT = 6
private const val OWNER_SEGMENT_INDEX = 0
private const val REPOSITORY_SEGMENT_INDEX = 1
private const val BRANCH_SEGMENT_INDEX = 2
private const val CHANNELS_DIRECTORY_SEGMENT_INDEX = 3
private const val CHANNEL_SEGMENT_INDEX = 4
private const val FILENAME_SEGMENT_INDEX = 5
private const val JSON_SUFFIX = ".json"
private val ENVIRONMENT_PATTERN = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")

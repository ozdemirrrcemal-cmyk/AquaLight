package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Locale
import org.json.JSONObject

enum class DeviceFirmwareOtaPhase(
    val wireValue: String
) {
    IDLE("idle"),
    STARTING("starting"),
    SAFE_MODE("safeMode"),
    DOWNLOADING("downloading"),
    WRITING("writing"),
    VERIFYING("verifying"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    UNKNOWN("unknown");

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == FAILED

    companion object {
        private val exactValues = entries
            .filterNot { phase -> phase == UNKNOWN }
            .associateBy(DeviceFirmwareOtaPhase::wireValue)

        fun fromWireExact(value: String): DeviceFirmwareOtaPhase? = exactValues[value]

        fun fromWire(value: String?): DeviceFirmwareOtaPhase = when (value?.trim()) {
            IDLE.wireValue -> IDLE
            STARTING.wireValue -> STARTING
            SAFE_MODE.wireValue -> SAFE_MODE
            DOWNLOADING.wireValue -> DOWNLOADING
            WRITING.wireValue -> WRITING
            VERIFYING.wireValue -> VERIFYING
            SUCCEEDED.wireValue -> SUCCEEDED
            FAILED.wireValue -> FAILED
            else -> UNKNOWN
        }
    }
}

data class DeviceFirmwarePartitionInfo(
    val present: Boolean = false,
    val label: String = "",
    val address: Long = 0L,
    val size: Long = 0L,
    val type: Int = 0,
    val subtype: Int = 0
)

data class DeviceFirmwarePartitionStatus(
    val running: DeviceFirmwarePartitionInfo = DeviceFirmwarePartitionInfo(),
    val boot: DeviceFirmwarePartitionInfo = DeviceFirmwarePartitionInfo(),
    val nextUpdate: DeviceFirmwarePartitionInfo = DeviceFirmwarePartitionInfo(),
    val bootMatchesRunning: Boolean = false,
    val runningState: String = "",
    val runningStateCode: Int = 0,
    val stateReadOk: Boolean = false,
    val stateReadError: Int = 0
)

data class DeviceFirmwareOtaSnapshot(
    val phase: DeviceFirmwareOtaPhase = DeviceFirmwareOtaPhase.IDLE,
    val phaseRaw: String = DeviceFirmwareOtaPhase.IDLE.wireValue,
    val active: Boolean = false,
    val completed: Boolean = false,
    val success: Boolean = false,
    val failed: Boolean = false,
    val restartRequired: Boolean = false,
    val restartScheduled: Boolean = false,
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L,
    val bytesWritten: Long = 0L,
    val contentLength: Long = 0L,
    val progressPermille: Int = 0,
    val progressPercent: Double = 0.0,
    val targetVersion: String = "",
    val sha256Expected: String = "",
    val sha256Actual: String = "",
    val lastError: String = "",
    val lastErrorField: String = "",
    val urlScheme: String = "",
    val httpStatus: Int = 0
)

data class DeviceFirmwareStatus(
    val version: String = "",
    val build: String = "",
    val hardwareRevision: String = "",
    val sdkVersion: String = "",
    val uptimeMs: Long = 0L,
    val productKey: String = "",
    val productId: String = "",
    val family: String = "",
    val model: String = "",
    val displayName: String = "",
    val skuCode: String = "",
    val flashChipSize: Long = 0L,
    val flashSketchSize: Long = 0L,
    val flashFreeSketchSpace: Long = 0L,
    val partition: DeviceFirmwarePartitionStatus = DeviceFirmwarePartitionStatus(),
    val otaSupported: Boolean = false,
    val otaTransport: String = "",
    val otaBinaryTransfer: String = "",
    val otaProgressEvent: String = "",
    val otaCompletedEvent: String = "",
    val otaStartCommand: String = "",
    val otaStatusCommand: String = "",
    val ota: DeviceFirmwareOtaSnapshot = DeviceFirmwareOtaSnapshot()
)

data class DeviceFirmwareOtaStartPayload(
    val url: String,
    val version: String,
    val sha256: String,
    val expectedSize: Int,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String,
    val applyNow: Boolean = true
) {
    init {
        require(url.isNotBlank()) { "OTA url must not be blank." }
        require(url.length <= DeviceFirmwareRuntimeContract.Limit.MAX_URL_LENGTH) {
            "OTA url is longer than firmware limit."
        }
        require(url.startsWith(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX)) {
            "OTA url must target official AquaLight release repository."
        }
        require(url.startsWith("https://")) { "OTA url must use HTTPS." }
        require(version.isExactFirmwareVersion()) { "OTA version must use exact X.Y.Z format." }
        require(sha256.isSha256Hex()) { "sha256 must be 64 hex characters." }
        require(expectedSize > 0) { "expectedSize must be greater than zero." }
        require(productKey.isNotBlank()) { "productKey must not be blank." }
        require(productId.isNotBlank()) { "productId must not be blank." }
        require(model.isNotBlank()) { "model must not be blank." }
        require(hardwareRevision.isNotBlank()) { "hardwareRevision must not be blank." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceFirmwareRuntimeContract.Field.URL, url)
            .put(DeviceFirmwareRuntimeContract.Field.VERSION, version)
            .put(DeviceFirmwareRuntimeContract.Field.SHA256, sha256.lowercase())
            .put(DeviceFirmwareRuntimeContract.Field.EXPECTED_SIZE, expectedSize)
            .put(DeviceFirmwareRuntimeContract.Field.APPLY_NOW, applyNow)
            .put(DeviceFirmwareRuntimeContract.Field.PRODUCT_KEY, productKey)
            .put(DeviceFirmwareRuntimeContract.Field.PRODUCT_ID, productId)
            .put(DeviceFirmwareRuntimeContract.Field.MODEL, model)
            .put(DeviceFirmwareRuntimeContract.Field.HARDWARE_REVISION, hardwareRevision)
    }
}

data class DeviceFirmwareOtaStartRequestEcho(
    val urlScheme: String,
    val version: String,
    val expectedSize: Int,
    val applyNow: Boolean,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String
)

data class DeviceFirmwareOtaStartAccepted(
    val accepted: Boolean,
    val request: DeviceFirmwareOtaStartRequestEcho?,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareOtaClearResult(
    val cleared: Boolean,
    val previous: DeviceFirmwareOtaSnapshot,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareManifest(
    val schema: String,
    val brand: String,
    val channel: String,
    val version: String,
    val tag: String,
    val releaseRepo: String,
    val generatedAt: String,
    val platform: DeviceFirmwareManifestPlatform,
    val releaseNotes: DeviceFirmwareReleaseNotes,
    val artifacts: List<DeviceFirmwareManifestArtifact>,
    val signature: DeviceFirmwareManifestSignature
) {
    val isSupportedSchema: Boolean
        get() = schema == DeviceFirmwareRuntimeContract.Manifest.SCHEMA &&
            brand == DeviceFirmwareRuntimeContract.Manifest.BRAND &&
            releaseRepo == DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY
}

data class DeviceFirmwareManifestPlatform(
    val framework: String,
    val core: String,
    val platform: String,
    val partitionTable: String,
    val normalOtaAssetType: String
)

data class DeviceFirmwareManifestSignature(
    val scheme: String,
    val keyId: String,
    val payloadHash: String,
    val value: String
)

data class DeviceFirmwareReleaseNoteItem(
    val tr: String,
    val en: String
) {
    fun text(locale: String): String = when (locale) {
        DeviceFirmwareRuntimeContract.ReleaseNotes.TURKISH -> tr
        DeviceFirmwareRuntimeContract.ReleaseNotes.ENGLISH -> en
        else -> error("Unsupported OTA release-note locale: $locale")
    }
}

data class DeviceFirmwareReleaseNotes(
    val schema: String,
    val defaultLocale: String,
    val items: List<DeviceFirmwareReleaseNoteItem>
) {
    init {
        require(schema == DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA) {
            "Unsupported OTA release-notes schema."
        }
        require(defaultLocale == DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE) {
            "OTA release-notes defaultLocale differs from firmware."
        }
        require(items.isNotEmpty()) { "OTA release notes must contain at least one item." }
        require(items.size <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS) {
            "OTA release notes exceed the firmware item limit."
        }
    }

    fun resolve(preferredLocaleTags: List<String>): DeviceFirmwareReleaseContent {
        val locale = preferredLocaleTags
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { tag -> tag.lowercase(Locale.ROOT).substringBefore('-') }
            .firstOrNull { language ->
                language == DeviceFirmwareRuntimeContract.ReleaseNotes.TURKISH ||
                    language == DeviceFirmwareRuntimeContract.ReleaseNotes.ENGLISH
            }
            ?: defaultLocale
        return DeviceFirmwareReleaseContent(
            localeTag = locale,
            title = "",
            summary = "",
            changes = items.map { item -> item.text(locale) },
            warnings = emptyList(),
            mandatory = false
        )
    }
}

data class DeviceFirmwareManifestArtifact(
    val env: String,
    val product: DeviceFirmwareManifestProduct,
    val compatibility: DeviceFirmwareCompatibility,
    val firmware: DeviceFirmwareAsset,
    val factory: DeviceFirmwareFactoryAsset?
)

data class DeviceFirmwareManifestProduct(
    val productKey: String,
    val productId: String,
    val brand: String,
    val family: String,
    val line: String,
    val model: String,
    val displayName: String,
    val skuCode: String,
    val hardwareRevision: String,
    val capabilities: DeviceCapabilities,
    val limits: DeviceLimits
)

data class DeviceFirmwareCompatibility(
    val productKey: String,
    val productId: String,
    val family: String,
    val line: String,
    val model: String,
    val hardwareRevision: String
)

data class DeviceFirmwareAsset(
    val version: String,
    val filename: String,
    val url: String,
    val sha256: String,
    val size: Int,
    val format: String,
    val otaSlotCompatible: Boolean
)

data class DeviceFirmwareFactoryAsset(
    val filename: String,
    val url: String,
    val sha256: String,
    val size: Int
)

sealed interface DeviceFirmwareAvailability {
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val releaseContent: DeviceFirmwareReleaseContent
    ) : DeviceFirmwareAvailability

    data class UpdateAvailable(
        val plan: DeviceFirmwareUpdatePlan
    ) : DeviceFirmwareAvailability
}

data class DeviceFirmwareUpdatePlan(
    val deviceUid: DeviceUid,
    val currentVersion: String,
    val targetVersion: String,
    val channel: String,
    val env: String,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String,
    val displayName: String,
    val firmware: DeviceFirmwareAsset,
    val payload: DeviceFirmwareOtaStartPayload,
    val runtimeMetadataGeneration: Long = 0L,
    val manifestTag: String = "",
    val releaseContent: DeviceFirmwareReleaseContent = DeviceFirmwareReleaseContent.EMPTY
)

internal fun String.isExactFirmwareVersion(): Boolean =
    exactFirmwareVersionPartsOrNull() != null

internal fun String.exactFirmwareVersionPartsOrNull(): List<Long>? {
    if (!EXACT_FIRMWARE_VERSION_PATTERN.matches(this)) return null
    val values = split('.').map { part ->
        part.toLongOrNull()?.takeIf { value -> value <= UINT32_MAX } ?: return null
    }
    return values.takeIf { it.size == EXACT_FIRMWARE_VERSION_PART_COUNT }
}

private val EXACT_FIRMWARE_VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
private const val EXACT_FIRMWARE_VERSION_PART_COUNT = 3
private const val UINT32_MAX = 4_294_967_295L

internal fun String.isSha256Hex(): Boolean {
    return trim().length == DeviceFirmwareRuntimeContract.Limit.SHA256_HEX_LENGTH &&
        trim().matches(Regex("(?i)^[0-9a-f]+$"))
}

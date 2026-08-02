package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
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

data class DeviceFirmwareRuntimeInfo(
    val transport: String = "",
    val wsSchema: String = "",
    val wsProtocolVersion: Int = 0,
    val readOnly: Boolean = false
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
    val allowInsecureHttp: Boolean = false,
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
    val ota: DeviceFirmwareOtaSnapshot = DeviceFirmwareOtaSnapshot(),
    val runtime: DeviceFirmwareRuntimeInfo = DeviceFirmwareRuntimeInfo()
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
    val applyNow: Boolean = true,
    val allowInsecureHttp: Boolean = false
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
        require(version.isNotBlank()) { "OTA version must not be blank." }
        require(sha256.isSha256Hex()) { "sha256 must be 64 hex characters." }
        require(expectedSize > 0) { "expectedSize must be greater than zero." }
        require(productKey.isNotBlank()) { "productKey must not be blank." }
        require(productId.isNotBlank()) { "productId must not be blank." }
        require(model.isNotBlank()) { "model must not be blank." }
        require(hardwareRevision.isNotBlank()) { "hardwareRevision must not be blank." }
        require(!allowInsecureHttp) { "allowInsecureHttp must stay false in production Android." }
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
            .put(DeviceFirmwareRuntimeContract.Field.ALLOW_INSECURE_HTTP, false)
    }
}

data class DeviceFirmwareOtaStartRequestEcho(
    val urlScheme: String,
    val version: String,
    val expectedSize: Int,
    val applyNow: Boolean,
    val allowInsecureHttp: Boolean,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String
)

data class DeviceFirmwareOtaStartAccepted(
    val operation: String,
    val accepted: Boolean,
    val runtimeTransport: String,
    val command: String,
    val binaryTransfer: String,
    val event: String,
    val progressEvent: String,
    val completedEvent: String,
    val request: DeviceFirmwareOtaStartRequestEcho,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareOtaStatusResponse(
    val operation: String,
    val runtimeTransport: String,
    val command: String,
    val binaryTransfer: String,
    val progressEvent: String,
    val completedEvent: String,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareOtaEvent(
    val completed: Boolean,
    val success: Boolean,
    val failed: Boolean,
    val runtimeTransport: String,
    val binaryTransfer: String,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareOtaClearPrevious(
    val phase: DeviceFirmwareOtaPhase,
    val phaseRaw: String,
    val restartRequired: Boolean,
    val restartScheduled: Boolean,
    val targetVersion: String,
    val lastError: String,
    val lastErrorField: String
)

data class DeviceFirmwareOtaClearResult(
    val operation: String,
    val cleared: Boolean,
    val runtimeTransport: String,
    val command: String,
    val previous: DeviceFirmwareOtaClearPrevious,
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
    val artifacts: List<DeviceFirmwareManifestArtifact>,
    val signature: DeviceFirmwareManifestSignature,
    val releaseNotes: DeviceFirmwareReleaseNotes
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
)

data class DeviceFirmwareReleaseNotes(
    val schema: String,
    val defaultLocale: String,
    val items: List<DeviceFirmwareReleaseNoteItem>
) {
    init {
        require(schema == DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA) {
            "Unsupported OTA release-notes schema."
        }
        require(defaultLocale in SUPPORTED_LOCALES) {
            "OTA release notes defaultLocale must be tr or en."
        }
        require(items.isNotEmpty()) { "OTA release notes must contain at least one item." }
    }

    fun resolve(preferredLocaleTags: List<String>): DeviceFirmwareReleaseContent {
        val locale = preferredLocaleTags
            .asSequence()
            .map { tag -> tag.substringBefore('-').lowercase(Locale.ROOT) }
            .firstOrNull(SUPPORTED_LOCALES::contains)
            ?: defaultLocale
        val localizedItems = items.map { item ->
            when (locale) {
                DeviceFirmwareRuntimeContract.Manifest.TURKISH -> item.tr
                else -> item.en
            }
        }
        return DeviceFirmwareReleaseContent(
            localeTag = locale,
            items = localizedItems
        )
    }

    private companion object {
        val SUPPORTED_LOCALES = setOf(
            DeviceFirmwareRuntimeContract.Manifest.TURKISH,
            DeviceFirmwareRuntimeContract.Manifest.ENGLISH
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
    val capabilities: DeviceFirmwareManifestCapabilities,
    val limits: DeviceFirmwareManifestLimits
)

data class DeviceFirmwareManifestCapabilities(
    val light: Boolean,
    val manualLight: Boolean,
    val lightProgram: Boolean,
    val lightPresets: Boolean,
    val lightSimulation: Boolean,
    val fan: Boolean,
    val cooling: Boolean,
    val temperature: Boolean,
    val standaloneTimer: Boolean,
    val dosing: Boolean,
    val timeSync: Boolean,
    val ota: Boolean
)

data class DeviceFirmwareManifestLimits(
    val lightChannelCount: Int,
    val fanOutputCount: Int,
    val temperatureSensorCount: Int,
    val timerChannelCount: Int,
    val dosingChannelCount: Int
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
    val firmware: DeviceFirmwareAsset,
    val payload: DeviceFirmwareOtaStartPayload,
    val runtimeMetadataGeneration: Long = 0L,
    val manifestTag: String = "",
    val releaseContent: DeviceFirmwareReleaseContent = DeviceFirmwareReleaseContent.EMPTY
)

internal fun String.isSha256Hex(): Boolean {
    return trim().length == DeviceFirmwareRuntimeContract.Limit.SHA256_HEX_LENGTH &&
        trim().matches(Regex("(?i)^[0-9a-f]+$"))
}

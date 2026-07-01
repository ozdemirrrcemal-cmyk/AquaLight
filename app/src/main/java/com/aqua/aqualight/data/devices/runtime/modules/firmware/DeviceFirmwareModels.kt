package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
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
        fun fromWire(value: String?): DeviceFirmwareOtaPhase {
            return when (value?.trim()) {
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
    val ota: DeviceFirmwareOtaSnapshot = DeviceFirmwareOtaSnapshot()
)

data class DeviceFirmwareOtaStartPayload(
    val url: String,
    val version: String,
    val sha256: String,
    val expectedSize: Int,
    val productKey: String,
    val productId: String,
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
            .put(DeviceFirmwareRuntimeContract.Field.HARDWARE_REVISION, hardwareRevision)
            .put(DeviceFirmwareRuntimeContract.Field.ALLOW_INSECURE_HTTP, false)
    }
}

data class DeviceFirmwareOtaStartAccepted(
    val accepted: Boolean,
    val request: DeviceFirmwareOtaStartPayload?,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareOtaClearResult(
    val cleared: Boolean,
    val previous: DeviceFirmwareOtaSnapshot,
    val ota: DeviceFirmwareOtaSnapshot
)

data class DeviceFirmwareCommandResult(
    val sent: Boolean,
    val module: String = DeviceFirmwareRuntimeContract.MODULE,
    val action: String,
    val messageId: String = "",
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}

data class DeviceFirmwareManifest(
    val schema: String,
    val brand: String,
    val channel: String,
    val version: String,
    val tag: String,
    val releaseRepo: String,
    val generatedAt: String,
    val artifacts: List<DeviceFirmwareManifestArtifact>
) {
    val isSupportedSchema: Boolean
        get() = schema == DeviceFirmwareRuntimeContract.Manifest.SCHEMA &&
            brand == DeviceFirmwareRuntimeContract.Manifest.BRAND
}

data class DeviceFirmwareManifestArtifact(
    val env: String,
    val product: DeviceFirmwareManifestProduct,
    val compatibility: DeviceFirmwareCompatibility,
    val firmware: DeviceFirmwareAsset,
    val factory: DeviceFirmwareAsset? = null
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
    val hardwareRevision: String
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
    val filename: String,
    val url: String,
    val sha256: String,
    val size: Int,
    val format: String = "",
    val otaSlotCompatible: Boolean = false
)

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
    val payload: DeviceFirmwareOtaStartPayload
)

internal fun String.isSha256Hex(): Boolean {
    return trim().length == DeviceFirmwareRuntimeContract.Limit.SHA256_HEX_LENGTH &&
        trim().matches(Regex("(?i)^[0-9a-f]+$"))
}

package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaPhase
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredFiniteDouble
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import com.aqua.aqualight.data.devices.runtime.parsing.requiredStringAllowEmpty
import kotlin.math.abs
import org.json.JSONObject

data class DeviceRuntimeFirmwareCapabilities(
    val transport: String,
    val wsSchema: String,
    val wsProtocolVersion: Int,
    val readOnly: Boolean
)

data class DeviceRuntimeFirmwareStatus(
    val status: DeviceFirmwareStatus,
    val runtime: DeviceRuntimeFirmwareCapabilities
)

object DeviceRuntimeFirmwareStatusParser {

    fun parse(data: JSONObject): Result<DeviceRuntimeFirmwareStatus> = runCatching {
        data.requireExactKeys(FIRMWARE_KEYS, "firmware.status.get.data")
        validateProduct(data.requiredObject("product"))
        validateFlash(data.requiredObject("flash"))
        validatePartition(data.requiredObject("partition"))
        validateOtaRoot(data.requiredObject("ota"))
        val runtime = parseRuntime(data.requiredObject("runtime"))

        data.requiredString("version")
        data.requiredString("build")
        data.requiredString("hardwareRevision")
        data.requiredString("sdkVersion")
        data.requiredNonNegativeLong("uptimeMs")

        DeviceRuntimeFirmwareStatus(
            status = DeviceFirmwareStatusParser.parseFirmwareStatus(data),
            runtime = runtime
        )
    }

    private fun validateProduct(product: JSONObject) {
        product.requireExactKeys(PRODUCT_KEYS, "firmware product")
        PRODUCT_KEYS.forEach { key -> product.requiredString(key) }
    }

    private fun validateFlash(flash: JSONObject) {
        flash.requireExactKeys(FLASH_KEYS, "firmware flash")
        FLASH_KEYS.forEach { key -> flash.requiredNonNegativeLong(key) }
    }

    private fun validatePartition(partition: JSONObject) {
        partition.requireExactKeys(PARTITION_KEYS, "firmware partition")
        validatePartitionInfo(partition.requiredObject("running"), "running")
        validatePartitionInfo(partition.requiredObject("boot"), "boot")
        validatePartitionInfo(partition.requiredObject("nextUpdate"), "nextUpdate")
        partition.requiredBoolean("bootMatchesRunning")
        partition.requiredString("runningState")
        partition.requiredInt("runningStateCode")
        partition.requiredBoolean("stateReadOk")
        partition.requiredInt("stateReadError")
    }

    private fun validatePartitionInfo(info: JSONObject, label: String) {
        val present = info.requiredBoolean("present")
        val expected = if (present) PARTITION_INFO_PRESENT_KEYS else PARTITION_INFO_ABSENT_KEYS
        info.requireExactKeys(expected, "firmware partition $label")
        if (present) {
            info.requiredString("label")
            info.requiredNonNegativeLong("address")
            info.requiredNonNegativeLong("size")
            info.requiredInt("type")
            info.requiredInt("subtype")
        }
    }

    private fun validateOtaRoot(ota: JSONObject) {
        ota.requireExactKeys(OTA_ROOT_KEYS, "firmware ota")
        ota.requiredBoolean("supported")
        require(ota.requiredString("transport") == OTA_CONTROL_TRANSPORT)
        require(ota.requiredString("binaryTransfer") == OTA_BINARY_TRANSFER)
        require(ota.requiredString("progressEvent") == OTA_PROGRESS_EVENT)
        require(ota.requiredString("completedEvent") == OTA_COMPLETED_EVENT)
        require(ota.requiredString("startCommand") == OTA_START_COMMAND)
        require(ota.requiredString("statusCommand") == OTA_STATUS_COMMAND)
        validateOtaSnapshot(ota.requiredObject("status"))
    }

    private fun validateOtaSnapshot(ota: JSONObject) {
        ota.requireExactKeys(OTA_STATUS_KEYS, "firmware ota status")
        val phaseRaw = ota.requiredString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown firmware OTA phase: $phaseRaw"
        }
        val active = ota.requiredBoolean("active")
        val progressPermille = ota.requiredInt("progressPermille")
        val progressPercent = ota.requiredFiniteDouble("progressPercent")
        val bytesWritten = ota.requiredNonNegativeLong("bytesWritten")
        val contentLength = ota.requiredNonNegativeLong("contentLength")
        require(progressPermille in MIN_PROGRESS_PERMILLE..MAX_PROGRESS_PERMILLE)
        require(
            abs(progressPercent - progressPermille / PERMILLE_PER_PERCENT) <=
                PROGRESS_PERCENT_TOLERANCE
        )
        require(contentLength == UNKNOWN_CONTENT_LENGTH || bytesWritten <= contentLength)
        require(!ota.requiredBoolean("allowInsecureHttp"))
        val urlScheme = ota.requiredStringAllowEmpty("urlScheme")
        require(urlScheme.isEmpty() || urlScheme == HTTPS_SCHEME)
        ota.requiredNonNegativeLong("startedAtMs")
        ota.requiredNonNegativeLong("finishedAtMs")
        ota.requiredStringAllowEmpty("targetVersion")
        ota.requiredStringAllowEmpty("sha256Expected")
        ota.requiredStringAllowEmpty("sha256Actual")
        ota.requiredStringAllowEmpty("lastError")
        ota.requiredStringAllowEmpty("lastErrorField")
        ota.requiredInt("httpStatus")

        val activePhase = phase in ACTIVE_OTA_PHASES
        require(active == activePhase)
    }

    private fun parseRuntime(runtime: JSONObject): DeviceRuntimeFirmwareCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "firmware runtime")
        return DeviceRuntimeFirmwareCapabilities(
            transport = runtime.requiredString("transport").also {
                require(it == WEBSOCKET_TRANSPORT)
            },
            wsSchema = runtime.requiredString("wsSchema").also {
                require(it == AqlWsContract.SCHEMA)
            },
            wsProtocolVersion = runtime.requiredInt("wsProtocolVersion").also {
                require(it == AqlWsContract.PROTOCOL_VERSION)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(it) }
        )
    }

    private val FIRMWARE_KEYS = setOf(
        "version", "build", "hardwareRevision", "sdkVersion", "uptimeMs", "product",
        "flash", "partition", "ota", "runtime"
    )
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "family", "model", "displayName", "skuCode"
    )
    private val FLASH_KEYS = setOf("chipSize", "sketchSize", "freeSketchSpace")
    private val PARTITION_KEYS = setOf(
        "running", "boot", "nextUpdate", "bootMatchesRunning", "runningState",
        "runningStateCode", "stateReadOk", "stateReadError"
    )
    private val PARTITION_INFO_PRESENT_KEYS = setOf(
        "present", "label", "address", "size", "type", "subtype"
    )
    private val PARTITION_INFO_ABSENT_KEYS = setOf("present")
    private val OTA_ROOT_KEYS = setOf(
        "supported", "transport", "binaryTransfer", "progressEvent", "completedEvent",
        "startCommand", "statusCommand", "status"
    )
    private val OTA_STATUS_KEYS = setOf(
        "phase", "active", "restartRequired", "restartScheduled", "allowInsecureHttp",
        "startedAtMs", "finishedAtMs", "bytesWritten", "contentLength", "progressPermille",
        "progressPercent", "targetVersion", "sha256Expected", "sha256Actual", "lastError",
        "lastErrorField", "urlScheme", "httpStatus"
    )
    private val RUNTIME_KEYS = setOf(
        "transport", "wsSchema", "wsProtocolVersion", "readOnly"
    )
    private val ACTIVE_OTA_PHASES = setOf(
        DeviceFirmwareOtaPhase.STARTING,
        DeviceFirmwareOtaPhase.SAFE_MODE,
        DeviceFirmwareOtaPhase.DOWNLOADING,
        DeviceFirmwareOtaPhase.WRITING,
        DeviceFirmwareOtaPhase.VERIFYING
    )

    private const val OTA_CONTROL_TRANSPORT = "websocket-control"
    private const val OTA_BINARY_TRANSFER = "firmware-download"
    private const val OTA_PROGRESS_EVENT = "firmware.ota.progress"
    private const val OTA_COMPLETED_EVENT = "firmware.ota.completed"
    private const val OTA_START_COMMAND = "firmware.ota.start"
    private const val OTA_STATUS_COMMAND = "firmware.ota.status"
    private const val WEBSOCKET_TRANSPORT = "websocket"
    private const val HTTPS_SCHEME = "https"
    private const val MIN_PROGRESS_PERMILLE = 0
    private const val MAX_PROGRESS_PERMILLE = 1_000
    private const val PERMILLE_PER_PERCENT = 10.0
    private const val PROGRESS_PERCENT_TOLERANCE = 0.11
    private const val UNKNOWN_CONTENT_LENGTH = 0L
}

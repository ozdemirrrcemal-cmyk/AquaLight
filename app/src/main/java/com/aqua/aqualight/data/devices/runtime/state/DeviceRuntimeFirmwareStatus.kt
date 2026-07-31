package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaPhase
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
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

@Suppress("TooManyFunctions")
object DeviceRuntimeFirmwareStatusParser {

    fun parse(data: JSONObject): Result<DeviceRuntimeFirmwareStatus> = runCatching {
        data.requireExactFirmwareKeys(FIRMWARE_KEYS, "firmware.status.get.data")
        validateProduct(data.requiredFirmwareObject("product"))
        validateFlash(data.requiredFirmwareObject("flash"))
        validatePartition(data.requiredFirmwareObject("partition"))
        validateOtaRoot(data.requiredFirmwareObject("ota"))
        val runtime = parseRuntime(data.requiredFirmwareObject("runtime"))

        require(data.requiredFirmwareString("version").isNotBlank())
        require(data.requiredFirmwareString("build").isNotBlank())
        require(data.requiredFirmwareString("hardwareRevision").isNotBlank())
        require(data.requiredFirmwareString("sdkVersion").isNotBlank())
        data.requiredFirmwareNonNegativeLong("uptimeMs")

        DeviceRuntimeFirmwareStatus(
            status = DeviceFirmwareStatusParser.parseFirmwareStatus(data),
            runtime = runtime
        )
    }

    private fun validateProduct(product: JSONObject) {
        product.requireExactFirmwareKeys(PRODUCT_KEYS, "firmware product")
        PRODUCT_KEYS.forEach { key -> product.requiredFirmwareString(key) }
    }

    private fun validateFlash(flash: JSONObject) {
        flash.requireExactFirmwareKeys(FLASH_KEYS, "firmware flash")
        FLASH_KEYS.forEach { key -> flash.requiredFirmwareNonNegativeLong(key) }
    }

    private fun validatePartition(partition: JSONObject) {
        partition.requireExactFirmwareKeys(PARTITION_KEYS, "firmware partition")
        validatePartitionInfo(partition.requiredFirmwareObject("running"), "running")
        validatePartitionInfo(partition.requiredFirmwareObject("boot"), "boot")
        validatePartitionInfo(partition.requiredFirmwareObject("nextUpdate"), "nextUpdate")
        partition.requiredFirmwareBoolean("bootMatchesRunning")
        partition.requiredFirmwareString("runningState")
        partition.requiredFirmwareInt("runningStateCode")
        partition.requiredFirmwareBoolean("stateReadOk")
        partition.requiredFirmwareInt("stateReadError")
    }

    private fun validatePartitionInfo(info: JSONObject, label: String) {
        val present = info.requiredFirmwareBoolean("present")
        val expected = if (present) PARTITION_INFO_PRESENT_KEYS else setOf("present")
        info.requireExactFirmwareKeys(expected, "firmware partition $label")
        if (present) {
            info.requiredFirmwareString("label")
            info.requiredFirmwareNonNegativeLong("address")
            info.requiredFirmwareNonNegativeLong("size")
            info.requiredFirmwareInt("type")
            info.requiredFirmwareInt("subtype")
        }
    }

    private fun validateOtaRoot(ota: JSONObject) {
        ota.requireExactFirmwareKeys(OTA_ROOT_KEYS, "firmware ota")
        ota.requiredFirmwareBoolean("supported")
        require(ota.requiredFirmwareString("transport") == "websocket-control")
        require(ota.requiredFirmwareString("binaryTransfer") == "firmware-download")
        require(ota.requiredFirmwareString("progressEvent") == "firmware.ota.progress")
        require(ota.requiredFirmwareString("completedEvent") == "firmware.ota.completed")
        require(ota.requiredFirmwareString("startCommand") == "firmware.ota.start")
        require(ota.requiredFirmwareString("statusCommand") == "firmware.ota.status")
        validateOtaSnapshot(ota.requiredFirmwareObject("status"))
    }

    private fun validateOtaSnapshot(ota: JSONObject) {
        ota.requireExactFirmwareKeys(OTA_STATUS_KEYS, "firmware ota status")
        val phaseRaw = ota.requiredFirmwareString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown firmware OTA phase: $phaseRaw"
        }
        val active = ota.requiredFirmwareBoolean("active")
        val progressPermille = ota.requiredFirmwareInt("progressPermille")
        val progressPercent = ota.requiredFirmwareDouble("progressPercent")
        val bytesWritten = ota.requiredFirmwareNonNegativeLong("bytesWritten")
        val contentLength = ota.requiredFirmwareNonNegativeLong("contentLength")
        require(progressPermille in 0..1_000)
        require(abs(progressPercent - progressPermille / 10.0) <= 0.11)
        require(contentLength == 0L || bytesWritten <= contentLength)
        require(!ota.requiredFirmwareBoolean("allowInsecureHttp"))
        val urlScheme = ota.requiredFirmwareStringAllowEmpty("urlScheme")
        require(urlScheme.isEmpty() || urlScheme == "https")
        ota.requiredFirmwareNonNegativeLong("startedAtMs")
        ota.requiredFirmwareNonNegativeLong("finishedAtMs")
        ota.requiredFirmwareStringAllowEmpty("targetVersion")
        ota.requiredFirmwareStringAllowEmpty("sha256Expected")
        ota.requiredFirmwareStringAllowEmpty("sha256Actual")
        ota.requiredFirmwareStringAllowEmpty("lastError")
        ota.requiredFirmwareStringAllowEmpty("lastErrorField")
        ota.requiredFirmwareInt("httpStatus")

        val activePhase = phase in setOf(
            DeviceFirmwareOtaPhase.STARTING,
            DeviceFirmwareOtaPhase.SAFE_MODE,
            DeviceFirmwareOtaPhase.DOWNLOADING,
            DeviceFirmwareOtaPhase.WRITING,
            DeviceFirmwareOtaPhase.VERIFYING
        )
        require(active == activePhase)
    }

    private fun parseRuntime(runtime: JSONObject): DeviceRuntimeFirmwareCapabilities {
        runtime.requireExactFirmwareKeys(RUNTIME_KEYS, "firmware runtime")
        return DeviceRuntimeFirmwareCapabilities(
            transport = runtime.requiredFirmwareString("transport").also {
                require(it == "websocket")
            },
            wsSchema = runtime.requiredFirmwareString("wsSchema").also {
                require(it == AqlWsContract.SCHEMA)
            },
            wsProtocolVersion = runtime.requiredFirmwareInt("wsProtocolVersion").also {
                require(it == AqlWsContract.PROTOCOL_VERSION)
            },
            readOnly = runtime.requiredFirmwareBoolean("readOnly").also { require(it) }
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
}

private fun JSONObject.requireExactFirmwareKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) { "$label keys differ from firmware contract: $actual" }
}

private fun JSONObject.requiredFirmwareObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requiredFirmwareString(key: String): String =
    requiredFirmwareStringAllowEmpty(key).also {
        require(it.isNotEmpty()) { "$key must not be empty." }
    }

private fun JSONObject.requiredFirmwareStringAllowEmpty(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.none(Char::isISOControl))
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace()))
    return value
}

private fun JSONObject.requiredFirmwareBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

private fun JSONObject.requiredFirmwareInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val longValue = number.toLong()
    require(number.toDouble().isFinite() && number.toDouble() == longValue.toDouble())
    require(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    return longValue.toInt()
}

private fun JSONObject.requiredFirmwareNonNegativeLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val longValue = number.toLong()
    require(number.toDouble().isFinite() && number.toDouble() == longValue.toDouble())
    require(longValue >= 0L)
    return longValue
}

private fun JSONObject.requiredFirmwareDouble(key: String): Double =
    (get(key) as? Number)?.toDouble()?.also { require(it.isFinite()) }
        ?: error("$key must be a finite number.")

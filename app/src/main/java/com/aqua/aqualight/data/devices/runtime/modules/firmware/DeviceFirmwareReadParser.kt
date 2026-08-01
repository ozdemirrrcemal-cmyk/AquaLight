package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceFirmwareReadParser {
    fun parseStatus(data: JSONObject): DeviceFirmwareStatus {
        DeviceRuntimeJson.requireExactKeys(data, ROOT_KEYS, ROOT_LABEL)
        require(DeviceRuntimeJson.longValue(data, "uptimeMs") >= 0L)
        DeviceRuntimeJson.stringValue(data, "version")
        DeviceRuntimeJson.stringValue(data, "build")
        DeviceRuntimeJson.stringValue(data, "hardwareRevision")
        DeviceRuntimeJson.stringValue(data, "sdkVersion")
        validateProduct(DeviceRuntimeJson.objectValue(data, "product"))
        validateFlash(DeviceRuntimeJson.objectValue(data, "flash"))
        validatePartition(DeviceRuntimeJson.objectValue(data, "partition"))
        validateOta(DeviceRuntimeJson.objectValue(data, "ota"))
        validateRuntime(DeviceRuntimeJson.objectValue(data, "runtime"))
        return DeviceFirmwareStatusParser.parseFirmwareStatus(data)
    }

    fun parseOtaStatus(data: JSONObject): DeviceFirmwareOtaSnapshot =
        DeviceFirmwareStatusParser.parseOtaStatusResponseExact(data).getOrThrow()

    private fun validateProduct(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, PRODUCT_KEYS, "$ROOT_LABEL.product")
        PRODUCT_KEYS.forEach { key -> DeviceRuntimeJson.stringValue(data, key) }
    }

    private fun validateFlash(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, FLASH_KEYS, "$ROOT_LABEL.flash")
        FLASH_KEYS.forEach { key ->
            require(DeviceRuntimeJson.longValue(data, key) >= 0L) { "$key must not be negative." }
        }
    }

    private fun validatePartition(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, PARTITION_KEYS, "$ROOT_LABEL.partition")
        validatePartitionInfo(DeviceRuntimeJson.objectValue(data, "running"), "running")
        validatePartitionInfo(DeviceRuntimeJson.objectValue(data, "boot"), "boot")
        validatePartitionInfo(DeviceRuntimeJson.objectValue(data, "nextUpdate"), "nextUpdate")
        DeviceRuntimeJson.booleanValue(data, "bootMatchesRunning")
        DeviceRuntimeJson.stringAllowEmpty(data, "runningState")
        DeviceRuntimeJson.intValue(data, "runningStateCode")
        DeviceRuntimeJson.booleanValue(data, "stateReadOk")
        DeviceRuntimeJson.intValue(data, "stateReadError")
    }

    private fun validatePartitionInfo(data: JSONObject, name: String) {
        val present = DeviceRuntimeJson.booleanValue(data, "present")
        DeviceRuntimeJson.requireExactKeys(
            data,
            if (present) PRESENT_PARTITION_KEYS else ABSENT_PARTITION_KEYS,
            "$ROOT_LABEL.partition.$name"
        )
        if (present) {
            DeviceRuntimeJson.stringValue(data, "label")
            require(DeviceRuntimeJson.longValue(data, "address") >= 0L)
            require(DeviceRuntimeJson.longValue(data, "size") > 0L)
            DeviceRuntimeJson.intValue(data, "type")
            DeviceRuntimeJson.intValue(data, "subtype")
        }
    }

    private fun validateOta(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, OTA_KEYS, "$ROOT_LABEL.ota")
        DeviceRuntimeJson.booleanValue(data, "supported")
        require(DeviceRuntimeJson.stringValue(data, "transport") == "websocket-control")
        require(DeviceRuntimeJson.stringValue(data, "binaryTransfer") == "firmware-download")
        require(
            DeviceRuntimeJson.stringValue(data, "progressEvent") ==
                DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS
        )
        require(
            DeviceRuntimeJson.stringValue(data, "completedEvent") ==
                DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
        )
        require(DeviceRuntimeJson.stringValue(data, "startCommand") == "firmware.ota.start")
        require(DeviceRuntimeJson.stringValue(data, "statusCommand") == "firmware.ota.status")
        DeviceFirmwareStatusParser.parseOtaProgressEventExact(
            DeviceRuntimeJson.objectValue(data, "status")
        ).getOrThrow()
    }

    private fun validateRuntime(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, RUNTIME_KEYS, "$ROOT_LABEL.runtime")
        require(DeviceRuntimeJson.stringValue(data, "transport") == "websocket")
        require(DeviceRuntimeJson.stringValue(data, "wsSchema") == AqlWsContract.SCHEMA)
        require(
            DeviceRuntimeJson.intValue(data, "wsProtocolVersion") ==
                AqlWsContract.PROTOCOL_VERSION
        )
        require(DeviceRuntimeJson.booleanValue(data, "readOnly"))
    }

    private const val ROOT_LABEL = "firmware.status.get.data"
    private val ROOT_KEYS = setOf(
        "version", "build", "hardwareRevision", "sdkVersion", "uptimeMs",
        "product", "flash", "partition", "ota", "runtime"
    )
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "family", "model", "displayName", "skuCode"
    )
    private val FLASH_KEYS = setOf("chipSize", "sketchSize", "freeSketchSpace")
    private val PARTITION_KEYS = setOf(
        "running", "boot", "nextUpdate", "bootMatchesRunning", "runningState",
        "runningStateCode", "stateReadOk", "stateReadError"
    )
    private val ABSENT_PARTITION_KEYS = setOf("present")
    private val PRESENT_PARTITION_KEYS = setOf(
        "present", "label", "address", "size", "type", "subtype"
    )
    private val OTA_KEYS = setOf(
        "supported", "transport", "binaryTransfer", "progressEvent", "completedEvent",
        "startCommand", "statusCommand", "status"
    )
    private val RUNTIME_KEYS = setOf(
        "transport", "wsSchema", "wsProtocolVersion", "readOnly"
    )
}

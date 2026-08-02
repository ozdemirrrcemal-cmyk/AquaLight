package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

@Suppress("TooManyFunctions")
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
        return parseValidatedStatus(data)
    }

    fun parseOtaStatus(data: JSONObject): DeviceFirmwareOtaStatusResponse =
        DeviceFirmwareStatusParser.parseOtaStatusResponseExact(data).getOrThrow()

    /**
     * Maps only the object that parseStatus has already validated against the exact firmware
     * schema. No optional getter, alias, coercion or default-valued compatibility path exists.
     */
    private fun parseValidatedStatus(data: JSONObject): DeviceFirmwareStatus {
        val product = data.getJSONObject("product")
        val flash = data.getJSONObject("flash")
        val partition = data.getJSONObject("partition")
        val ota = data.getJSONObject("ota")
        val runtime = data.getJSONObject("runtime")
        return DeviceFirmwareStatus(
            version = data.getString("version"),
            build = data.getString("build"),
            hardwareRevision = data.getString("hardwareRevision"),
            sdkVersion = data.getString("sdkVersion"),
            uptimeMs = data.getLong("uptimeMs"),
            productKey = product.getString("productKey"),
            productId = product.getString("productId"),
            family = product.getString("family"),
            model = product.getString("model"),
            displayName = product.getString("displayName"),
            skuCode = product.getString("skuCode"),
            flashChipSize = flash.getLong("chipSize"),
            flashSketchSize = flash.getLong("sketchSize"),
            flashFreeSketchSpace = flash.getLong("freeSketchSpace"),
            partition = parseValidatedPartition(partition),
            otaSupported = ota.getBoolean("supported"),
            otaTransport = ota.getString("transport"),
            otaBinaryTransfer = ota.getString("binaryTransfer"),
            otaProgressEvent = ota.getString("progressEvent"),
            otaCompletedEvent = ota.getString("completedEvent"),
            otaStartCommand = ota.getString("startCommand"),
            otaStatusCommand = ota.getString("statusCommand"),
            ota = DeviceFirmwareStatusParser.parseOtaSnapshotExact(
                ota.getJSONObject("status")
            ).getOrThrow(),
            runtime = DeviceFirmwareRuntimeInfo(
                transport = runtime.getString("transport"),
                wsSchema = runtime.getString("wsSchema"),
                wsProtocolVersion = runtime.getInt("wsProtocolVersion"),
                readOnly = runtime.getBoolean("readOnly")
            )
        )
    }

    private fun parseValidatedPartition(data: JSONObject): DeviceFirmwarePartitionStatus =
        DeviceFirmwarePartitionStatus(
            running = parseValidatedPartitionInfo(data.getJSONObject("running")),
            boot = parseValidatedPartitionInfo(data.getJSONObject("boot")),
            nextUpdate = parseValidatedPartitionInfo(data.getJSONObject("nextUpdate")),
            bootMatchesRunning = data.getBoolean("bootMatchesRunning"),
            runningState = data.getString("runningState"),
            runningStateCode = data.getInt("runningStateCode"),
            stateReadOk = data.getBoolean("stateReadOk"),
            stateReadError = data.getInt("stateReadError")
        )

    private fun parseValidatedPartitionInfo(data: JSONObject): DeviceFirmwarePartitionInfo {
        if (!data.getBoolean("present")) return DeviceFirmwarePartitionInfo()
        return DeviceFirmwarePartitionInfo(
            present = true,
            label = data.getString("label"),
            address = data.getLong("address"),
            size = data.getLong("size"),
            type = data.getInt("type"),
            subtype = data.getInt("subtype")
        )
    }

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
        DeviceFirmwareStatusParser.parseOtaSnapshotExact(
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

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONObject

object DeviceFirmwareStatusParser {

    fun parseFirmwareStatus(data: JSONObject): DeviceFirmwareStatus {
        val product = data.optJSONObject("product") ?: JSONObject()
        val flash = data.optJSONObject("flash") ?: JSONObject()
        val otaRoot = data.optJSONObject("ota") ?: JSONObject()

        return DeviceFirmwareStatus(
            version = data.optString("version", "").trim(),
            build = data.optString("build", "").trim(),
            hardwareRevision = data.optString("hardwareRevision", "").trim(),
            sdkVersion = data.optString("sdkVersion", "").trim(),
            uptimeMs = data.optLong("uptimeMs", 0L),
            productKey = product.optString("productKey", "").trim(),
            productId = product.optString("productId", "").trim(),
            family = product.optString("family", "").trim(),
            model = product.optString("model", "").trim(),
            displayName = product.optString("displayName", "").trim(),
            skuCode = product.optString("skuCode", "").trim(),
            flashChipSize = flash.optLong("chipSize", 0L),
            flashSketchSize = flash.optLong("sketchSize", 0L),
            flashFreeSketchSpace = flash.optLong("freeSketchSpace", 0L),
            partition = parsePartitionStatus(data.optJSONObject("partition")),
            otaSupported = otaRoot.optBoolean("supported", false),
            otaTransport = otaRoot.optString("transport", "").trim(),
            otaBinaryTransfer = otaRoot.optString("binaryTransfer", "").trim(),
            otaProgressEvent = otaRoot.optString("progressEvent", "").trim(),
            otaCompletedEvent = otaRoot.optString("completedEvent", "").trim(),
            otaStartCommand = otaRoot.optString("startCommand", "").trim(),
            otaStatusCommand = otaRoot.optString("statusCommand", "").trim(),
            ota = parseOtaSnapshot(otaRoot.optJSONObject("status"))
        )
    }

    fun parseOtaStatusResponse(data: JSONObject): DeviceFirmwareOtaSnapshot {
        return parseOtaSnapshot(data.optJSONObject("ota") ?: data)
    }

    fun parseOtaProgressEvent(data: JSONObject): DeviceFirmwareOtaSnapshot {
        return parseOtaSnapshot(data)
    }

    fun parseOtaStartAccepted(data: JSONObject): DeviceFirmwareOtaStartAccepted {
        return DeviceFirmwareOtaStartAccepted(
            accepted = data.optBoolean("accepted", false),
            request = null,
            ota = parseOtaSnapshot(data.optJSONObject("ota"))
        )
    }

    fun parseOtaClearResult(data: JSONObject): DeviceFirmwareOtaClearResult {
        return DeviceFirmwareOtaClearResult(
            cleared = data.optBoolean("cleared", false),
            previous = parseOtaSnapshot(data.optJSONObject("previous")),
            ota = parseOtaSnapshot(data.optJSONObject("ota"))
        )
    }

    private fun parsePartitionStatus(json: JSONObject?): DeviceFirmwarePartitionStatus {
        val source = json ?: JSONObject()
        return DeviceFirmwarePartitionStatus(
            running = parsePartitionInfo(source.optJSONObject("running")),
            boot = parsePartitionInfo(source.optJSONObject("boot")),
            nextUpdate = parsePartitionInfo(source.optJSONObject("nextUpdate")),
            bootMatchesRunning = source.optBoolean("bootMatchesRunning", false),
            runningState = source.optString("runningState", "").trim(),
            runningStateCode = source.optInt("runningStateCode", 0),
            stateReadOk = source.optBoolean("stateReadOk", false),
            stateReadError = source.optInt("stateReadError", 0)
        )
    }

    private fun parsePartitionInfo(json: JSONObject?): DeviceFirmwarePartitionInfo {
        val source = json ?: JSONObject()
        return DeviceFirmwarePartitionInfo(
            present = source.optBoolean("present", false),
            label = source.optString("label", "").trim(),
            address = source.optLong("address", 0L),
            size = source.optLong("size", 0L),
            type = source.optInt("type", 0),
            subtype = source.optInt("subtype", 0)
        )
    }

    private fun parseOtaSnapshot(json: JSONObject?): DeviceFirmwareOtaSnapshot {
        val source = json ?: JSONObject()
        val phaseRaw = source.optString("phase", DeviceFirmwareOtaPhase.IDLE.wireValue).trim()
        val phase = DeviceFirmwareOtaPhase.fromWire(phaseRaw)
        val progressPermille = source.optInt("progressPermille", 0)

        return DeviceFirmwareOtaSnapshot(
            phase = phase,
            phaseRaw = phaseRaw.ifBlank { phase.wireValue },
            active = source.optBoolean("active", false),
            completed = source.optBoolean("completed", phase.isTerminal),
            success = source.optBoolean("success", phase == DeviceFirmwareOtaPhase.SUCCEEDED),
            failed = source.optBoolean("failed", phase == DeviceFirmwareOtaPhase.FAILED),
            restartRequired = source.optBoolean("restartRequired", false),
            restartScheduled = source.optBoolean("restartScheduled", false),
            allowInsecureHttp = source.optBoolean("allowInsecureHttp", false),
            startedAtMs = source.optLong("startedAtMs", 0L),
            finishedAtMs = source.optLong("finishedAtMs", 0L),
            bytesWritten = source.optLong("bytesWritten", 0L),
            contentLength = source.optLong("contentLength", 0L),
            progressPermille = progressPermille,
            progressPercent = source.optDouble("progressPercent", progressPermille / 10.0),
            targetVersion = source.optString("targetVersion", "").trim(),
            sha256Expected = source.optString("sha256Expected", "").trim(),
            sha256Actual = source.optString("sha256Actual", "").trim(),
            lastError = source.optString("lastError", "").trim(),
            lastErrorField = source.optString("lastErrorField", "").trim(),
            urlScheme = source.optString("urlScheme", "").trim(),
            httpStatus = source.optInt("httpStatus", 0)
        )
    }
}

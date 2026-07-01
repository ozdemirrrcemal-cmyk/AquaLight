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
        val request = data.optJSONObject("request")
        return DeviceFirmwareOtaStartAccepted(
            accepted = data.optBoolean("accepted", false),
            request = request?.let { parseAcceptedRequest(it) },
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

    private fun parseAcceptedRequest(request: JSONObject): DeviceFirmwareOtaStartPayload? {
        val urlScheme = request.optString("urlScheme", "").trim()
        val version = request.optString("version", "").trim()
        val expectedSize = request.optInt("expectedSize", 0)
        val productKey = request.optString("productKey", "").trim()
        val productId = request.optString("productId", "").trim()
        val hardwareRevision = request.optString("hardwareRevision", "").trim()

        if (version.isBlank() || productKey.isBlank() || productId.isBlank() || hardwareRevision.isBlank()) {
            return null
        }

        // Firmware intentionally echoes only urlScheme, not the full URL. The production start payload
        // is retained by the caller from the update plan, so this parsed request is best-effort only.
        return runCatching {
            DeviceFirmwareOtaStartPayload(
                url = "${urlScheme.ifBlank { "https" }}://placeholder.invalid/",
                version = version,
                sha256 = "0".repeat(DeviceFirmwareRuntimeContract.Limit.SHA256_HEX_LENGTH),
                expectedSize = expectedSize.coerceAtLeast(1),
                productKey = productKey,
                productId = productId,
                hardwareRevision = hardwareRevision,
                applyNow = request.optBoolean("applyNow", true),
                allowInsecureHttp = false
            )
        }.getOrNull()
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

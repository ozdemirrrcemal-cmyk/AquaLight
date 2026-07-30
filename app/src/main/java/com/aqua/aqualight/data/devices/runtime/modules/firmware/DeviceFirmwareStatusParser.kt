package com.aqua.aqualight.data.devices.runtime.modules.firmware

import kotlin.math.abs
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
        return parseOtaSnapshot(data.optJSONObject("ota") ?: data)
    }

    fun parseOtaStartAccepted(data: JSONObject): DeviceFirmwareOtaStartAccepted {
        return DeviceFirmwareOtaStartAccepted(
            accepted = data.optBoolean("accepted", false),
            request = data.optJSONObject("request")?.let(::parseRequestEchoPermissive),
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

    fun parseOtaStatusResponseExact(data: JSONObject): Result<DeviceFirmwareOtaSnapshot> =
        runCatching {
            data.requireExactKeys(OTA_STATUS_RESPONSE_KEYS, "firmware.ota.status.data")
            require(data.requiredExactString("operation") == "otaStatus")
            require(data.requiredExactString("runtimeTransport") == "websocket")
            require(data.requiredExactString("command") == "firmware.ota.status")
            require(data.requiredExactString("binaryTransfer") == "firmware-download")
            require(
                data.requiredExactString("progressEvent") ==
                    DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS
            )
            require(
                data.requiredExactString("completedEvent") ==
                    DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
            )
            parseOtaSnapshotExact(data.requiredObject("ota"))
        }

    fun parseOtaStartAcceptedExact(data: JSONObject): Result<DeviceFirmwareOtaStartAccepted> =
        runCatching {
            data.requireExactKeys(OTA_START_RESPONSE_KEYS, "firmware.ota.start.data")
            require(data.requiredExactString("operation") == "otaStart")
            require(data.requiredExactBoolean("accepted"))
            require(data.requiredExactString("runtimeTransport") == "websocket")
            require(data.requiredExactString("command") == "firmware.ota.start")
            require(data.requiredExactString("binaryTransfer") == "firmware-download")
            require(data.requiredExactString("event") == DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS)
            require(
                data.requiredExactString("progressEvent") ==
                    DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS
            )
            require(
                data.requiredExactString("completedEvent") ==
                    DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
            )
            DeviceFirmwareOtaStartAccepted(
                accepted = true,
                request = parseRequestEchoExact(data.requiredObject("request")),
                ota = parseOtaSnapshotExact(data.requiredObject("ota"))
            )
        }

    fun parseOtaClearResultExact(data: JSONObject): Result<DeviceFirmwareOtaClearResult> =
        runCatching {
            data.requireExactKeys(OTA_CLEAR_RESPONSE_KEYS, "firmware.ota.clear.data")
            require(data.requiredExactString("operation") == "otaClear")
            require(data.requiredExactBoolean("cleared"))
            require(data.requiredExactString("runtimeTransport") == "websocket")
            require(data.requiredExactString("command") == "firmware.ota.clear")
            DeviceFirmwareOtaClearResult(
                cleared = true,
                previous = parseOtaSnapshotExact(data.requiredObject("previous")),
                ota = parseOtaSnapshotExact(data.requiredObject("ota"))
            )
        }

    fun parseOtaProgressEventExact(data: JSONObject): Result<DeviceFirmwareOtaSnapshot> =
        runCatching {
            parseOtaSnapshotExact(data.optJSONObject("ota") ?: data)
        }

    private fun parseRequestEchoExact(json: JSONObject): DeviceFirmwareOtaStartRequestEcho {
        json.requireExactKeys(OTA_REQUEST_ECHO_KEYS, "firmware.ota.start.data.request")
        return DeviceFirmwareOtaStartRequestEcho(
            urlScheme = json.requiredExactString("urlScheme"),
            version = json.requiredExactString("version"),
            expectedSize = json.requiredExactInt("expectedSize"),
            applyNow = json.requiredExactBoolean("applyNow"),
            allowInsecureHttp = json.requiredExactBoolean("allowInsecureHttp"),
            productKey = json.requiredExactString("productKey"),
            productId = json.requiredExactString("productId"),
            model = json.requiredExactString("model"),
            hardwareRevision = json.requiredExactString("hardwareRevision")
        ).also { echo ->
            require(echo.urlScheme == "https")
            require(echo.expectedSize > 0)
            require(!echo.allowInsecureHttp)
        }
    }

    private fun parseRequestEchoPermissive(json: JSONObject): DeviceFirmwareOtaStartRequestEcho {
        return DeviceFirmwareOtaStartRequestEcho(
            urlScheme = json.optString("urlScheme", "").trim(),
            version = json.optString("version", "").trim(),
            expectedSize = json.optInt("expectedSize", 0),
            applyNow = json.optBoolean("applyNow", true),
            allowInsecureHttp = json.optBoolean("allowInsecureHttp", false),
            productKey = json.optString("productKey", "").trim(),
            productId = json.optString("productId", "").trim(),
            model = json.optString("model", "").trim(),
            hardwareRevision = json.optString("hardwareRevision", "").trim()
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
            completed = phase.isTerminal,
            success = phase == DeviceFirmwareOtaPhase.SUCCEEDED,
            failed = phase == DeviceFirmwareOtaPhase.FAILED,
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

    private fun parseOtaSnapshotExact(source: JSONObject): DeviceFirmwareOtaSnapshot {
        source.requireExactKeys(OTA_SNAPSHOT_KEYS, "firmware OTA snapshot")
        val phaseRaw = source.requiredExactString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown firmware OTA phase: $phaseRaw"
        }
        val active = source.requiredExactBoolean("active")
        val progressPermille = source.requiredExactInt("progressPermille")
        val progressPercent = source.requiredExactDouble("progressPercent")
        val bytesWritten = source.requiredExactLong("bytesWritten")
        val contentLength = source.requiredExactLong("contentLength")
        val restartRequired = source.requiredExactBoolean("restartRequired")
        val restartScheduled = source.requiredExactBoolean("restartScheduled")
        val allowInsecureHttp = source.requiredExactBoolean("allowInsecureHttp")
        val targetVersion = source.requiredStringAllowEmpty("targetVersion")
        val sha256Expected = source.requiredStringAllowEmpty("sha256Expected")
        val sha256Actual = source.requiredStringAllowEmpty("sha256Actual")
        val urlScheme = source.requiredStringAllowEmpty("urlScheme")

        require(progressPermille in 0..1_000) { "OTA progressPermille is outside 0..1000." }
        require(abs(progressPercent - progressPermille / 10.0) <= 0.11) {
            "OTA progressPercent differs from progressPermille."
        }
        require(bytesWritten >= 0L && contentLength >= 0L)
        require(contentLength == 0L || bytesWritten <= contentLength)
        require(!allowInsecureHttp) { "Firmware reported insecure OTA transport." }
        require(urlScheme.isEmpty() || urlScheme == "https")
        require(sha256Expected.isEmpty() || sha256Expected.isSha256Hex())
        require(sha256Actual.isEmpty() || sha256Actual.isSha256Hex())

        val activePhase = phase in setOf(
            DeviceFirmwareOtaPhase.STARTING,
            DeviceFirmwareOtaPhase.SAFE_MODE,
            DeviceFirmwareOtaPhase.DOWNLOADING,
            DeviceFirmwareOtaPhase.WRITING,
            DeviceFirmwareOtaPhase.VERIFYING
        )
        require(active == activePhase) { "OTA active flag differs from its exact phase." }
        require(!restartRequired || phase == DeviceFirmwareOtaPhase.SUCCEEDED)
        require(!restartScheduled || restartRequired)
        if (active || phase.isTerminal) {
            require(targetVersion.isNotBlank()) { "Active/terminal OTA targetVersion is missing." }
        }

        return DeviceFirmwareOtaSnapshot(
            phase = phase,
            phaseRaw = phaseRaw,
            active = active,
            completed = phase.isTerminal,
            success = phase == DeviceFirmwareOtaPhase.SUCCEEDED,
            failed = phase == DeviceFirmwareOtaPhase.FAILED,
            restartRequired = restartRequired,
            restartScheduled = restartScheduled,
            allowInsecureHttp = false,
            startedAtMs = source.requiredExactLong("startedAtMs"),
            finishedAtMs = source.requiredExactLong("finishedAtMs"),
            bytesWritten = bytesWritten,
            contentLength = contentLength,
            progressPermille = progressPermille,
            progressPercent = progressPercent,
            targetVersion = targetVersion,
            sha256Expected = sha256Expected,
            sha256Actual = sha256Actual,
            lastError = source.requiredStringAllowEmpty("lastError"),
            lastErrorField = source.requiredStringAllowEmpty("lastErrorField"),
            urlScheme = urlScheme,
            httpStatus = source.requiredExactInt("httpStatus")
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = buildSet {
            val iterator = keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        require(actual == expected) { "$label keys differ from the firmware contract." }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be an object.")

    private fun JSONObject.requiredExactString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty()) { "$key must not be empty." }
        require(!value.first().isWhitespace() && !value.last().isWhitespace())
        require(value.none(Char::isISOControl))
        return value
    }

    private fun JSONObject.requiredStringAllowEmpty(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace()))
        require(value.none(Char::isISOControl))
        return value
    }

    private fun JSONObject.requiredExactBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredExactInt(key: String): Int {
        val value = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble())
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredExactLong(key: String): Long {
        val value = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble())
        return asLong
    }

    private fun JSONObject.requiredExactDouble(key: String): Double {
        val value = get(key) as? Number ?: error("$key must be numeric.")
        return value.toDouble().also { require(it.isFinite()) }
    }

    private val OTA_STATUS_RESPONSE_KEYS = setOf(
        "operation", "runtimeTransport", "command", "binaryTransfer",
        "progressEvent", "completedEvent", "ota"
    )
    private val OTA_START_RESPONSE_KEYS = setOf(
        "operation", "accepted", "runtimeTransport", "command", "binaryTransfer", "event",
        "progressEvent", "completedEvent", "request", "ota"
    )
    private val OTA_CLEAR_RESPONSE_KEYS = setOf(
        "operation", "cleared", "runtimeTransport", "command", "previous", "ota"
    )
    private val OTA_REQUEST_ECHO_KEYS = setOf(
        "urlScheme", "version", "expectedSize", "applyNow", "allowInsecureHttp",
        "productKey", "productId", "model", "hardwareRevision"
    )
    private val OTA_SNAPSHOT_KEYS = setOf(
        "phase", "active", "restartRequired", "restartScheduled", "allowInsecureHttp",
        "startedAtMs", "finishedAtMs", "bytesWritten", "contentLength", "progressPermille",
        "progressPercent", "targetVersion", "sha256Expected", "sha256Actual", "lastError",
        "lastErrorField", "urlScheme", "httpStatus"
    )
}

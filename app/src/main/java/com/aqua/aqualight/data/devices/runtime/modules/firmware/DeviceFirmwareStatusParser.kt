package com.aqua.aqualight.data.devices.runtime.modules.firmware

import kotlin.math.abs
import org.json.JSONObject

@Suppress("TooManyFunctions", "LongMethod", "MagicNumber")
object DeviceFirmwareStatusParser {

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
            parseOtaSnapshotExactObject(data.requiredObject("ota"))
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
                ota = parseOtaSnapshotExactObject(data.requiredObject("ota"))
            )
        }

    fun parseOtaClearResultExact(data: JSONObject): Result<DeviceFirmwareOtaClearResult> =
        runCatching {
            data.requireExactKeys(OTA_CLEAR_RESPONSE_KEYS, "firmware.ota.clear.data")
            require(data.requiredExactString("operation") == "otaClear")
            require(data.requiredExactBoolean("cleared"))
            require(data.requiredExactString("runtimeTransport") == "websocket")
            require(data.requiredExactString("command") == "firmware.ota.clear")
            val previous = parseOtaClearPreviousExact(data.requiredObject("previous"))
            val ota = parseOtaSnapshotExactObject(data.requiredObject("ota"))
            require(ota.phase == DeviceFirmwareOtaPhase.IDLE)
            DeviceFirmwareOtaClearResult(
                cleared = true,
                previous = previous,
                ota = ota
            )
        }

    fun parseOtaSnapshotExact(data: JSONObject): Result<DeviceFirmwareOtaSnapshot> =
        runCatching { parseOtaSnapshotExactObject(data) }

    fun parseOtaProgressEventExact(data: JSONObject): Result<DeviceFirmwareOtaSnapshot> =
        runCatching {
            data.requireExactKeys(OTA_EVENT_KEYS, "firmware OTA event data")
            require(data.requiredExactString("runtimeTransport") == "websocket")
            require(data.requiredExactString("binaryTransfer") == "firmware-download")
            val snapshot = parseOtaSnapshotFieldsExact(data)
            require(data.requiredExactBoolean("completed") == snapshot.phase.isTerminal)
            require(
                data.requiredExactBoolean("success") ==
                    (snapshot.phase == DeviceFirmwareOtaPhase.SUCCEEDED)
            )
            require(
                data.requiredExactBoolean("failed") ==
                    (snapshot.phase == DeviceFirmwareOtaPhase.FAILED)
            )
            snapshot
        }

    private fun parseRequestEchoExact(json: JSONObject): DeviceFirmwareOtaStartRequestEcho {
        json.requireExactKeys(OTA_REQUEST_ECHO_KEYS, "firmware.ota.start.data.request")
        return DeviceFirmwareOtaStartRequestEcho(
            urlScheme = json.requiredExactString("urlScheme"),
            version = json.requiredExactString("version"),
            expectedSize = json.requiredExactInt("expectedSize"),
            applyNow = json.requiredExactBoolean("applyNow"),
            productKey = json.requiredExactString("productKey"),
            productId = json.requiredExactString("productId"),
            model = json.requiredExactString("model"),
            hardwareRevision = json.requiredExactString("hardwareRevision")
        ).also { echo ->
            require(echo.urlScheme == "https")
            require(echo.version.isExactFirmwareVersion())
            require(echo.expectedSize > 0)
        }
    }

    private fun parseOtaSnapshotExactObject(source: JSONObject): DeviceFirmwareOtaSnapshot {
        source.requireExactKeys(OTA_SNAPSHOT_KEYS, "firmware OTA snapshot")
        return parseOtaSnapshotFieldsExact(source)
    }

    private fun parseOtaSnapshotFieldsExact(source: JSONObject): DeviceFirmwareOtaSnapshot {
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
        val startedAtMs = source.requiredExactLong("startedAtMs")
        val finishedAtMs = source.requiredExactLong("finishedAtMs")
        // Firmware stores the signed HTTPClient result. Negative values are transport diagnostics.
        val httpStatus = source.requiredExactInt("httpStatus")
        require(startedAtMs >= 0L && finishedAtMs >= 0L)
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
            require(targetVersion.isExactFirmwareVersion()) {
                "Active/terminal OTA targetVersion must use exact X.Y.Z format."
            }
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
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
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
            httpStatus = httpStatus
        )
    }

    private fun parseOtaClearPreviousExact(source: JSONObject): DeviceFirmwareOtaSnapshot {
        source.requireExactKeys(OTA_CLEAR_PREVIOUS_KEYS, "firmware.ota.clear.data.previous")
        val phaseRaw = source.requiredExactString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown previous firmware OTA phase: $phaseRaw"
        }
        val restartRequired = source.requiredExactBoolean("restartRequired")
        val restartScheduled = source.requiredExactBoolean("restartScheduled")
        require(!restartRequired || phase == DeviceFirmwareOtaPhase.SUCCEEDED)
        require(!restartScheduled || restartRequired)
        return DeviceFirmwareOtaSnapshot(
            phase = phase,
            phaseRaw = phaseRaw,
            active = false,
            completed = phase.isTerminal,
            success = phase == DeviceFirmwareOtaPhase.SUCCEEDED,
            failed = phase == DeviceFirmwareOtaPhase.FAILED,
            restartRequired = restartRequired,
            restartScheduled = restartScheduled,
            targetVersion = source.requiredStringAllowEmpty("targetVersion"),
            lastError = source.requiredStringAllowEmpty("lastError"),
            lastErrorField = source.requiredStringAllowEmpty("lastErrorField")
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
    private val OTA_CLEAR_PREVIOUS_KEYS = setOf(
        "phase", "restartRequired", "restartScheduled", "targetVersion", "lastError",
        "lastErrorField"
    )
    private val OTA_REQUEST_ECHO_KEYS = setOf(
        "urlScheme", "version", "expectedSize", "applyNow",
        "productKey", "productId", "model", "hardwareRevision"
    )
    private val OTA_SNAPSHOT_KEYS = setOf(
        "phase", "active", "restartRequired", "restartScheduled",
        "startedAtMs", "finishedAtMs", "bytesWritten", "contentLength", "progressPermille",
        "progressPercent", "targetVersion", "sha256Expected", "sha256Actual", "lastError",
        "lastErrorField", "urlScheme", "httpStatus"
    )
    private val OTA_EVENT_KEYS = OTA_SNAPSHOT_KEYS + setOf(
        "completed", "success", "failed", "runtimeTransport", "binaryTransfer"
    )
}

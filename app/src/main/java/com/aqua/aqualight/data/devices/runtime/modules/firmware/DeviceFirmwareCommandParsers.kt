package com.aqua.aqualight.data.devices.runtime.modules.firmware

import kotlin.math.abs
import org.json.JSONObject

/** Exact decoders for the four authenticated firmware/OTA commands and OTA events. */
@Suppress("LongMethod", "MagicNumber", "TooManyFunctions")
object DeviceFirmwareCommandParsers {

    fun parseFirmwareStatus(data: JSONObject): DeviceFirmwareStatus {
        data.requireExactKeys(FIRMWARE_STATUS_KEYS, "firmware.status.get.data")

        val product = data.requiredObject("product").also {
            it.requireExactKeys(PRODUCT_KEYS, "firmware.status.get.data.product")
        }
        val flash = data.requiredObject("flash").also {
            it.requireExactKeys(FLASH_KEYS, "firmware.status.get.data.flash")
        }
        val partition = parsePartitionStatus(data.requiredObject("partition"))
        val otaRoot = data.requiredObject("ota").also {
            it.requireExactKeys(FIRMWARE_OTA_ROOT_KEYS, "firmware.status.get.data.ota")
        }
        val runtime = data.requiredObject("runtime").also {
            it.requireExactKeys(FIRMWARE_RUNTIME_KEYS, "firmware.status.get.data.runtime")
            require(it.requiredExactString("transport") == "websocket")
            require(it.requiredExactString("wsSchema") == "aql.ws.v1")
            require(it.requiredExactInt("wsProtocolVersion") == 1)
            require(it.requiredExactBoolean("readOnly"))
        }

        val otaSupported = otaRoot.requiredExactBoolean("supported")
        require(otaRoot.requiredExactString("transport") == "websocket-control")
        require(otaRoot.requiredExactString("binaryTransfer") == "firmware-download")
        require(
            otaRoot.requiredExactString("progressEvent") ==
                DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS
        )
        require(
            otaRoot.requiredExactString("completedEvent") ==
                DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
        )
        require(otaRoot.requiredExactString("startCommand") == "firmware.ota.start")
        require(otaRoot.requiredExactString("statusCommand") == "firmware.ota.status")

        val status = DeviceFirmwareStatus(
            version = data.requiredExactString("version"),
            build = data.requiredExactString("build"),
            hardwareRevision = data.requiredExactString("hardwareRevision"),
            sdkVersion = data.requiredExactString("sdkVersion"),
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            productKey = product.requiredExactString("productKey"),
            productId = product.requiredExactString("productId"),
            family = product.requiredExactString("family"),
            model = product.requiredExactString("model"),
            displayName = product.requiredExactString("displayName"),
            skuCode = product.requiredExactString("skuCode"),
            flashChipSize = flash.requiredNonNegativeLong("chipSize"),
            flashSketchSize = flash.requiredNonNegativeLong("sketchSize"),
            flashFreeSketchSpace = flash.requiredNonNegativeLong("freeSketchSpace"),
            partition = partition,
            otaSupported = otaSupported,
            otaTransport = "websocket-control",
            otaBinaryTransfer = "firmware-download",
            otaProgressEvent = DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS,
            otaCompletedEvent = DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED,
            otaStartCommand = "firmware.ota.start",
            otaStatusCommand = "firmware.ota.status",
            ota = parseOtaSnapshot(otaRoot.requiredObject("status"))
        )

        require(status.flashSketchSize <= status.flashChipSize)
        require(status.flashFreeSketchSpace <= status.flashChipSize)
        @Suppress("UNUSED_VARIABLE")
        val validatedRuntime = runtime
        return status
    }

    fun parseOtaStatus(data: JSONObject): DeviceFirmwareOtaSnapshot {
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
        return parseOtaSnapshot(data.requiredObject("ota"))
    }

    fun parseOtaStart(data: JSONObject): DeviceFirmwareOtaStartAccepted {
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
        return DeviceFirmwareOtaStartAccepted(
            accepted = true,
            request = parseRequestEcho(data.requiredObject("request")),
            ota = parseOtaSnapshot(data.requiredObject("ota"))
        )
    }

    fun parseOtaClear(data: JSONObject): DeviceFirmwareOtaClearTypedResult {
        data.requireExactKeys(OTA_CLEAR_RESPONSE_KEYS, "firmware.ota.clear.data")
        require(data.requiredExactString("operation") == "otaClear")
        require(data.requiredExactBoolean("cleared"))
        require(data.requiredExactString("runtimeTransport") == "websocket")
        require(data.requiredExactString("command") == "firmware.ota.clear")
        return DeviceFirmwareOtaClearTypedResult(
            cleared = true,
            previous = parseOtaPrevious(data.requiredObject("previous")),
            ota = parseOtaSnapshot(data.requiredObject("ota"))
        )
    }

    /**
     * Firmware emits two exact `firmware.ota.progress` shapes:
     * the command-result event staged by ota.start and periodic OTA snapshot events.
     */
    fun parseOtaEvent(data: JSONObject): DeviceFirmwareOtaSnapshot {
        val keys = data.keySetExact()
        return when (keys) {
            OTA_STAGED_EVENT_KEYS -> {
                require(data.requiredExactString("commandId").isNotBlank())
                require(data.requiredExactString("module") == DeviceFirmwareRuntimeContract.MODULE)
                require(data.requiredExactString("action") == DeviceFirmwareRuntimeContract.Action.OTA_START)
                require(data.requiredExactString("sessionId").isNotBlank())
                require(data.requiredNonNegativeLong("publishedAtMs") >= 0L)
                parseOtaStart(data.requiredObject("result")).ota
            }
            OTA_TICK_EVENT_KEYS -> parseOtaTickEvent(data)
            else -> error("firmware OTA event keys differ from the firmware contract.")
        }
    }

    private fun parseOtaTickEvent(data: JSONObject): DeviceFirmwareOtaSnapshot {
        data.requireExactKeys(OTA_TICK_EVENT_KEYS, "firmware OTA tick event")
        require(data.requiredExactString("runtimeTransport") == "websocket")
        require(data.requiredExactString("binaryTransfer") == "firmware-download")

        val snapshotJson = JSONObject()
        OTA_SNAPSHOT_KEYS.forEach { key -> snapshotJson.put(key, data.get(key)) }
        val snapshot = parseOtaSnapshot(snapshotJson)
        val completed = data.requiredExactBoolean("completed")
        val success = data.requiredExactBoolean("success")
        val failed = data.requiredExactBoolean("failed")
        require(completed == snapshot.phase.isTerminal)
        require(success == (snapshot.phase == DeviceFirmwareOtaPhase.SUCCEEDED))
        require(failed == (snapshot.phase == DeviceFirmwareOtaPhase.FAILED))
        require(!(success && failed))
        return snapshot
    }

    private fun parseRequestEcho(json: JSONObject): DeviceFirmwareOtaStartRequestEcho {
        json.requireExactKeys(OTA_REQUEST_ECHO_KEYS, "firmware.ota.start.data.request")
        return DeviceFirmwareOtaStartRequestEcho(
            urlScheme = json.requiredExactString("urlScheme"),
            version = json.requiredExactString("version"),
            expectedSize = json.requiredPositiveInt("expectedSize"),
            applyNow = json.requiredExactBoolean("applyNow"),
            allowInsecureHttp = json.requiredExactBoolean("allowInsecureHttp"),
            productKey = json.requiredExactString("productKey"),
            productId = json.requiredExactString("productId"),
            model = json.requiredExactString("model"),
            hardwareRevision = json.requiredExactString("hardwareRevision")
        ).also { echo ->
            require(echo.urlScheme == "https")
            require(!echo.allowInsecureHttp)
        }
    }

    private fun parseOtaPrevious(json: JSONObject): DeviceFirmwareOtaClearPrevious {
        json.requireExactKeys(OTA_CLEAR_PREVIOUS_KEYS, "firmware.ota.clear.data.previous")
        val phaseRaw = json.requiredExactString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown previous firmware OTA phase: $phaseRaw"
        }
        val restartRequired = json.requiredExactBoolean("restartRequired")
        val restartScheduled = json.requiredExactBoolean("restartScheduled")
        require(!restartRequired || phase == DeviceFirmwareOtaPhase.SUCCEEDED)
        require(!restartScheduled || restartRequired)
        return DeviceFirmwareOtaClearPrevious(
            phase = phase,
            phaseRaw = phaseRaw,
            restartRequired = restartRequired,
            restartScheduled = restartScheduled,
            targetVersion = json.requiredStringAllowEmpty("targetVersion"),
            lastError = json.requiredStringAllowEmpty("lastError"),
            lastErrorField = json.requiredStringAllowEmpty("lastErrorField")
        )
    }

    private fun parsePartitionStatus(json: JSONObject): DeviceFirmwarePartitionStatus {
        json.requireExactKeys(PARTITION_STATUS_KEYS, "firmware.status.get.data.partition")
        return DeviceFirmwarePartitionStatus(
            running = parsePartitionInfo(json.requiredObject("running"), "running"),
            boot = parsePartitionInfo(json.requiredObject("boot"), "boot"),
            nextUpdate = parsePartitionInfo(json.requiredObject("nextUpdate"), "nextUpdate"),
            bootMatchesRunning = json.requiredExactBoolean("bootMatchesRunning"),
            runningState = json.requiredExactString("runningState"),
            runningStateCode = json.requiredExactInt("runningStateCode"),
            stateReadOk = json.requiredExactBoolean("stateReadOk"),
            stateReadError = json.requiredExactInt("stateReadError")
        )
    }

    private fun parsePartitionInfo(
        json: JSONObject,
        label: String
    ): DeviceFirmwarePartitionInfo {
        val present = json.requiredExactBoolean("present")
        if (!present) {
            json.requireExactKeys(setOf("present"), "firmware partition $label")
            return DeviceFirmwarePartitionInfo(present = false)
        }
        json.requireExactKeys(PARTITION_INFO_KEYS, "firmware partition $label")
        return DeviceFirmwarePartitionInfo(
            present = true,
            label = json.requiredExactString("label"),
            address = json.requiredNonNegativeLong("address"),
            size = json.requiredNonNegativeLong("size"),
            type = json.requiredExactInt("type"),
            subtype = json.requiredExactInt("subtype")
        )
    }

    private fun parseOtaSnapshot(source: JSONObject): DeviceFirmwareOtaSnapshot {
        source.requireExactKeys(OTA_SNAPSHOT_KEYS, "firmware OTA snapshot")
        val phaseRaw = source.requiredExactString("phase")
        val phase = requireNotNull(DeviceFirmwareOtaPhase.fromWireExact(phaseRaw)) {
            "Unknown firmware OTA phase: $phaseRaw"
        }
        val active = source.requiredExactBoolean("active")
        val progressPermille = source.requiredExactInt("progressPermille")
        val progressPercent = source.requiredExactDouble("progressPercent")
        val bytesWritten = source.requiredNonNegativeLong("bytesWritten")
        val contentLength = source.requiredNonNegativeLong("contentLength")
        val restartRequired = source.requiredExactBoolean("restartRequired")
        val restartScheduled = source.requiredExactBoolean("restartScheduled")
        val allowInsecureHttp = source.requiredExactBoolean("allowInsecureHttp")
        val targetVersion = source.requiredStringAllowEmpty("targetVersion")
        val sha256Expected = source.requiredStringAllowEmpty("sha256Expected")
        val sha256Actual = source.requiredStringAllowEmpty("sha256Actual")
        val urlScheme = source.requiredStringAllowEmpty("urlScheme")
        val startedAtMs = source.requiredNonNegativeLong("startedAtMs")
        val finishedAtMs = source.requiredNonNegativeLong("finishedAtMs")
        val httpStatus = source.requiredExactInt("httpStatus")

        require(progressPermille in 0..1_000)
        require(abs(progressPercent - progressPermille / 10.0) <= 0.11)
        require(contentLength == 0L || bytesWritten <= contentLength)
        require(!allowInsecureHttp)
        require(urlScheme.isEmpty() || urlScheme == "https")
        require(sha256Expected.isEmpty() || sha256Expected.isSha256Hex())
        require(sha256Actual.isEmpty() || sha256Actual.isSha256Hex())
        require(finishedAtMs == 0L || finishedAtMs >= startedAtMs)
        require(httpStatus >= 0)

        val activePhase = phase in ACTIVE_PHASES
        require(active == activePhase)
        require(!restartRequired || phase == DeviceFirmwareOtaPhase.SUCCEEDED)
        require(!restartScheduled || restartRequired)
        if (active || phase.isTerminal) require(targetVersion.isNotBlank())

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

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        require(keySetExact() == expected) { "$label keys differ from the firmware contract." }
    }

    private fun JSONObject.keySetExact(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
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

    private fun JSONObject.requiredPositiveInt(key: String): Int =
        requiredExactInt(key).also { require(it > 0) }

    private fun JSONObject.requiredNonNegativeLong(key: String): Long {
        val value = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble())
        require(asLong >= 0L)
        return asLong
    }

    private fun JSONObject.requiredExactDouble(key: String): Double {
        val value = get(key) as? Number ?: error("$key must be numeric.")
        return value.toDouble().also { require(it.isFinite()) }
    }

    private val ACTIVE_PHASES = setOf(
        DeviceFirmwareOtaPhase.STARTING,
        DeviceFirmwareOtaPhase.SAFE_MODE,
        DeviceFirmwareOtaPhase.DOWNLOADING,
        DeviceFirmwareOtaPhase.WRITING,
        DeviceFirmwareOtaPhase.VERIFYING
    )

    private val FIRMWARE_STATUS_KEYS = setOf(
        "version", "build", "hardwareRevision", "sdkVersion", "uptimeMs",
        "product", "flash", "partition", "ota", "runtime"
    )
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "family", "model", "displayName", "skuCode"
    )
    private val FLASH_KEYS = setOf("chipSize", "sketchSize", "freeSketchSpace")
    private val PARTITION_STATUS_KEYS = setOf(
        "running", "boot", "nextUpdate", "bootMatchesRunning", "runningState",
        "runningStateCode", "stateReadOk", "stateReadError"
    )
    private val PARTITION_INFO_KEYS = setOf(
        "present", "label", "address", "size", "type", "subtype"
    )
    private val FIRMWARE_OTA_ROOT_KEYS = setOf(
        "supported", "transport", "binaryTransfer", "progressEvent", "completedEvent",
        "startCommand", "statusCommand", "status"
    )
    private val FIRMWARE_RUNTIME_KEYS = setOf(
        "transport", "wsSchema", "wsProtocolVersion", "readOnly"
    )
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
    private val OTA_CLEAR_PREVIOUS_KEYS = setOf(
        "phase", "restartRequired", "restartScheduled", "targetVersion", "lastError",
        "lastErrorField"
    )
    private val OTA_SNAPSHOT_KEYS = setOf(
        "phase", "active", "restartRequired", "restartScheduled", "allowInsecureHttp",
        "startedAtMs", "finishedAtMs", "bytesWritten", "contentLength", "progressPermille",
        "progressPercent", "targetVersion", "sha256Expected", "sha256Actual", "lastError",
        "lastErrorField", "urlScheme", "httpStatus"
    )
    private val OTA_TICK_EVENT_KEYS = OTA_SNAPSHOT_KEYS + setOf(
        "completed", "success", "failed", "runtimeTransport", "binaryTransfer"
    )
    private val OTA_STAGED_EVENT_KEYS = setOf(
        "commandId", "module", "action", "sessionId", "publishedAtMs", "result"
    )
}

data class DeviceFirmwareOtaClearPrevious(
    val phase: DeviceFirmwareOtaPhase,
    val phaseRaw: String,
    val restartRequired: Boolean,
    val restartScheduled: Boolean,
    val targetVersion: String,
    val lastError: String,
    val lastErrorField: String
)

data class DeviceFirmwareOtaClearTypedResult(
    val cleared: Boolean,
    val previous: DeviceFirmwareOtaClearPrevious,
    val ota: DeviceFirmwareOtaSnapshot
)

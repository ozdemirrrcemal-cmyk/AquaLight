package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** Debug-only, redacted evidence for one firmware.ota.start exchange. */
internal object DeviceRuntimeOtaDiagnostics {

    private val reports = ConcurrentHashMap<String, String>()

    fun recordOutgoing(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsOutgoingMessage.Command
    ) {
        if (!BuildConfig.DEBUG || !message.isOtaStart) return
        reports[deviceUid.value] = buildString {
            appendLine("AQUALIGHT_OTA_DIAGNOSTIC_V1")
            appendLine("stage=outgoing")
            appendLine("deviceUid=${deviceUid.value}")
            appendLine("generation=$generation")
            appendLine("messageId=${message.id}")
            appendLine("module=${message.module}")
            appendLine("action=${message.action}")
            append(OtaDiagnosticJsonFormatter.payload(message.data))
        }.trimEnd()
    }

    fun recordIncoming(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage
    ) {
        if (!BuildConfig.DEBUG) return
        val current = reports[deviceUid.value] ?: return
        if (!message.isOtaStart && "messageId=${message.id}" !in current) return
        append(deviceUid.value) {
            appendLine()
            appendLine("stage=incoming")
            appendLine("generation=$generation")
            appendLine("messageId=${message.id}")
            appendLine("module=${message.module}")
            appendLine("action=${message.action}")
            when (message) {
                is AqlWsIncomingMessage.Response -> {
                    appendLine("envelope=response")
                    appendLine("ok=${message.ok}")
                    appendLine("statusCode=${message.statusCode}")
                    append(OtaDiagnosticJsonFormatter.response(message.data))
                }
                is AqlWsIncomingMessage.Error -> {
                    appendLine("envelope=error")
                    appendLine("statusCode=${message.statusCode}")
                    appendLine("code=${OtaDiagnosticJsonFormatter.safe(message.code)}")
                    appendLine("field=${OtaDiagnosticJsonFormatter.safe(message.field)}")
                    appendLine("message=${OtaDiagnosticJsonFormatter.safe(message.message)}")
                    appendLine("dataKeys=${OtaDiagnosticJsonFormatter.keys(message.data)}")
                }
                is AqlWsIncomingMessage.Event -> {
                    appendLine("envelope=event")
                    appendLine("dataKeys=${OtaDiagnosticJsonFormatter.keys(message.data)}")
                }
            }
        }
    }

    fun recordParserFailure(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        messageId: String,
        error: Throwable
    ) {
        if (!BuildConfig.DEBUG) return
        append(deviceUid.value) {
            appendLine()
            appendLine("stage=parse_failure")
            appendLine("generation=$generation")
            appendLine("messageId=$messageId")
            appendLine("exception=${error::class.java.simpleName}")
            appendLine("parserError=${OtaDiagnosticJsonFormatter.safe(error.message)}")
        }
    }

    fun report(deviceUid: String): String =
        if (BuildConfig.DEBUG) reports[deviceUid].orEmpty() else ""

    private fun append(deviceUid: String, block: StringBuilder.() -> Unit) {
        reports.compute(deviceUid) { _, current ->
            buildString {
                append(current.orEmpty())
                block()
            }.takeLast(MAX_REPORT_CHARS)
        }
    }

    private val AqlWsOutgoingMessage.Command.isOtaStart: Boolean
        get() = module == FIRMWARE_MODULE && action == OTA_START_ACTION

    private val AqlWsIncomingMessage.isOtaStart: Boolean
        get() = module == FIRMWARE_MODULE && action == OTA_START_ACTION

    private const val FIRMWARE_MODULE = "firmware"
    private const val OTA_START_ACTION = "ota.start"
    private const val MAX_REPORT_CHARS = 12_000
}

private object OtaDiagnosticJsonFormatter {

    fun payload(data: JSONObject): String = buildString {
        appendLine("payloadKeys=${keys(data)}")
        appendLine("url=${url(data.optString("url"))}")
        appendLine("version=${safe(data.optString("version"))}")
        appendLine("sha256=${digest(data.optString("sha256"))}")
        appendLine("expectedSize=${safe(data.opt("expectedSize"))}")
        appendLine("applyNow=${safe(data.opt("applyNow"))}")
        appendLine("allowInsecureHttp=${safe(data.opt("allowInsecureHttp"))}")
        appendLine("productKey=${safe(data.optString("productKey"))}")
        appendLine("productId=${safe(data.optString("productId"))}")
        appendLine("model=${safe(data.optString("model"))}")
        appendLine("hardwareRevision=${safe(data.optString("hardwareRevision"))}")
    }.trimEnd()

    fun response(data: JSONObject): String = buildString {
        appendLine("dataKeys=${keys(data)}")
        appendRequestSummary(data.optJSONObject("request"))
        appendOtaSummary(data.optJSONObject("ota"))
    }.trimEnd()

    fun keys(data: JSONObject): String = buildList {
        val iterator = data.keys()
        while (iterator.hasNext()) add(iterator.next())
    }.sorted().joinToString(prefix = "[", postfix = "]")

    fun safe(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "<missing>"
        else -> value.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_VALUE_CHARS)
            .ifBlank { "<empty>" }
    }

    private fun StringBuilder.appendRequestSummary(request: JSONObject?) {
        if (request == null) {
            appendLine("request=<missing>")
            return
        }
        appendLine("requestKeys=${keys(request)}")
        REQUEST_FIELDS.forEach { field ->
            appendLine("request.$field=${safe(request.opt(field))}")
        }
    }

    private fun StringBuilder.appendOtaSummary(ota: JSONObject?) {
        if (ota == null) {
            appendLine("ota=<missing>")
            return
        }
        appendLine("otaKeys=${keys(ota)}")
        OTA_FIELDS.forEach { field ->
            val value = if (field in DIGEST_FIELDS) {
                digest(ota.optString(field))
            } else {
                safe(ota.opt(field))
            }
            appendLine("ota.$field=$value")
        }
    }

    private fun url(value: String): String {
        val normalized = value.substringBefore('?').substringBefore('#')
        val scheme = normalized.substringBefore("://", missingDelimiterValue = "")
        val fileName = normalized.substringAfterLast('/', missingDelimiterValue = normalized)
        return safe(
            listOf(scheme, fileName)
                .filter(String::isNotBlank)
                .joinToString(":")
        )
    }

    private fun digest(value: String): String = when {
        value.isBlank() -> "<empty>"
        value.length <= DIGEST_EDGE_CHARS * 2 -> safe(value)
        else -> "${value.take(DIGEST_EDGE_CHARS)}…${value.takeLast(DIGEST_EDGE_CHARS)}"
    }

    private val REQUEST_FIELDS = listOf(
        "urlScheme",
        "version",
        "expectedSize",
        "applyNow",
        "allowInsecureHttp",
        "productKey",
        "productId",
        "model",
        "hardwareRevision"
    )
    private val OTA_FIELDS = listOf(
        "phase",
        "active",
        "targetVersion",
        "contentLength",
        "sha256Expected",
        "sha256Actual",
        "urlScheme",
        "lastErrorField",
        "lastError",
        "httpStatus"
    )
    private val DIGEST_FIELDS = setOf("sha256Expected", "sha256Actual")
    private const val DIGEST_EDGE_CHARS = 8
    private const val MAX_VALUE_CHARS = 240
}

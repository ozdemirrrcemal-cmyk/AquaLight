package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DEVICE_CUSTOM_NAME_MAX_BYTES
import org.json.JSONObject

/** Typed mirror of the authenticated firmware `device.name.set` contract. */
object DeviceNameRuntimeContract {
    const val MODULE = AqlWsContract.MODULE_DEVICE
    const val ACTION_SET = AqlWsContract.ACTION_DEVICE_NAME_SET
    const val STATUS_CHANGED_EVENT = "status.changed"

    object Field {
        const val CUSTOM_NAME = "customName"
        const val SAVE = "save"
    }
}

data class DeviceNameSetPayload(
    val customName: String?,
    val save: Boolean = true
) {
    val normalizedCustomName: String? = customName?.trim()

    init {
        val value = normalizedCustomName.orEmpty()
        require(value.none(Char::isISOControl)) {
            "customName must not contain control characters."
        }
        require(value.toByteArray(Charsets.UTF_8).size <= DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "customName must not exceed $DEVICE_CUSTOM_NAME_MAX_BYTES UTF-8 bytes."
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(
            DeviceNameRuntimeContract.Field.CUSTOM_NAME,
            normalizedCustomName ?: JSONObject.NULL
        )
        .put(DeviceNameRuntimeContract.Field.SAVE, save)
}

data class DeviceNameStatus(
    val productDisplayName: String,
    val customName: String,
    val effectiveDisplayName: String,
    val editable: Boolean,
    val maxBytes: Int
)

data class DeviceNameSetResponse(
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val event: String?,
    val status: DeviceNameStatus
)

object DeviceNameSetResponseParser {
    fun parse(data: JSONObject): Result<DeviceNameSetResponse> = runCatching {
        data.requireKnownKeys(
            required = RESPONSE_REQUIRED_KEYS,
            optional = RESPONSE_OPTIONAL_KEYS,
            label = "device.name.set.data"
        )
        require(data.requireString("operation") == OPERATION) {
            "device.name.set operation is incompatible."
        }
        val changed = data.requireBoolean("changed")
        val saved = data.requireBoolean("saved")
        val saveRequested = data.requireBoolean("saveRequested")
        val event = data.optionalString("event")
        val status = parseStatus(data.requireObject("status"))

        require(!saveRequested || saved) {
            "A successful persistent device-name response must report saved=true."
        }
        require(saveRequested || !saved) {
            "A runtime-only device-name response must report saved=false."
        }
        require((changed && event == DeviceNameRuntimeContract.STATUS_CHANGED_EVENT) ||
            (!changed && event == null)) {
            "device.name.set event does not match the changed flag."
        }

        DeviceNameSetResponse(
            changed = changed,
            saved = saved,
            saveRequested = saveRequested,
            event = event,
            status = status
        )
    }

    private fun parseStatus(status: JSONObject): DeviceNameStatus {
        status.requireExactKeys(STATUS_KEYS, "device.name.set.data.status")
        val productDisplayName = status.requireString("productDisplayName")
        val customName = status.requireOptionalText("customName")
        val effectiveDisplayName = status.requireString("effectiveDisplayName")
        val editable = status.requireBoolean("editable")
        val maxBytes = status.requireInt("maxBytes")

        require(editable) { "Commercial firmware must keep the device name editable." }
        require(maxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "device.name.set maxBytes is incompatible."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            "device.name.set customName exceeds its UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { productDisplayName }) {
            "device.name.set effectiveDisplayName violates the fallback contract."
        }

        return DeviceNameStatus(
            productDisplayName = productDisplayName,
            customName = customName,
            effectiveDisplayName = effectiveDisplayName,
            editable = editable,
            maxBytes = maxBytes
        )
    }

    private const val OPERATION = "deviceNameSet"
    private val RESPONSE_REQUIRED_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "status"
    )
    private val RESPONSE_OPTIONAL_KEYS = setOf("event")
    private val STATUS_KEYS = setOf(
        "productDisplayName", "customName", "effectiveDisplayName", "editable", "maxBytes"
    )
}

private fun JSONObject.requireKnownKeys(
    required: Set<String>,
    optional: Set<String>,
    label: String
) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    val missing = required - actual
    val unknown = actual - required - optional
    require(missing.isEmpty() && unknown.isEmpty()) {
        "$label keys differ from the firmware contract; " +
            "missing=${missing.sorted()} unknown=${unknown.sorted()}"
    }
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    requireKnownKeys(expected, emptySet(), label)
}

private fun JSONObject.requireObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? JSONObject ?: error("$key must be a JSON object.")
}

private fun JSONObject.requireString(key: String): String {
    val value = requireOptionalText(key)
    require(value.isNotEmpty()) { "$key must not be empty." }
    return value
}

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key)) return null
    require(!isNull(key)) { "$key must not be null." }
    return requireString(key)
}

private fun JSONObject.requireOptionalText(key: String): String {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

private fun JSONObject.requireBoolean(key: String): Boolean {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? Boolean ?: error("$key must be a boolean.")
}

private fun JSONObject.requireInt(key: String): Int {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asLong = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
        "$key must be an integer."
    }
    require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$key is outside the supported integer range."
    }
    return asLong.toInt()
}

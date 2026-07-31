package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeDeviceNameStatus
import com.aqua.aqualight.data.devices.model.FIRMWARE_DEVICE_CUSTOM_NAME_MAX_BYTES
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

data class DeviceNameSetRequest(
    val customName: String?,
    val save: Boolean = true
) {
    val normalizedCustomName: String = customName?.trim().orEmpty()

    init {
        require(normalizedCustomName.none(Char::isISOControl)) {
            "customName must not contain control characters."
        }
        require(
            normalizedCustomName.toByteArray(Charsets.UTF_8).size <=
                FIRMWARE_DEVICE_CUSTOM_NAME_MAX_BYTES
        ) {
            "customName exceeds the firmware UTF-8 byte limit."
        }
    }
}

data class DeviceNameSetResult(
    val changed: Boolean,
    val saveRequested: Boolean,
    val saved: Boolean,
    val event: String?,
    val status: DeviceRuntimeDeviceNameStatus
)

class DeviceNameSetCommand(
    private val request: DeviceNameSetRequest
) : DeviceRuntimeCommand<DeviceNameSetResult> {
    override val module: String = AqlWsContract.MODULE_DEVICE
    override val action: String = AqlWsContract.ACTION_DEVICE_NAME_SET

    override fun encodeData(): JSONObject = JSONObject()
        .put(
            FIELD_CUSTOM_NAME,
            request.normalizedCustomName.takeIf(String::isNotEmpty) ?: JSONObject.NULL
        )
        .put(FIELD_SAVE, request.save)

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceNameSetResult {
        require(response.statusCode == 200) { "device.name.set must return status 200." }
        val data = response.data
        val changed = data.requiredBoolean(FIELD_CHANGED)
        val expectedKeys = if (changed) RESULT_KEYS_WITH_EVENT else RESULT_KEYS_WITHOUT_EVENT
        data.requireExactKeys(expectedKeys, "device.name.set.data")
        require(data.requiredString(FIELD_OPERATION) == OPERATION_UPDATED) {
            "device.name.set operation is incompatible."
        }
        val saveRequested = data.requiredBoolean(FIELD_SAVE_REQUESTED)
        val saved = data.requiredBoolean(FIELD_SAVED)
        require(saveRequested == request.save) {
            "device.name.set saveRequested differs from the request."
        }
        require(saved == request.save) {
            "device.name.set persistence result differs from the successful request."
        }
        val event = if (changed) {
            data.requiredString(FIELD_EVENT).also { value ->
                require(value == QUALIFIED_STATUS_CHANGED_EVENT) {
                    "device.name.set event is incompatible."
                }
            }
        } else {
            null
        }
        val statusObject = data.requiredObject(FIELD_STATUS)
        statusObject.requireExactKeys(STATUS_KEYS, "device.name.set.data.status")
        val status = DeviceRuntimeDeviceNameStatus(
            productDisplayName = statusObject.requiredString(FIELD_PRODUCT_DISPLAY_NAME),
            customName = statusObject.requiredOptionalString(FIELD_CUSTOM_NAME),
            effectiveDisplayName = statusObject.requiredString(FIELD_EFFECTIVE_DISPLAY_NAME),
            editable = statusObject.requiredBoolean(FIELD_EDITABLE),
            maxBytes = statusObject.requiredInt(FIELD_MAX_BYTES)
        )
        require(status.editable) { "device.name.set returned a non-editable name policy." }
        require(status.customName == request.normalizedCustomName) {
            "device.name.set returned a different customName."
        }
        return DeviceNameSetResult(
            changed = changed,
            saveRequested = saveRequested,
            saved = saved,
            event = event,
            status = status
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from the pinned firmware contract; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject {
        require(has(key) && !isNull(key)) { "$key is required." }
        return get(key) as? JSONObject ?: error("$key must be a JSON object.")
    }

    private fun JSONObject.requiredString(key: String): String {
        val value = requiredOptionalString(key)
        require(value.isNotEmpty()) { "$key must not be empty." }
        return value
    }

    private fun JSONObject.requiredOptionalString(key: String): String {
        require(has(key) && !isNull(key)) { "$key is required." }
        val value = get(key) as? String ?: error("$key must be a string.")
        if (value.isEmpty()) return value
        require(value == value.trim()) { "$key must not contain surrounding whitespace." }
        require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
        return value
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !isNull(key)) { "$key is required." }
        return get(key) as? Boolean ?: error("$key must be a boolean.")
    }

    private fun JSONObject.requiredInt(key: String): Int {
        require(has(key) && !isNull(key)) { "$key is required." }
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble()) {
            "$key must be an integer."
        }
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "$key is outside the integer range."
        }
        return asLong.toInt()
    }

    private companion object {
        const val FIELD_CUSTOM_NAME = "customName"
        const val FIELD_SAVE = "save"
        const val FIELD_OPERATION = "operation"
        const val FIELD_CHANGED = "changed"
        const val FIELD_SAVE_REQUESTED = "saveRequested"
        const val FIELD_SAVED = "saved"
        const val FIELD_EVENT = "event"
        const val FIELD_STATUS = "status"
        const val FIELD_PRODUCT_DISPLAY_NAME = "productDisplayName"
        const val FIELD_EFFECTIVE_DISPLAY_NAME = "effectiveDisplayName"
        const val FIELD_EDITABLE = "editable"
        const val FIELD_MAX_BYTES = "maxBytes"
        const val OPERATION_UPDATED = "updated"
        const val QUALIFIED_STATUS_CHANGED_EVENT = "device.status.changed"

        val RESULT_KEYS_WITHOUT_EVENT = setOf(
            FIELD_OPERATION,
            FIELD_CHANGED,
            FIELD_SAVE_REQUESTED,
            FIELD_SAVED,
            FIELD_STATUS
        )
        val RESULT_KEYS_WITH_EVENT = RESULT_KEYS_WITHOUT_EVENT + FIELD_EVENT
        val STATUS_KEYS = setOf(
            FIELD_PRODUCT_DISPLAY_NAME,
            FIELD_CUSTOM_NAME,
            FIELD_EFFECTIVE_DISPLAY_NAME,
            FIELD_EDITABLE,
            FIELD_MAX_BYTES
        )
    }
}

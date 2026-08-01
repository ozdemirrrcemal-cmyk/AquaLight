package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.model.DeviceRuntimeNameStatus
import com.aqua.aqualight.data.devices.runtime.core.utf8ByteCount
import org.json.JSONObject

data class DeviceNameSetRequest(
    val customName: String?,
    val save: Boolean = true
) {
    init {
        customName?.let { value ->
            require(value.none(Char::isISOControl)) {
                "customName must not contain control characters."
            }
            require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
                "customName must not contain surrounding whitespace."
            }
            require(value.utf8ByteCount() <= DeviceRuntimeNameStatus.DEVICE_CUSTOM_NAME_MAX_BYTES) {
                "customName exceeds the firmware UTF-8 byte limit."
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(FIELD_CUSTOM_NAME, customName ?: JSONObject.NULL)
        .put(FIELD_SAVE, save)

    private companion object {
        const val FIELD_CUSTOM_NAME = "customName"
        const val FIELD_SAVE = "save"
    }
}

data class DeviceNameSetResult(
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val status: DeviceRuntimeNameStatus
)

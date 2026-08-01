package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeNameStatus
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

object DeviceRuntimeNameStatusParser {
    fun parse(source: JSONObject, label: String): Result<DeviceRuntimeNameStatus> = runCatching {
        DeviceRuntimeJson.requireExactKeys(source, NAME_KEYS, label)
        DeviceRuntimeNameStatus(
            productDisplayName = DeviceRuntimeJson.stringValue(
                source,
                FIELD_PRODUCT_DISPLAY_NAME,
                "$label.$FIELD_PRODUCT_DISPLAY_NAME"
            ),
            customName = DeviceRuntimeJson.stringAllowEmpty(
                source,
                FIELD_CUSTOM_NAME,
                "$label.$FIELD_CUSTOM_NAME"
            ),
            effectiveDisplayName = DeviceRuntimeJson.stringValue(
                source,
                FIELD_EFFECTIVE_DISPLAY_NAME,
                "$label.$FIELD_EFFECTIVE_DISPLAY_NAME"
            ),
            nameEditable = DeviceRuntimeJson.booleanValue(
                source,
                FIELD_NAME_EDITABLE,
                "$label.$FIELD_NAME_EDITABLE"
            ),
            customNameMaxBytes = DeviceRuntimeJson.intValue(
                source,
                FIELD_CUSTOM_NAME_MAX_BYTES,
                "$label.$FIELD_CUSTOM_NAME_MAX_BYTES"
            )
        )
    }

    fun parseFlat(source: JSONObject, label: String): Result<DeviceRuntimeNameStatus> = runCatching {
        DeviceRuntimeNameStatus(
            productDisplayName = DeviceRuntimeJson.stringValue(
                source,
                FIELD_DISPLAY_NAME,
                "$label.$FIELD_DISPLAY_NAME"
            ),
            customName = DeviceRuntimeJson.stringAllowEmpty(
                source,
                FIELD_CUSTOM_NAME,
                "$label.$FIELD_CUSTOM_NAME"
            ),
            effectiveDisplayName = DeviceRuntimeJson.stringValue(
                source,
                FIELD_EFFECTIVE_DISPLAY_NAME,
                "$label.$FIELD_EFFECTIVE_DISPLAY_NAME"
            ),
            nameEditable = DeviceRuntimeJson.booleanValue(
                source,
                FIELD_NAME_EDITABLE,
                "$label.$FIELD_NAME_EDITABLE"
            ),
            customNameMaxBytes = DeviceRuntimeJson.intValue(
                source,
                FIELD_CUSTOM_NAME_MAX_BYTES,
                "$label.$FIELD_CUSTOM_NAME_MAX_BYTES"
            )
        )
    }

    private companion object {
        const val FIELD_PRODUCT_DISPLAY_NAME = "productDisplayName"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_CUSTOM_NAME = "customName"
        const val FIELD_EFFECTIVE_DISPLAY_NAME = "effectiveDisplayName"
        const val FIELD_NAME_EDITABLE = "nameEditable"
        const val FIELD_CUSTOM_NAME_MAX_BYTES = "customNameMaxBytes"

        val NAME_KEYS = setOf(
            FIELD_PRODUCT_DISPLAY_NAME,
            FIELD_CUSTOM_NAME,
            FIELD_EFFECTIVE_DISPLAY_NAME,
            FIELD_NAME_EDITABLE,
            FIELD_CUSTOM_NAME_MAX_BYTES
        )
    }
}

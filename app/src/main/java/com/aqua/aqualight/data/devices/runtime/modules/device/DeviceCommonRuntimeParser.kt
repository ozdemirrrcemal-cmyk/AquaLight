package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.repository.DeviceRuntimeNameStatusParser
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceCommonRuntimeParser {
    fun parseNameSet(data: JSONObject): DeviceNameSetResult {
        val changed = DeviceRuntimeJson.booleanValue(data, FIELD_CHANGED)
        val expectedKeys = if (changed) CHANGED_KEYS else UNCHANGED_KEYS
        DeviceRuntimeJson.requireExactKeys(data, expectedKeys, LABEL)
        require(DeviceRuntimeJson.stringValue(data, FIELD_OPERATION) == OPERATION_NAME_SET)
        if (changed) {
            require(DeviceRuntimeJson.stringValue(data, FIELD_EVENT) == EVENT_DEVICE_STATUS_CHANGED)
        }
        val saveRequested = DeviceRuntimeJson.booleanValue(data, FIELD_SAVE_REQUESTED)
        val saved = DeviceRuntimeJson.booleanValue(data, FIELD_SAVED)
        require(!saved || saveRequested) { "device.name.set cannot save when save was not requested." }
        return DeviceNameSetResult(
            changed = changed,
            saved = saved,
            saveRequested = saveRequested,
            status = DeviceRuntimeNameStatusParser.parse(
                DeviceRuntimeJson.objectValue(data, FIELD_STATUS),
                "$LABEL.$FIELD_STATUS"
            ).getOrThrow()
        )
    }

    private const val LABEL = "device.name.set.data"
    private const val FIELD_OPERATION = "operation"
    private const val FIELD_CHANGED = "changed"
    private const val FIELD_SAVED = "saved"
    private const val FIELD_SAVE_REQUESTED = "saveRequested"
    private const val FIELD_EVENT = "event"
    private const val FIELD_STATUS = "status"
    private const val OPERATION_NAME_SET = "nameSet"
    private const val EVENT_DEVICE_STATUS_CHANGED = "device.status.changed"

    private val UNCHANGED_KEYS = setOf(
        FIELD_OPERATION,
        FIELD_CHANGED,
        FIELD_SAVED,
        FIELD_SAVE_REQUESTED,
        FIELD_STATUS
    )
    private val CHANGED_KEYS = UNCHANGED_KEYS + FIELD_EVENT
}

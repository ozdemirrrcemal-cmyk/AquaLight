package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.contract.AqlWsEventContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

data class DeviceRuntimeTypedEvent(
    val deviceUid: DeviceUid,
    val generation: DeviceRuntimeConnectionGeneration,
    val messageId: String,
    val type: Type,
    val payload: DeviceRuntimeEventPayload
) {
    val module: String
        get() = type.module

    val action: String
        get() = type.action

    enum class Type(
        val module: String,
        val action: String
    ) {
        DEVICE_STATUS_CHANGED(
            AqlWsContract.MODULE_DEVICE,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        NETWORK_STATE_CHANGED(
            AqlWsContract.MODULE_NETWORK,
            AqlWsEventContract.ACTION_NETWORK_STATE_CHANGED
        ),
        LIGHT_STATUS_CHANGED(
            AqlWsContract.MODULE_LIGHT,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        COOLING_STATUS_CHANGED(
            AqlWsContract.MODULE_COOLING,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        TIMER_STATUS_CHANGED(
            AqlWsContract.MODULE_TIMER,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        DOSING_STATUS_CHANGED(
            AqlWsContract.MODULE_DOSING,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        TEMPERATURE_CHANGED(
            AqlWsEventContract.MODULE_TEMPERATURE,
            AqlWsEventContract.ACTION_TEMPERATURE_CHANGED
        ),
        TIME_STATUS_CHANGED(
            AqlWsContract.MODULE_TIME,
            AqlWsEventContract.ACTION_STATUS_CHANGED
        ),
        FIRMWARE_OTA_PROGRESS(
            AqlWsContract.MODULE_FIRMWARE,
            AqlWsEventContract.ACTION_OTA_PROGRESS
        ),
        FIRMWARE_OTA_COMPLETED(
            AqlWsContract.MODULE_FIRMWARE,
            AqlWsEventContract.ACTION_OTA_COMPLETED
        ),
        SYSTEM_RESTARTING(
            AqlWsContract.MODULE_SYSTEM,
            AqlWsEventContract.ACTION_SYSTEM_RESTARTING
        );

        companion object {
            fun from(module: String, action: String): Type? = values().firstOrNull { type ->
                type.module == module && type.action == action
            }
        }
    }
}

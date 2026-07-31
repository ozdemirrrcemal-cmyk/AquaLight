package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract

internal object DeviceRuntimeRefreshPolicy {

    fun mutationTargets(module: String, action: String): Set<DeviceRuntimeRefreshTarget> {
        if (action == AqlWsContract.ACTION_STATUS_GET) return emptySet()
        return when (module) {
            AqlWsContract.MODULE_DEVICE -> setOf(DeviceRuntimeRefreshTarget.DEVICE)
            AqlWsContract.MODULE_SECURITY -> setOf(DeviceRuntimeRefreshTarget.SECURITY)
            AqlWsContract.MODULE_NETWORK -> setOf(DeviceRuntimeRefreshTarget.NETWORK)
            AqlWsContract.MODULE_TIME -> setOf(DeviceRuntimeRefreshTarget.TIME)
            AqlWsContract.MODULE_FIRMWARE -> setOf(
                DeviceRuntimeRefreshTarget.FIRMWARE,
                DeviceRuntimeRefreshTarget.OTA
            )
            AqlWsContract.MODULE_LIGHT -> setOf(DeviceRuntimeRefreshTarget.LIGHT)
            AqlWsContract.MODULE_COOLING -> setOf(DeviceRuntimeRefreshTarget.COOLING)
            AqlWsContract.MODULE_TIMER -> setOf(DeviceRuntimeRefreshTarget.TIMER)
            AqlWsContract.MODULE_DOSING -> setOf(DeviceRuntimeRefreshTarget.DOSING)
            else -> emptySet()
        }
    }

    fun eventTargets(action: String): Set<DeviceRuntimeRefreshTarget> = when (action) {
        AqlWsContract.Event.DEVICE_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.DEVICE)
        AqlWsContract.Event.NETWORK_STATE_CHANGED -> setOf(DeviceRuntimeRefreshTarget.NETWORK)
        AqlWsContract.Event.LIGHT_STATUS_CHANGED -> setOf(
            DeviceRuntimeRefreshTarget.LIGHT,
            DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION
        )
        AqlWsContract.Event.COOLING_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.COOLING)
        AqlWsContract.Event.TIMER_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.TIMER)
        AqlWsContract.Event.DOSING_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.DOSING)
        AqlWsContract.Event.TIME_STATUS_CHANGED -> setOf(DeviceRuntimeRefreshTarget.TIME)
        AqlWsContract.Event.FIRMWARE_OTA_PROGRESS,
        AqlWsContract.Event.FIRMWARE_OTA_COMPLETED -> setOf(DeviceRuntimeRefreshTarget.OTA)
        else -> emptySet()
    }
}

package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet

internal fun DeviceCapabilitySet.toRootCapabilities(): Set<DeviceRootCapability> = buildSet {
    if (manualLight) add(DeviceRootCapability.MANUAL_LIGHT)
    if (lightProgram) add(DeviceRootCapability.LIGHT_PROGRAM)
    if (lightPresets) add(DeviceRootCapability.LIGHT_PRESETS)
    if (lightSimulation) add(DeviceRootCapability.LIGHT_SIMULATION)
    if (dosing) add(DeviceRootCapability.DOSING)
    if (standaloneTimer) add(DeviceRootCapability.STANDALONE_TIMER)
    if (cooling) add(DeviceRootCapability.COOLING)
    if (fan) add(DeviceRootCapability.FAN)
    if (temperature) add(DeviceRootCapability.TEMPERATURE)
    if (timeSync) add(DeviceRootCapability.TIME_SYNC)
    if (ota) add(DeviceRootCapability.OTA)
}

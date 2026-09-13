package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceSettingsOperations
import com.aqua.aqualight.application.devices.light.protection.DeviceLightProtectionOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository

/**
 * Owner-scoped composition root for the shared family Settings destination.
 *
 * Each delegated adapter keeps one application responsibility. The presentation facade remains a
 * stable screen contract without widening the root-screen adapter.
 */
internal class DefaultDeviceFamilySettingsOperations(
    devicesRepository: DevicesRepository,
    lightProtectionOperations: DeviceLightProtectionOperations
) : DeviceFamilySettingsOperations,
    DeviceRootOperations by DefaultDeviceRootOperations(devicesRepository),
    DeviceSettingsOperations by DefaultDeviceSettingsOperations(devicesRepository),
    DeviceLightProtectionOperations by lightProtectionOperations

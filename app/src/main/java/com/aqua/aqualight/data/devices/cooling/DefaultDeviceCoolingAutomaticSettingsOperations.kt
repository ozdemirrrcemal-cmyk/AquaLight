package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository

/**
 * Composition shell kept while Cooling is intentionally disconnected.
 *
 * It contains no legacy wire model, parser, payload or repository behavior.
 */
internal class DefaultDeviceCoolingAutomaticSettingsOperations(
    @Suppress("UNUSED_PARAMETER") devicesRepository: DevicesRepository
) : DeviceCoolingAutomaticSettingsOperations by
    DisconnectedDeviceCoolingAutomaticSettingsOperations

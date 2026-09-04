package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository

/** Composition shell with no Cooling transport until strict V1 wiring is connected. */
internal class DefaultDeviceCoolingTemperatureHistoryOperations(
    @Suppress("UNUSED_PARAMETER") devicesRepository: DevicesRepository
) : DeviceCoolingTemperatureHistoryOperations by DisconnectedDeviceCoolingOperations

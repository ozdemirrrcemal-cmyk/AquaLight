package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.cooling.DisconnectedDeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository

/** Composition shell with no Cooling transport until strict V1 wiring is connected. */
internal class DefaultDeviceCoolingControlOperations(
    @Suppress("UNUSED_PARAMETER") devicesRepository: DevicesRepository
) : DeviceCoolingControlOperations by DisconnectedDeviceCoolingControlOperations

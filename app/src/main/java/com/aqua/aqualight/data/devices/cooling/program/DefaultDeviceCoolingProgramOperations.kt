package com.aqua.aqualight.data.devices.cooling.program

import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import com.aqua.aqualight.data.devices.cooling.DisconnectedDeviceCoolingOperations

/** Composition shell with no Cooling transport until strict V1 wiring is connected. */
class DefaultDeviceCoolingProgramOperations :
    DeviceCoolingProgramOperations by DisconnectedDeviceCoolingOperations

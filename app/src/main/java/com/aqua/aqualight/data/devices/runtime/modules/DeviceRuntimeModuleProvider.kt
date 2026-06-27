package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient

class DeviceRuntimeModuleProvider(
    commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    val time: DeviceTimeRuntimeRepository =
        DeviceTimeRuntimeRepository(commandClientProvider)

    val timer: DeviceTimerRuntimeRepository =
        DeviceTimerRuntimeRepository(commandClientProvider)

    val cooling: DeviceCoolingRuntimeRepository =
        DeviceCoolingRuntimeRepository(commandClientProvider)

    val dosing: DeviceDosingRuntimeRepository =
        DeviceDosingRuntimeRepository(commandClientProvider)

    val light: DeviceLightRuntimeRepository =
        DeviceLightRuntimeRepository(commandClientProvider)
}

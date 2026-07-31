package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdateRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient

class DeviceRuntimeModuleProvider(
    commandClientProvider: (DeviceUid) -> AqlWsCommandClient?,
    commandGateway: DeviceRuntimeCommandGateway,
    onOwnershipCredentialInvalidated:
        suspend (DeviceUid, DeviceRuntimeConnectionGeneration) -> Unit
) {
    val security: DeviceSecurityRuntimeRepository = DeviceSecurityRuntimeRepository(
        commandGateway = commandGateway,
        onOwnershipCredentialInvalidated = onOwnershipCredentialInvalidated
    )

    val network: DeviceNetworkRuntimeRepository = DeviceNetworkRuntimeRepository(commandGateway)
    val time: DeviceTimeRuntimeRepository = DeviceTimeRuntimeRepository(commandGateway)
    val light: DeviceLightRuntimeRepository = DeviceLightRuntimeRepository(commandGateway)
    val lightTemperatureProtection: DeviceLightTemperatureProtectionRuntimeRepository =
        DeviceLightTemperatureProtectionRuntimeRepository(commandGateway)

    val firmware: DeviceFirmwareRuntimeRepository =
        DeviceFirmwareRuntimeRepository(commandClientProvider)
    val firmwareUpdate: DeviceFirmwareUpdateRepository =
        DeviceFirmwareUpdateRepository(firmware)
    val timer: DeviceTimerRuntimeRepository =
        DeviceTimerRuntimeRepository(commandClientProvider)
    val cooling: DeviceCoolingRuntimeRepository =
        DeviceCoolingRuntimeRepository(commandClientProvider)
    val dosing: DeviceDosingRuntimeRepository =
        DeviceDosingRuntimeRepository(commandClientProvider)
}

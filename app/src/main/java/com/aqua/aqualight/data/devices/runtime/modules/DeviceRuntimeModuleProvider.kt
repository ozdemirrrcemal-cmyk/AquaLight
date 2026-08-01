package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceCommonRuntimeRepository
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

/** Common modules use the correlated broker; later product stages retain the legacy client. */
class DeviceRuntimeModuleProvider(
    commandGateway: DeviceRuntimeCommandGateway,
    commandClientProvider: (DeviceUid) -> AqlWsCommandClient?,
    revokeLocalCredential: suspend (DeviceUid) -> Result<Unit>
) {
    val device = DeviceCommonRuntimeRepository(commandGateway)
    val security = DeviceSecurityRuntimeRepository(commandGateway, revokeLocalCredential)
    val network = DeviceNetworkRuntimeRepository(commandGateway)
    val time = DeviceTimeRuntimeRepository(commandGateway)

    val firmware = DeviceFirmwareRuntimeRepository(commandGateway, commandClientProvider)
    val firmwareUpdate = DeviceFirmwareUpdateRepository(firmware)

    val timer = DeviceTimerRuntimeRepository(commandClientProvider)
    val cooling = DeviceCoolingRuntimeRepository(commandClientProvider)
    val dosing = DeviceDosingRuntimeRepository(commandClientProvider)
    val light = DeviceLightRuntimeRepository(commandClientProvider)
    val lightTemperatureProtection =
        DeviceLightTemperatureProtectionRuntimeRepository(commandClientProvider)
}

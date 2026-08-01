package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceCommonRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdateRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient

/** Common and Light modules use the correlated broker; later product stages retain legacy clients. */
class DeviceRuntimeModuleProvider(
    commandGateway: DeviceRuntimeCommandGateway,
    commandClientProvider: (DeviceUid) -> AqlWsCommandClient?,
    revokeLocalCredential: suspend (DeviceUid) -> Result<Unit>
) {
    private val lightStateStore = DeviceLightRuntimeStateStore()

    val device = DeviceCommonRuntimeRepository(commandGateway)
    val security = DeviceSecurityRuntimeRepository(commandGateway, revokeLocalCredential)
    val network = DeviceNetworkRuntimeRepository(commandGateway)
    val time = DeviceTimeRuntimeRepository(commandGateway)

    val firmware = DeviceFirmwareRuntimeRepository(commandGateway, commandClientProvider)
    val firmwareUpdate = DeviceFirmwareUpdateRepository(firmware)

    val timer = DeviceTimerRuntimeRepository(commandClientProvider)
    val cooling = DeviceCoolingRuntimeRepository(commandClientProvider)
    val dosing = DeviceDosingRuntimeRepository(commandClientProvider)
    val light = DeviceLightRuntimeRepository(commandGateway, lightStateStore)
    val lightTemperatureProtection =
        DeviceLightTemperatureProtectionRuntimeRepository(commandGateway, lightStateStore)

    internal fun acceptTypedRuntimeEvent(event: DeviceRuntimeTypedEvent) {
        light.applyTypedEvent(event)
    }

    internal fun clearRuntimeState(deviceUid: DeviceUid) {
        light.clearState(deviceUid)
    }
}

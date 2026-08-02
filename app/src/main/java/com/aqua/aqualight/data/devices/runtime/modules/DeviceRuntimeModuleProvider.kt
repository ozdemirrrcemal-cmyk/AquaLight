package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceCommonRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdateRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient

/** Common, Light, Cooling and Timer modules use the correlated broker. */
class DeviceRuntimeModuleProvider internal constructor(
    commandGateway: DeviceRuntimeCommandGateway,
    commandClientProvider: (DeviceUid) -> AqlWsCommandClient?,
    revokeLocalCredential: suspend (DeviceUid) -> Result<Unit>,
    timerAccessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    private val lightStateStore = DeviceLightRuntimeStateStore()
    private val lightEventReducer = DeviceLightTypedEventReducer(lightStateStore)
    private val coolingStateStore = DeviceCoolingRuntimeStateStore()
    private val coolingEventReducer = DeviceCoolingTypedEventReducer(coolingStateStore)
    private val timerStateStore = DeviceTimerRuntimeStateStore()
    private val timerEventReducer = DeviceTimerTypedEventReducer(
        timerStateStore,
        timerAccessProvider
    )

    val device = DeviceCommonRuntimeRepository(commandGateway)
    val security = DeviceSecurityRuntimeRepository(commandGateway, revokeLocalCredential)
    val network = DeviceNetworkRuntimeRepository(commandGateway)
    val time = DeviceTimeRuntimeRepository(commandGateway)

    val firmware = DeviceFirmwareRuntimeRepository(commandGateway, commandClientProvider)
    val firmwareUpdate = DeviceFirmwareUpdateRepository(firmware)

    val timer = DeviceTimerRuntimeRepository(commandGateway, timerStateStore, timerAccessProvider)
    val cooling = DeviceCoolingRuntimeRepository(commandGateway, coolingStateStore)
    val dosing = DeviceDosingRuntimeRepository(commandClientProvider)
    val light = DeviceLightRuntimeRepository(commandGateway, lightStateStore)
    val lightTemperatureProtection =
        DeviceLightTemperatureProtectionRuntimeRepository(commandGateway, lightStateStore)

    internal fun acceptTypedRuntimeEvent(event: DeviceRuntimeTypedEvent) {
        lightEventReducer.apply(event)
        coolingEventReducer.apply(event)
        timerEventReducer.apply(event)
    }

    internal fun clearRuntimeState(deviceUid: DeviceUid) {
        lightStateStore.clear(deviceUid)
        coolingStateStore.clear(deviceUid)
        timerStateStore.clear(deviceUid)
    }
}

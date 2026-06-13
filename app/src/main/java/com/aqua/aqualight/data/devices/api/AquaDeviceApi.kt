package com.aqua.aqualight.data.devices.api

import com.aqua.aqualight.data.devices.api.cooling.CoolingApi
import com.aqua.aqualight.data.devices.api.dosing.DosingApi
import com.aqua.aqualight.data.devices.api.light.LightApi
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.api.timer.TimerApi

sealed interface AquaDeviceApi {
    val identity: DeviceIdentity
    val connection: AquaDeviceConnection
    val mode: DeviceApiMode
    val capabilities: DeviceApiCapabilities
}

data class AquaLightDeviceApi(
    override val identity: DeviceIdentity,
    override val connection: AquaDeviceConnection,
    override val mode: DeviceApiMode,
    override val capabilities: DeviceApiCapabilities,
    val lightApi: LightApi
) : AquaDeviceApi

data class AquaTimerDeviceApi(
    override val identity: DeviceIdentity,
    override val connection: AquaDeviceConnection,
    override val mode: DeviceApiMode,
    override val capabilities: DeviceApiCapabilities,
    val timerApi: TimerApi
) : AquaDeviceApi

data class AquaDosingDeviceApi(
    override val identity: DeviceIdentity,
    override val connection: AquaDeviceConnection,
    override val mode: DeviceApiMode,
    override val capabilities: DeviceApiCapabilities,
    val dosingApi: DosingApi
) : AquaDeviceApi

data class AquaCoolingDeviceApi(
    override val identity: DeviceIdentity,
    override val connection: AquaDeviceConnection,
    override val mode: DeviceApiMode,
    override val capabilities: DeviceApiCapabilities,
    val coolingApi: CoolingApi
) : AquaDeviceApi

package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightCoolingControllerState
import com.aqua.aqualight.data.devices.api.light.LightDeviceState
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.api.light.LightNextEvent
import com.aqua.aqualight.data.devices.api.light.LightPwmChannelState
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.api.light.LightTemperatureSensorState
import com.aqua.aqualight.data.devices.api.light.LightThermalProtectionState
import com.aqua.aqualight.data.devices.api.light.LightTimeState

data class LightRuntimeSnapshot(
    val mode: LightMode = LightMode.UNKNOWN,
    val isPowerOn: Boolean = false,
    val outputPercent: Int = 0,
    val channels: LightChannelValues = LightChannelValues(),
    val currentWatt: Double? = null,
    val maxWatt: Double? = null,
    val deviceTime: LightTimeState = LightTimeState(),
    val nextEvent: LightNextEvent? = null,
    val temperatureSensors: List<LightTemperatureSensorState> = emptyList(),
    val coolingControllers: List<LightCoolingControllerState> = emptyList(),
    val ledPwmChannels: List<LightPwmChannelState> = emptyList(),
    val fanPwmChannels: List<LightPwmChannelState> = emptyList(),
    val scheduleChannels: List<LightScheduleChannelState> = emptyList(),
    val thermalProtection: LightThermalProtectionState = LightThermalProtectionState(),
    val source: LightRuntimeSource = LightRuntimeSource.NONE,
    val rawDeviceState: LightDeviceState? = null
) {
    companion object {
        fun fromDeviceState(
            state: LightDeviceState,
            source: LightRuntimeSource
        ): LightRuntimeSnapshot {
            return LightRuntimeSnapshot(
                mode = state.status.mode,
                isPowerOn = state.status.isPowerOn,
                outputPercent = state.status.outputPercent,
                channels = state.channels,
                currentWatt = state.status.currentWatt,
                maxWatt = state.status.maxWatt,
                deviceTime = state.time,
                nextEvent = state.nextEvent,
                temperatureSensors = state.temperatureSensors,
                coolingControllers = state.coolingControllers,
                ledPwmChannels = state.ledChannels,
                fanPwmChannels = state.fanChannels,
                scheduleChannels = state.scheduleChannels,
                thermalProtection = state.thermalProtection,
                source = source,
                rawDeviceState = state
            )
        }
    }
}

enum class LightRuntimeSource {
    LEGACY,
    V1,
    NONE
}

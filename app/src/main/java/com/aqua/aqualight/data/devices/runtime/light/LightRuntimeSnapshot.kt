package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.light.LightChannelRole
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
import com.aqua.aqualight.data.devices.light.math.LightPowerMath
import com.aqua.aqualight.data.devices.light.math.LightRgbwPowerCalibration

data class LightRuntimeSnapshot(
    val mode: LightMode = LightMode.UNKNOWN,
    val isPowerOn: Boolean = false,
    val outputPercent: Int = 0,
    val channels: LightChannelValues = LightChannelValues(),
    val currentWatt: Double? = null,
    val maxWatt: Double? = null,
    val powerLoadPercent: Int? = null,
    val powerCalibration: LightRgbwPowerCalibration? = null,
    val fanOutputPercent: Int? = null,
    val deviceTime: LightTimeState = LightTimeState(),
    val nextEvent: LightNextEvent? = null,
    val temperatureSensors: List<LightTemperatureSensorState> = emptyList(),
    val coolingControllers: List<LightCoolingControllerState> = emptyList(),
    val ledPwmChannels: List<LightPwmChannelState> = emptyList(),
    val fanPwmChannels: List<LightPwmChannelState> = emptyList(),
    val scheduleChannels: List<LightScheduleChannelState> = emptyList(),
    val thermalProtection: LightThermalProtectionState = LightThermalProtectionState(),
    val activeSceneName: String? = null,
    val activeSceneSource: String? = null,
    val localOverride: LightLocalOverrideState? = null,
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
                outputPercent = state.channels.maxPercent,
                channels = state.channels,
                currentWatt = state.status.currentWatt,
                maxWatt = state.status.maxWatt,
                powerLoadPercent = state.status.powerLoadPercent ?: LightPowerMath.powerLoadPercent(
                    currentWatt = state.status.currentWatt,
                    maxWatt = state.status.maxWatt
                ),
                powerCalibration = state.ledChannels.toRgbwPowerCalibration(),
                fanOutputPercent = state.status.fanOutputPercent,
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


private fun List<LightPwmChannelState>.toRgbwPowerCalibration(): LightRgbwPowerCalibration? {
    fun maxWattFor(role: LightChannelRole): Double? {
        val total = filter { channel ->
            channel.role == role
        }.mapNotNull { channel ->
            channel.maxWatt?.takeIf { it > 0.0 }
        }.sum()

        return total.takeIf { it > 0.0 }
    }

    val calibration = LightRgbwPowerCalibration(
        redMaxWatt = maxWattFor(LightChannelRole.RED),
        greenMaxWatt = maxWattFor(LightChannelRole.GREEN),
        blueMaxWatt = maxWattFor(LightChannelRole.BLUE),
        whiteMaxWatt = maxWattFor(LightChannelRole.WHITE)
    )

    return calibration.takeIf { it.hasChannelWattData }
}

enum class LightRuntimeSource {
    V1,
    NONE
}

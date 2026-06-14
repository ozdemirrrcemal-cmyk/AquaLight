package com.aqua.aqualight.data.devices.api.light

import kotlin.math.roundToInt

data class LightStatus(
    val mode: LightMode = LightMode.UNKNOWN,
    val isPowerOn: Boolean = false,
    val outputPercent: Int = 0,
    val redPercent: Int = 0,
    val greenPercent: Int = 0,
    val bluePercent: Int = 0,
    val whitePercent: Int = 0,
    val deviceTimeText: String = "",
    val temperatureCelsius: Double? = null,
    val currentWatt: Double? = null,
    val maxWatt: Double? = null,
    val thermalReductionPercent: Int? = null,
    val fanOutputPercent: Int? = null
)

enum class LightMode {
    AUTO,
    MANUAL,
    SCENE,
    MOONLIGHT,
    IDLE,
    UNKNOWN
}

data class LightChannelValues(
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0
) {
    fun normalized(): LightChannelValues {
        return copy(
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100)
        )
    }

    val maxPercent: Int
        get() = maxOf(red, green, blue, white)

    val isOff: Boolean
        get() = red <= 0 && green <= 0 && blue <= 0 && white <= 0
}

data class LightProgram(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val startMinute: Int,
    val peakStartMinute: Int,
    val peakEndMinute: Int,
    val endMinute: Int,
    val channelValues: LightChannelValues,
    val repeatDays: Set<Int> = emptySet()
)

data class LightManualRequest(
    val powerOn: Boolean,
    val channelValues: LightChannelValues
)

data class LightAutomationRequest(
    val moonlightEnabled: Boolean = false,
    val cloudSimulationEnabled: Boolean = false
)

data class LightDeviceState(
    val status: LightStatus = LightStatus(),
    val channels: LightChannelValues = LightChannelValues(),
    val ledChannels: List<LightPwmChannelState> = emptyList(),
    val fanChannels: List<LightPwmChannelState> = emptyList(),
    val scheduleChannels: List<LightScheduleChannelState> = emptyList(),
    val temperatureSensors: List<LightTemperatureSensorState> = emptyList(),
    val coolingControllers: List<LightCoolingControllerState> = emptyList(),
    val time: LightTimeState = LightTimeState(),
    val thermalProtection: LightThermalProtectionState = LightThermalProtectionState(),
    val nextEvent: LightNextEvent? = null,
    val legacyIp: String = "",
    val legacyRawPayload: String = ""
)

data class LightPwmChannelState(
    val index: Int,
    val name: String = "",
    val role: LightChannelRole = LightChannelRole.UNKNOWN,
    val regime: LightPwmRegime = LightPwmRegime.UNKNOWN,
    val gpioPwm: String = "",
    val color: Long? = null,
    val lumen: Double? = null,
    val lux: Double? = null,
    val maxWatt: Double? = null,
    val group: Int? = null,
    val currentValue: Double? = null,
    val currentPercent: Int? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val isInverted: Boolean = false,
    val pca9685Status: String = "",
    val frequency: Int? = null
)

enum class LightChannelRole {
    RED,
    GREEN,
    BLUE,
    WHITE,
    FAN,
    UNKNOWN
}

enum class LightPwmRegime {
    AUTO,
    ON,
    OFF,
    UNKNOWN
}

data class LightScheduleChannelState(
    val index: Int,
    val role: LightChannelRole = LightChannelRole.UNKNOWN,
    val gpioPwm: String = "",
    val points: List<LightSchedulePoint> = emptyList()
)

data class LightSchedulePoint(
    val minuteOfDay: Int,
    val timeText: String,
    val value: Double,
    val percent: Int
)

data class LightNextEvent(
    val minuteOfDay: Int,
    val timeText: String,
    val label: String
)

data class LightTemperatureSensorState(
    val index: Int,
    val name: String = "",
    val color: Long? = null,
    val temperatureCelsius: Double? = null,
    val lightLimitCelsius: Double? = null,
    val historyRaw: String = ""
)

data class LightCoolingControllerState(
    val index: Int,
    val enabled: Boolean = false,
    val name: String = "",
    val gpioPwm: String = "",
    val sensorIndexes: List<Int> = emptyList(),
    val startCelsius: Double? = null,
    val fullSpeedCelsius: Double? = null,
    val currentTemperatureCelsius: Double? = null,
    val linkedFanChannel: LightPwmChannelState? = null
)

data class LightTimeState(
    val currentText: String = "",
    val currentMinuteOfDay: Int? = null,
    val uptimeText: String = "",
    val timeZoneMinutes: Int? = null,
    val autoSyncNtpEnabled: Boolean? = null,
    val autoSyncGadgetEnabled: Boolean? = null,
    val ds1307Status: String = "",
    val pcf8563Status: String = ""
)

data class LightThermalProtectionState(
    val reductionFactor: Double? = null,
    val reductionPercent: Int? = null,
    val lightDownErrPercent: Int? = null,
    val recoveryIntervalSeconds: Int? = null,
    val limitCelsius: Double? = null
)

object LightApiMath {

    fun valueToPercent(
        value: Double?
    ): Int? {
        if (value == null || value.isNaN()) {
            return null
        }

        val percent = if (value <= 1.0001) {
            value * 100.0
        } else {
            value
        }

        return percent.roundToInt().coerceIn(0, 100)
    }

    fun percentToDeviceValue(
        percent: Int
    ): Double {
        return percent.coerceIn(0, 100) / 100.0
    }

    fun calculateCurrentWatt(
        channels: List<LightPwmChannelState>
    ): Double? {
        val wattValues = channels.mapNotNull { channel ->
            val maxWatt = channel.maxWatt ?: return@mapNotNull null
            val percent = channel.currentPercent ?: return@mapNotNull null
            maxWatt * percent.coerceIn(0, 100) / 100.0
        }

        return wattValues.takeIf { it.isNotEmpty() }?.sum()
    }

    fun calculateMaxWatt(
        configuredMaxWatt: Double?,
        channels: List<LightPwmChannelState>
    ): Double? {
        if (configuredMaxWatt != null && configuredMaxWatt > 0.0) {
            return configuredMaxWatt
        }

        val channelMax = channels.mapNotNull { channel ->
            channel.maxWatt?.takeIf { it > 0.0 }
        }.sum()

        return channelMax.takeIf { it > 0.0 }
    }
}

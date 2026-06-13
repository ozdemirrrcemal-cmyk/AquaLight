package com.aqua.aqualight.data.devices.api.light

data class LightStatus(
    val mode: LightMode = LightMode.UNKNOWN,
    val isPowerOn: Boolean = false,
    val outputPercent: Int = 0,
    val redPercent: Int = 0,
    val greenPercent: Int = 0,
    val bluePercent: Int = 0,
    val whitePercent: Int = 0,
    val deviceTimeText: String = "",
    val temperatureCelsius: Double? = null
)

enum class LightMode {
    AUTO,
    MANUAL,
    SCENE,
    MOONLIGHT,
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

package com.aqua.aqualight.data.devices.light.runtime

data class LightCoolingState(
    val hasData: Boolean = false,
    val fans: List<LightCoolingFanState> = emptyList()
) {

    val fanCount: Int
        get() = fans.size

    val enabledFanCount: Int
        get() = fans.count { fan ->
            fan.enabled
        }

    val maxOutputPercent: Int?
        get() = fans
            .mapNotNull { fan ->
                fan.outputPercent
            }
            .maxOrNull()

    val coolingModeText: String
        get() {
            return if (enabledFanCount > 0) {
                "Auto"
            } else {
                "Disabled"
            }
        }

    val statusText: String
        get() {
            if (!hasData) {
                return "Syncing"
            }

            if (fans.isEmpty()) {
                return "Not configured"
            }

            if (enabledFanCount == 0) {
                return "Disabled"
            }

            val output = maxOutputPercent ?: return "Auto"

            return when {
                output <= 0 -> "Auto · Off"
                output >= 100 -> "Auto · Full"
                else -> "Auto · $output%"
            }
        }

    val fansText: String
        get() {
            return when {
                !hasData -> "Syncing"
                fanCount <= 0 -> "Not configured"
                enabledFanCount <= 0 -> "$fanCount installed"
                fanCount == 1 -> "1 fan"
                else -> "$fanCount fans"
            }
        }

    val fanStartTemperatureCelsius: Int
        get() = fans.firstOrNull()?.fanStartTemperatureCelsius ?: 30

    val fanFullSpeedTemperatureCelsius: Int
        get() = fans.firstOrNull()?.fanFullSpeedTemperatureCelsius ?: 50
}

data class LightCoolingFanState(
    val index: Int,
    val enabled: Boolean,
    val : Int,
    val fanFullSpeedTemperatureCelsius: Int,
    val outputPercent: Int?,
    val regime: String,
    val linkedSensorCount: Int
)
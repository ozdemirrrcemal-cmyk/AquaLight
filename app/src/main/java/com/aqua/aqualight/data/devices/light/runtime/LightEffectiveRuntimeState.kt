package com.aqua.aqualight.data.devices.light.runtime

/**
 * Resolved light runtime state shared by dashboard, tank device cards and
 * control screens.  DataStore/runtime decides the operating mode; ESP32 live
 * telemetry remains the preferred source for actual channel/output/watt values.
 */
data class LightEffectiveRuntimeState(
    val deviceId: Long,
    val mode: LightEffectiveRuntimeMode,
    val title: String,
    val outputPercent: Int?,
    val red: Int?,
    val green: Int?,
    val blue: Int?,
    val white: Int?,
    val leftText: String? = null,
    val rightText: String? = null,
    val timelineProgressPercent: Int? = null
) {
    val isManualOverride: Boolean
        get() = mode == LightEffectiveRuntimeMode.MANUAL ||
            mode == LightEffectiveRuntimeMode.SCENE
}

enum class LightEffectiveRuntimeMode {
    SYNCING,
    AUTO,
    MANUAL,
    SCENE,
    MOONLIGHT
}

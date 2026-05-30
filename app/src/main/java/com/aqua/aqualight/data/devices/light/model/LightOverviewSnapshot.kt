package com.aqua.aqualight.data.devices.light.model

data class LightOverviewSnapshot(
    val deviceId: Long,

    val connectionLabel: String = "",
    val isOnline: Boolean = false,
    val isRefreshing: Boolean = false,

    val programTitle: String = "",
    val programSubtitle: String = "",
    val modeLabel: String = "",

    val currentOutputPercent: Int? = null,

    val redPercent: Int? = null,
    val greenPercent: Int? = null,
    val bluePercent: Int? = null,
    val whitePercent: Int? = null,

    val nowLabel: String = "",
    val nextLabel: String = "",
    val curveNowLabel: String = "",

    val timelineStartLabel: String = "",
    val timelineMidLabel: String = "",
    val timelineEndLabel: String = "",

    val curveStartTime: String = "",
    val curvePeakTimeRange: String = "",
    val curveSunsetTime: String = "",
    val curveRampMinutes: Int? = null,

    val activeProgramName: String = "",
    val activeProgramSchedule: String = "",
    val activeProgramChannels: String = "",
    val isProgramEnabled: Boolean = false,

    val healthLabel: String = "",
    val temperatureC: Int? = null,
    val fanLabel: String = "",
    val deviceTime: String = "",
    val firmware: String = ""
) {

    companion object {
        fun loading(
            deviceId: Long
        ): LightOverviewSnapshot {
            return LightOverviewSnapshot(
                deviceId = deviceId,
                connectionLabel = "Connecting · WRGB",
                isOnline = false,
                isRefreshing = false,
                programTitle = "Current Program",
                programSubtitle = "Waiting for device data",
                modeLabel = "SYNCING",
                timelineStartLabel = "00:00",
                timelineMidLabel = "12:00",
                timelineEndLabel = "24:00",
                healthLabel = "Connecting"
            )
        }
    }
}
package com.aqua.aqualight.data.devices.light.model

data class LightOverviewSnapshot(
    val isLoading: Boolean = true,
    val isOnline: Boolean = false,

    val connectionLabel: String = "",

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

    val timelineStartLabel: String = "00:00",
    val timelineMidLabel: String = "12:00",
    val timelineEndLabel: String = "24:00",

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
    val firmwareVersion: String = "",

    /**
     * Geriye uyumluluk için tutuldu.
     * Eski kodda curvePeakRange kullanıldıysa derleme kırılmasın.
     */
    val curvePeakRange: String = "",

    /**
     * Geriye uyumluluk için tutuldu.
     * Eski kodda firmwareLabel kullanıldıysa derleme kırılmasın.
     */
    val firmwareLabel: String = ""
) {

    companion object {
        fun loading(): LightOverviewSnapshot {
            return LightOverviewSnapshot(
                isLoading = true,
                isOnline = false,
                connectionLabel = "Connecting · WRGB"
            )
        }
    }
}
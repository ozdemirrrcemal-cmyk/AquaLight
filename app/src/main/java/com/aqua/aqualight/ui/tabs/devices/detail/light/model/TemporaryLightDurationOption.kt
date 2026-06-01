package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import androidx.annotation.StringRes
import com.aqua.aqualight.R

enum class TemporaryLightDurationOption(
    @StringRes val labelRes: Int,
    val minutes: Int?,
    val untilNextEvent: Boolean
) {
    MINUTES_15(
        labelRes = R.string.light_duration_15_min,
        minutes = 15,
        untilNextEvent = false
    ),

    MINUTES_30(
        labelRes = R.string.light_duration_30_min,
        minutes = 30,
        untilNextEvent = false
    ),

    MINUTES_60(
        labelRes = R.string.light_duration_60_min,
        minutes = 60,
        untilNextEvent = false
    ),

    UNTIL_NEXT_EVENT(
        labelRes = R.string.light_duration_until_next_event,
        minutes = null,
        untilNextEvent = true
    )
}
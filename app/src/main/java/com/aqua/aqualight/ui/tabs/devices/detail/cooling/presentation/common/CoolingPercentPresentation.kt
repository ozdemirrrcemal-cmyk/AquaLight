package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import kotlin.math.roundToInt

/** Unit formatting only; the firmware-owned runtime percentage remains a [Double] upstream. */
internal fun Double?.toCoolingDisplayPercentOrNull(): Int? = this
    ?.takeIf { value ->
        value.isFinite() &&
            value in MINIMUM_DISPLAY_PERCENT.toDouble()..MAXIMUM_DISPLAY_PERCENT.toDouble()
    }
    ?.roundToInt()

private const val MINIMUM_DISPLAY_PERCENT = 0
private const val MAXIMUM_DISPLAY_PERCENT = 100

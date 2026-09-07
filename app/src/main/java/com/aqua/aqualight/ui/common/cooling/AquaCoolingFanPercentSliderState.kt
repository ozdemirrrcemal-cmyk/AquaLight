package com.aqua.aqualight.ui.common.cooling

/** Immutable input contract for the shared Cooling fan-percent slider. */
internal data class AquaCoolingFanPercentSliderState(
    val percent: Int,
    val enabled: Boolean,
    val stepPercent: Int = 1,
    val minimumPercent: Int = AquaCoolingGaugeSpec.minimumPercent,
    val maximumPercent: Int = AquaCoolingGaugeSpec.maximumPercent
)

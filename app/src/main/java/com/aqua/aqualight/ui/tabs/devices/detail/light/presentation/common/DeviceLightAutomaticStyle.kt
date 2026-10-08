package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object DeviceLightAutomaticGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 5.dp
    val screenBottomPadding = 82.dp
    val cardGap = 8.dp
    val cardContentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    val cardContentMinimumHeight = 110.dp

    val cardHeaderIconSize = 22.dp
    val iconStrokeWidth = 1.5.dp
    val headerIconGap = 8.dp
    val headerControlGap = 5.dp
    val scheduleEventIconSize = 22.dp
    val scheduleTextGap = 5.dp
    val scheduleArrowGap = 7.dp
    val scheduleDividerWidth = 1.dp
    val scheduleDividerHeight = 22.dp
    val scheduleDividerGap = 8.dp
    val cardDividerHeight = 1.dp

    val switchWidth = 48.dp
    val switchHeight = 28.dp
    val switchThumbSize = 22.dp
    val switchInset = 3.dp
    val switchShape = RoundedCornerShape(percent = 50)

    val moreTouchSize = 34.dp
    val moreDotRadius = 1.3.dp
    val moreDotGap = 4.dp

    val channelDotSize = 10.dp
    val channelTextGap = 5.dp

    val addButtonHorizontalPadding = 12.dp
    val addButtonBottomPadding = 12.dp
    val addButtonHeight = 52.dp
    val addButtonShape = RoundedCornerShape(percent = 50)
    val addButtonIconSize = 25.dp
    val addButtonIconStrokeWidth = 2.dp
    val addButtonContentGap = 10.dp
}

internal object DeviceLightAutomaticIconGeometry {
    const val calendarLeftFraction = 0.16f
    const val calendarRightFraction = 0.84f
    const val calendarTopFraction = 0.23f
    const val calendarBottomFraction = 0.86f
    const val calendarCornerRadiusFraction = 0.08f
    const val calendarDividerFraction = 0.40f
    const val calendarFirstBindingFraction = 0.34f
    const val calendarSecondBindingFraction = 0.66f
    val calendarBindingFractions = listOf(
        calendarFirstBindingFraction,
        calendarSecondBindingFraction
    )
    const val calendarBindingTopFraction = 0.12f
    const val calendarBindingBottomFraction = 0.31f

    const val clockRadiusFraction = 0.38f
    const val clockHourHandFraction = 0.55f
    const val clockMinuteHandXFraction = 0.47f
    const val clockMinuteHandYFraction = 0.24f

    const val rampLeftXFraction = 0.13f
    const val rampLeftYFraction = 0.79f
    const val rampRightXFraction = 0.80f
    const val rampTopYFraction = 0.21f
    const val rampBottomYFraction = rampLeftYFraction

    const val moreDotCount = 3
    const val moreDotCenterIndex = 1
    const val plusInsetFraction = 0.18f
}

internal object DeviceLightAutomaticAlpha {
    const val disabled = 0.42f
    const val switchOffTrack = 0.58f
}

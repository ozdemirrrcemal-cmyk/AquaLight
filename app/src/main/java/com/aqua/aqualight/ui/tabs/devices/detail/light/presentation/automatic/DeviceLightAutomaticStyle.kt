package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object DeviceLightAutomaticGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 5.dp
    val screenBottomPadding = 82.dp
    val cardGap = 8.dp
    val cardContentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

    val cardHeaderIconSize = 22.dp
    val cardMetaIconSize = 20.dp
    val iconStrokeWidth = 1.5.dp
    val headerIconGap = 8.dp
    val headerControlGap = 5.dp
    val metaTopGap = 4.dp
    val metaItemGap = 7.dp
    val rampItemStartGap = 12.dp

    val switchWidth = 48.dp
    val switchHeight = 28.dp
    val switchThumbSize = 22.dp
    val switchInset = 3.dp
    val switchShape = RoundedCornerShape(percent = 50)

    val moreTouchSize = 34.dp
    val moreDotRadius = 1.3.dp
    val moreDotGap = 4.dp

    val chartTopGap = 7.dp
    val chartHeight = 72.dp
    val chartYAxisWidth = 34.dp
    val chartYAxisGap = 4.dp
    val chartXAxisHeight = 16.dp
    val chartGridStrokeWidth = 0.8.dp
    val chartSeriesStrokeWidth = 1.8.dp

    val legendTopGap = 6.dp
    val legendDotSize = 10.dp
    val legendTextGap = 5.dp
    val legendItemGap = 7.dp

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
    const val grid = 0.42f
    const val disabled = 0.42f
    const val switchOffTrack = 0.58f
}

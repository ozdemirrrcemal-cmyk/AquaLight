package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors

@Immutable
internal data class DeviceLightAdaptationVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

internal object DeviceLightAdaptationGeometry {
    val screenHorizontalPadding = 14.dp
    val screenTopPadding = 8.dp
    val screenBottomPadding = 10.dp
    val sectionGap = 12.dp
    val cardPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
    val compactCardPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
    val cardContentGap = 12.dp
    val titleGap = 4.dp
    val iconSize = 34.dp
    val infoIconSize = 22.dp
    val iconGap = 12.dp
    val statusShape = RoundedCornerShape(12.dp)
    val statusPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
    val dividerHeight = 1.dp
    val sliderHeight = 38.dp
    val sliderTrackHeight = 4.dp
    val sliderThumbRadius = 9.dp
    val sliderTickRadius = 2.dp
    val sliderInset = 10.dp
    val controlGap = 18.dp
    val helperTopGap = 2.dp
    val summaryRowGap = 13.dp
    val summaryLabelWidth = 126.dp
    val progressHeight = 8.dp
    val progressShape = RoundedCornerShape(4.dp)
    val progressLabelTopGap = 8.dp
    val actionHeight = 54.dp
    val actionShape = RoundedCornerShape(14.dp)
    val actionBorderWidth = 1.dp
    val actionTopPadding = 10.dp
    val largePercentSize = 34.sp
    val largePercentLineHeight = 40.sp
}

internal object DeviceLightAdaptationAlpha {
    const val softSurface = 0.12f
    const val disabled = 0.38f
    const val rail = 0.42f
    const val divider = 0.66f
}

internal object DeviceLightAdaptationSpec {
    const val percentScale = 10
    const val fullPercent = 100
    const val secondsPerDay = 86_400L
}

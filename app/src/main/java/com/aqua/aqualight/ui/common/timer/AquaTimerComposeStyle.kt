@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.timer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

/** Central visual contract for every Timer control-surface composable. */
object AquaTimerDashboardGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 8.dp
    val screenBottomPadding = 24.dp
    val cardGap = 9.dp

    val summaryIconContainerSize = 64.dp
    val summaryIconSize = 54.dp
    val summaryContentGap = 12.dp
    val summaryTextGap = 3.dp

    val channelIconContainerSize = 42.dp
    val channelIconSize = 34.dp
    val channelHeaderGap = 10.dp
    val channelTextGap = 2.dp
    val channelSectionGap = AquaDeviceCardGeometry.contentGap
    val metadataGap = 7.dp
    val metadataIconSize = 16.dp
    val metadataTextGap = 8.dp
    val dividerHeight = 1.dp

    val statusShape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    val statusHorizontalPadding = AquaDeviceCardGeometry.statusHorizontalPadding
    val statusVerticalPadding = AquaDeviceCardGeometry.statusVerticalPadding

    val modeGroupGap = 5.dp
    val modeShape = RoundedCornerShape(10.dp)
    val modeHorizontalPadding = 7.dp
    val modeVerticalPadding = 8.dp
    val modeLabelGap = 6.dp
    val modeIndicatorSize = 7.dp

    val messageMinimumHeight = 86.dp
    val messageGap = 6.dp
}

object AquaTimerDashboardTypography {
    val summaryValueSize = 18.sp
    val statusSize = 9.sp
    val modeSize = 10.sp
}

object AquaTimerInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.52f
}

object AquaTimerDashboardAlpha {
    const val iconBackground = 0.22f
    const val selectedBackground = 0.18f
    const val selectedOutline = 0.82f
    const val idleBackground = 0.18f
    const val statusBackground = 0.17f
    const val divider = 0.72f
}

@Composable
fun aquaTimerDashboardColors(): AquaDeviceCardColors = aquaDeviceCardColors()

fun aquaTimerDashboardTypography(colors: AquaDeviceCardColors): AquaDeviceCardTypography =
    aquaDeviceCardTypography(colors)

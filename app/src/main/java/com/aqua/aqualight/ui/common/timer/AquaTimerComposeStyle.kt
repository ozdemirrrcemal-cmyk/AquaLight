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
    val screenHorizontalPadding = TimerMetric.SCREEN_HORIZONTAL_PADDING.dp
    val screenTopPadding = TimerMetric.SCREEN_TOP_PADDING.dp
    val screenBottomPadding = TimerMetric.SCREEN_BOTTOM_PADDING.dp
    val cardGap = TimerMetric.CARD_GAP.dp

    val summaryIconContainerSize = TimerMetric.SUMMARY_ICON_CONTAINER_SIZE.dp
    val summaryIconSize = TimerMetric.SUMMARY_ICON_SIZE.dp
    val summaryContentGap = TimerMetric.SUMMARY_CONTENT_GAP.dp
    val summaryTextGap = TimerMetric.SUMMARY_TEXT_GAP.dp

    val channelIconContainerSize = TimerMetric.CHANNEL_ICON_CONTAINER_SIZE.dp
    val channelIconSize = TimerMetric.CHANNEL_ICON_SIZE.dp
    val channelPowerContainerSize = TimerMetric.CHANNEL_POWER_CONTAINER_SIZE.dp
    val channelPowerIconSize = TimerMetric.CHANNEL_POWER_ICON_SIZE.dp
    val channelPowerGlowWidth = TimerMetric.CHANNEL_POWER_GLOW_WIDTH.dp
    val channelHeaderGap = TimerMetric.CHANNEL_HEADER_GAP.dp
    val channelTextGap = TimerMetric.CHANNEL_TEXT_GAP.dp
    val channelSectionGap = AquaDeviceCardGeometry.contentGap
    val metadataGap = TimerMetric.METADATA_GAP.dp
    val metadataIconSize = TimerMetric.METADATA_ICON_SIZE.dp
    val metadataActionSize = TimerMetric.METADATA_ACTION_SIZE.dp
    val metadataTextGap = TimerMetric.METADATA_TEXT_GAP.dp
    val dividerHeight = TimerMetric.DIVIDER_HEIGHT.dp
    val detailHeroPowerContainerSize = TimerMetric.DETAIL_HERO_POWER_CONTAINER_SIZE.dp
    val detailHeroPowerIconSize = TimerMetric.DETAIL_HERO_POWER_ICON_SIZE.dp
    val detailHeroGap = TimerMetric.DETAIL_HERO_GAP.dp
    val detailRowIconSize = TimerMetric.DETAIL_ROW_ICON_SIZE.dp
    val detailRowPadding = TimerMetric.DETAIL_ROW_PADDING.dp
    val detailRowGap = TimerMetric.DETAIL_ROW_GAP.dp

    val statusShape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    val statusHorizontalPadding = AquaDeviceCardGeometry.statusHorizontalPadding
    val statusVerticalPadding = AquaDeviceCardGeometry.statusVerticalPadding

    val modeGroupGap = TimerMetric.MODE_GROUP_GAP.dp
    val modeShape = RoundedCornerShape(TimerMetric.MODE_CORNER_RADIUS.dp)
    val modeHorizontalPadding = TimerMetric.MODE_HORIZONTAL_PADDING.dp
    val modeVerticalPadding = TimerMetric.MODE_VERTICAL_PADDING.dp
    val modeLabelGap = TimerMetric.MODE_LABEL_GAP.dp
    val modeIndicatorSize = TimerMetric.MODE_INDICATOR_SIZE.dp

    val actionRowGap = TimerMetric.ACTION_ROW_GAP.dp
    val actionButtonGap = TimerMetric.ACTION_BUTTON_GAP.dp
    val editorSectionGap = TimerMetric.EDITOR_SECTION_GAP.dp
    val editorRowGap = TimerMetric.EDITOR_ROW_GAP.dp
    val editorWeekdayGap = TimerMetric.EDITOR_WEEKDAY_GAP.dp
    val editorWeekdaySize = TimerMetric.EDITOR_WEEKDAY_SIZE.dp
    val editorRowPadding = TimerMetric.EDITOR_ROW_PADDING.dp
    val editorTogglePadding = TimerMetric.EDITOR_TOGGLE_PADDING.dp
    val actionShape = RoundedCornerShape(TimerMetric.ACTION_CORNER_RADIUS.dp)
    val actionPadding = TimerMetric.ACTION_PADDING.dp

    val messageMinimumHeight = TimerMetric.MESSAGE_MINIMUM_HEIGHT.dp
    val messageGap = TimerMetric.MESSAGE_GAP.dp
}

object AquaTimerDashboardTypography {
    val summaryValueSize = TimerMetric.SUMMARY_VALUE_TEXT_SIZE.sp
    val statusSize = TimerMetric.STATUS_TEXT_SIZE.sp
    val modeSize = TimerMetric.MODE_TEXT_SIZE.sp
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
    const val actionBackground = 0.16f
    const val powerGlow = 0.34f
    const val powerSurface = 0.28f
}

@Composable
fun aquaTimerDashboardColors(): AquaDeviceCardColors = aquaDeviceCardColors()

fun aquaTimerDashboardTypography(colors: AquaDeviceCardColors): AquaDeviceCardTypography =
    aquaDeviceCardTypography(colors)

private object TimerMetric {
    const val SCREEN_HORIZONTAL_PADDING = 12
    const val SCREEN_TOP_PADDING = 8
    const val SCREEN_BOTTOM_PADDING = 24
    const val CARD_GAP = 9
    const val SUMMARY_ICON_CONTAINER_SIZE = 64
    const val SUMMARY_ICON_SIZE = 54
    const val SUMMARY_CONTENT_GAP = 12
    const val SUMMARY_TEXT_GAP = 3
    const val CHANNEL_ICON_CONTAINER_SIZE = 42
    const val CHANNEL_ICON_SIZE = 34
    const val CHANNEL_POWER_CONTAINER_SIZE = 50
    const val CHANNEL_POWER_ICON_SIZE = 25
    const val CHANNEL_POWER_GLOW_WIDTH = 2
    const val CHANNEL_HEADER_GAP = 10
    const val CHANNEL_TEXT_GAP = 2
    const val METADATA_GAP = 7
    const val METADATA_ICON_SIZE = 16
    const val METADATA_ACTION_SIZE = 48
    const val METADATA_TEXT_GAP = 8
    const val DIVIDER_HEIGHT = 1
    const val DETAIL_HERO_POWER_CONTAINER_SIZE = 96
    const val DETAIL_HERO_POWER_ICON_SIZE = 42
    const val DETAIL_HERO_GAP = 8
    const val DETAIL_ROW_ICON_SIZE = 22
    const val DETAIL_ROW_PADDING = 14
    const val DETAIL_ROW_GAP = 12
    const val MODE_GROUP_GAP = 5
    const val MODE_CORNER_RADIUS = 10
    const val MODE_HORIZONTAL_PADDING = 7
    const val MODE_VERTICAL_PADDING = 8
    const val MODE_LABEL_GAP = 6
    const val MODE_INDICATOR_SIZE = 7
    const val ACTION_ROW_GAP = 6
    const val ACTION_BUTTON_GAP = 6
    const val EDITOR_SECTION_GAP = 8
    const val EDITOR_ROW_GAP = 10
    const val EDITOR_WEEKDAY_GAP = 5
    const val EDITOR_WEEKDAY_SIZE = 36
    const val EDITOR_ROW_PADDING = 10
    const val EDITOR_TOGGLE_PADDING = 8
    const val ACTION_CORNER_RADIUS = 10
    const val ACTION_PADDING = 10
    const val MESSAGE_MINIMUM_HEIGHT = 86
    const val MESSAGE_GAP = 6
    const val SUMMARY_VALUE_TEXT_SIZE = 18
    const val STATUS_TEXT_SIZE = 9
    const val MODE_TEXT_SIZE = 10
}

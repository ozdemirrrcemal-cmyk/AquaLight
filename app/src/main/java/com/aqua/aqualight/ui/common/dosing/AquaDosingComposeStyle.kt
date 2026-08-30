@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.dosing

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Central, pixel-stable Compose contract for every Dosing surface.
 *
 * These values are an ownership move only. They intentionally preserve the exact dimensions,
 * radii, elevations, colors and gradient stops from the commercially reviewed feature rendering.
 * Feature packages must consume these tokens instead of declaring local visual primitives.
 */
object AquaDosingCatalogGeometry {
    val screenHorizontalPadding = 16.dp
    val screenTopPadding = 12.dp
    val screenBottomPadding = 24.dp
    val channelCardSpacing = 10.dp
    val cardListTopPadding = 24.dp
}

object AquaDosingInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.42f
}

object AquaDosingPumpGeometry {
    val zero = 0.dp
    val sectionHorizontalPadding = 16.dp
    val sectionTopPadding = 12.dp
    val pro2MaximumWidth = 360.dp
    val pro4MaximumWidth = 760.dp
    val pro2PumpHeadMaximumSize = 104.dp
    val deviceOuterShape = RoundedCornerShape(30.dp)
    val deviceInnerShape = RoundedCornerShape(24.dp)
    val deviceDeckShape = RoundedCornerShape(20.dp)
    val pumpOuterShape = RoundedCornerShape(20.dp)
    val pumpFaceShape = RoundedCornerShape(15.dp)
    val deviceShadowElevation = 18.dp
    val pumpShadowElevation = 8.dp
    val hubShadowElevation = 5.dp
    val deviceEdgeWidth = 1.dp
    val deviceOuterInset = 7.dp
    val deviceInnerInset = 7.dp
    val deviceDeckInset = 9.dp
    val pumpFrameInset = 7.dp
    val pumpSpacing = 8.dp
    val indicatorEdgeWidth = 1.dp
}

object AquaDosingCalibrationGeometry {
    val screenTopPadding = 12.dp
    val progressContentGap = 10.dp
    val copyGap = 8.dp
    val progressSegmentGap = 6.dp
    val progressSegmentHeight = 5.dp
    val controlGap = 12.dp
    val confirmationGap = 10.dp
    val textFieldHorizontalPadding = 16.dp
    val textFieldVerticalPadding = 15.dp
    val primeHorizontalPadding = 18.dp
    val primeVerticalPadding = 14.dp
    val primeDotSize = 9.dp
    val inlineGap = 10.dp
    val countdownGap = 4.dp
    val illustrationHeight = 210.dp
}

object AquaDosingCardGeometry {
    val zero = 0.dp
    val channelCardMinimumHeight = 104.dp
    val emptyVerticalPadding = 4.dp
    val emptyContentGap = 12.dp
    val emptyTextGap = 2.dp
    val emptyIconSize = 44.dp
    val emptyIconCornerRadius = 14.dp
    val emptyGlyphSize = 28.dp

    val glyphStroke = 1.45.dp
    val badgeOutlineWidth = 1.10.dp
    val badgePlusWidth = 1.55.dp
    val manualPlusWidth = 1.35.dp
    val nextDoseGlyphStroke = 1.25.dp
    val nextDoseCenterDotRadius = 0.65.dp
    val calendarCornerRadius = 2.5.dp
    val calendarDotRadius = 1.1.dp
    val modeStroke = 1.25.dp
    val clockTickWidth = 1.dp
    val timerEventRadius = 1.25.dp
    val customCornerRadius = 2.dp

    val nextDoseGlyphSize = 16.dp
    val nextDoseTextGap = 6.dp

    val progressRailHeight = 16.dp
    val progressCornerRadius = 8.dp
    val progressOutlineWidth = 1.dp
    val progressValueTagAreaHeight = 20.dp
    val nextDoseToProgressGap = 4.dp
    val progressToManualGap = 8.dp
    val manualPillMinimumWidth = 78.dp
    val manualPillMaximumWidth = 92.dp
    val manualHorizontalPadding = 7.dp

    val valueTagWidth = 56.dp
    val valueTagMinimumWidth = 42.dp
    val valueTagHeight = 16.dp
    val valueTagPointerWidth = 7.dp
    val valueTagPointerHeight = 4.dp
    val valueTagCornerRadius = 5.dp
    val valueTagOutlineWidth = 0.75.dp
    val valueTagHorizontalPadding = 5.dp
    val markerScaleHeight = 18.dp
    val markerAccentWidth = 5.dp
    val markerAccentHeight = 1.5.dp
    val markerAccentTop = 2.dp
    val markerLabelTop = 4.dp
    val markerLabelWidth = 44.dp
    val segmentGap = 1.dp
    val defaultGroupGap = 4.dp
    val railCornerRadius = 4.dp
    val segmentCornerRadius = 2.dp
    val hourlyGroupGap = 2.dp
    val customGroupGap = 6.dp

    val summaryRowGap = 5.dp
    val summaryColumnGap = 18.dp
    val summaryIconGap = 6.dp
    val summaryIconSize = 16.dp

    val reservoirGap = 6.dp
    val reservoirIconSize = 16.dp
    val reservoirStroke = 1.35.dp
    val reservoirLevelLineWidth = 1.dp
    val reservoirBodyCornerRadius = 2.5.dp
    val reservoirFillCornerRadius = 1.5.dp
    val reservoirCapCornerRadius = 1.dp
}

object AquaDosingPumpPalette {
    val outerEdge = Color(OUTER_EDGE_COLOR)
    val innerEdge = Color(INNER_EDGE_COLOR)
    val metalHighlight = Color(METAL_HIGHLIGHT_COLOR)
    val faceEdge = Color(FACE_EDGE_COLOR)
    val hubEdge = Color(HUB_EDGE_COLOR)
    val indicatorEdge = Color(INDICATOR_EDGE_COLOR)
    val runningGlow = Color(RUNNING_GLOW_COLOR)
    val errorGlow = Color(ERROR_GLOW_COLOR)

    val outerShell = Brush.verticalGradient(
        colors = listOf(
            Color(OUTER_SHELL_TOP_COLOR),
            Color(OUTER_SHELL_MIDDLE_COLOR),
            Color(OUTER_SHELL_BOTTOM_COLOR)
        )
    )
    val innerShell = Brush.verticalGradient(
        colors = listOf(
            Color(INNER_SHELL_TOP_COLOR),
            Color(INNER_SHELL_MIDDLE_COLOR),
            Color(INNER_SHELL_BOTTOM_COLOR)
        )
    )
    val metalDeck = Brush.horizontalGradient(
        METAL_STOP_START to Color(METAL_COLOR_START),
        METAL_STOP_1 to Color(METAL_COLOR_1),
        METAL_STOP_2 to Color(METAL_COLOR_2),
        METAL_STOP_3 to Color(METAL_COLOR_3),
        METAL_STOP_4 to Color(METAL_COLOR_4),
        METAL_STOP_5 to Color(METAL_COLOR_5),
        METAL_STOP_6 to Color(METAL_COLOR_6),
        METAL_STOP_7 to Color(METAL_COLOR_7),
        METAL_STOP_END to Color(METAL_COLOR_END)
    )
    val pumpFrame = Brush.linearGradient(
        colors = listOf(
            Color(PUMP_FRAME_COLOR_1),
            Color(PUMP_FRAME_COLOR_2),
            Color(PUMP_FRAME_COLOR_3),
            Color(PUMP_FRAME_COLOR_4),
            Color(PUMP_FRAME_COLOR_5)
        )
    )
    val pumpFace = Brush.linearGradient(
        colors = listOf(
            Color(PUMP_FACE_COLOR_1),
            Color(PUMP_FACE_COLOR_2),
            Color(PUMP_FACE_COLOR_3)
        )
    )
    val hub = Brush.radialGradient(
        colors = listOf(
            Color(HUB_COLOR_1),
            Color(HUB_COLOR_2),
            Color(HUB_COLOR_3),
            Color(HUB_COLOR_4)
        )
    )
    val idleIndicator = Brush.radialGradient(
        colors = listOf(
            Color(IDLE_INDICATOR_COLOR_1),
            Color(IDLE_INDICATOR_COLOR_2),
            Color(IDLE_INDICATOR_COLOR_3),
            Color(IDLE_INDICATOR_COLOR_4)
        )
    )
    val runningIndicator = Brush.radialGradient(
        colors = listOf(
            Color(RUNNING_INDICATOR_COLOR_1),
            Color(RUNNING_INDICATOR_COLOR_2),
            Color(RUNNING_INDICATOR_COLOR_3),
            Color(RUNNING_INDICATOR_COLOR_4)
        )
    )
    val errorIndicator = Brush.radialGradient(
        colors = listOf(
            Color(ERROR_INDICATOR_COLOR_1),
            Color(ERROR_INDICATOR_COLOR_2),
            Color(ERROR_INDICATOR_COLOR_3),
            Color(ERROR_INDICATOR_COLOR_4)
        )
    )
}

private const val OUTER_EDGE_COLOR = 0x52FFFFFF
private const val INNER_EDGE_COLOR = 0x1FFFFFFF
private const val METAL_HIGHLIGHT_COLOR = 0xA6FFFFFF
private const val FACE_EDGE_COLOR = 0x24FFFFFF
private const val HUB_EDGE_COLOR = 0x4DFFFFFF
private const val INDICATOR_EDGE_COLOR = 0xB3000000
private const val RUNNING_GLOW_COLOR = 0xFF49F28F
private const val ERROR_GLOW_COLOR = 0xFFFF5361
private const val OUTER_SHELL_TOP_COLOR = 0xFF3A3F46
private const val OUTER_SHELL_MIDDLE_COLOR = 0xFF15191E
private const val OUTER_SHELL_BOTTOM_COLOR = 0xFF050608
private const val INNER_SHELL_TOP_COLOR = 0xFF15181C
private const val INNER_SHELL_MIDDLE_COLOR = 0xFF080A0D
private const val INNER_SHELL_BOTTOM_COLOR = 0xFF030405
private const val METAL_COLOR_START = 0xFF3B4047
private const val METAL_COLOR_1 = 0xFFAEB3B9
private const val METAL_COLOR_2 = 0xFF5E646C
private const val METAL_COLOR_3 = 0xFFD8DBDE
private const val METAL_COLOR_4 = 0xFF666C74
private const val METAL_COLOR_5 = 0xFFB8BDC3
private const val METAL_COLOR_6 = 0xFF555B63
private const val METAL_COLOR_7 = 0xFFD4D7DA
private const val METAL_COLOR_END = 0xFF4B5057
private const val PUMP_FRAME_COLOR_1 = 0xFFF1F2F4
private const val PUMP_FRAME_COLOR_2 = 0xFF8C9299
private const val PUMP_FRAME_COLOR_3 = 0xFF292E34
private const val PUMP_FRAME_COLOR_4 = 0xFFA3A8AE
private const val PUMP_FRAME_COLOR_5 = 0xFF3B4046
private const val PUMP_FACE_COLOR_1 = 0xFF191C21
private const val PUMP_FACE_COLOR_2 = 0xFF050608
private const val PUMP_FACE_COLOR_3 = 0xFF101318
private const val HUB_COLOR_1 = 0xFF8A919A
private const val HUB_COLOR_2 = 0xFF343941
private const val HUB_COLOR_3 = 0xFF15181C
private const val HUB_COLOR_4 = 0xFF050607
private const val IDLE_INDICATOR_COLOR_1 = 0xFFD5D9DE
private const val IDLE_INDICATOR_COLOR_2 = 0xFF888F98
private const val IDLE_INDICATOR_COLOR_3 = 0xFF5B626C
private const val IDLE_INDICATOR_COLOR_4 = 0xFF2A2F35
private const val RUNNING_INDICATOR_COLOR_1 = 0xFFEFFFF5
private const val RUNNING_INDICATOR_COLOR_2 = 0xFF85FFB4
private const val RUNNING_INDICATOR_COLOR_3 = 0xFF38EC80
private const val RUNNING_INDICATOR_COLOR_4 = 0xFF0F7C3B
private const val ERROR_INDICATOR_COLOR_1 = 0xFFFFF3F4
private const val ERROR_INDICATOR_COLOR_2 = 0xFFFF9BA4
private const val ERROR_INDICATOR_COLOR_3 = 0xFFFF5361
private const val ERROR_INDICATOR_COLOR_4 = 0xFF8C1723
private const val METAL_STOP_START = 0f
private const val METAL_STOP_1 = 0.11f
private const val METAL_STOP_2 = 0.19f
private const val METAL_STOP_3 = 0.30f
private const val METAL_STOP_4 = 0.44f
private const val METAL_STOP_5 = 0.58f
private const val METAL_STOP_6 = 0.73f
private const val METAL_STOP_7 = 0.87f
private const val METAL_STOP_END = 1f

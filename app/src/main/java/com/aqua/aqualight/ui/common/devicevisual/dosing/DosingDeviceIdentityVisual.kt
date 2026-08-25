package com.aqua.aqualight.ui.common.devicevisual.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hose-equipped Dose Pro identity illustration for device cards, selectors and detail identity rows.
 *
 * Unlike [DosingPumpDevice], this is not an operational control surface. It owns no channel state;
 * the hoses and compact pump heads are presentation-only product identity primitives.
 */
@Composable
fun DosingDeviceIdentityVisual(
    pumpCount: Int,
    modifier: Modifier = Modifier
) {
    require(pumpCount == DOSING_PRO_2_PUMP_COUNT || pumpCount == DOSING_PRO_4_PUMP_COUNT)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val bodyWidth = maxWidth * identityBodyWidthRatio(pumpCount)
        val bodyHeight = minOf(
            bodyWidth / identityBodyAspectRatio(pumpCount),
            maxHeight * IDENTITY_BODY_MAX_HEIGHT_RATIO
        )

        DosingIdentityHoses(
            pumpCount = pumpCount,
            modifier = Modifier.matchParentSize()
        )
        DosingIdentityBody(
            pumpCount = pumpCount,
            width = bodyWidth,
            height = bodyHeight
        )
    }
}

@Composable
private fun DosingIdentityBody(
    pumpCount: Int,
    width: Dp,
    height: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = IDENTITY_BODY_SHADOW,
                shape = IDENTITY_OUTER_SHAPE,
                clip = false
            )
            .background(
                brush = DosingPumpPalette.outerShell,
                shape = IDENTITY_OUTER_SHAPE
            )
            .border(
                width = IDENTITY_EDGE_WIDTH,
                color = DosingPumpPalette.outerEdge,
                shape = IDENTITY_OUTER_SHAPE
            )
            .padding(IDENTITY_OUTER_INSET)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = DosingPumpPalette.metalDeck,
                    shape = IDENTITY_DECK_SHAPE
                )
                .border(
                    width = IDENTITY_EDGE_WIDTH,
                    color = DosingPumpPalette.metalHighlight,
                    shape = IDENTITY_DECK_SHAPE
                )
                .padding(IDENTITY_DECK_INSET),
            horizontalArrangement = Arrangement.spacedBy(IDENTITY_HEAD_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pumpCount) {
                DosingPumpHeadMarker(
                    modifier = Modifier
                        .weight(IDENTITY_HEAD_WEIGHT)
                        .aspectRatio(IDENTITY_HEAD_ASPECT_RATIO)
                )
            }
        }
    }
}

@Composable
private fun DosingIdentityHoses(
    pumpCount: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        repeat(pumpCount) { index ->
            drawIdentityHose(
                pumpIndex = index,
                pumpCount = pumpCount
            )
        }
    }
}

private fun DrawScope.drawIdentityHose(
    pumpIndex: Int,
    pumpCount: Int
) {
    val bodyWidthRatio = identityBodyWidthRatio(pumpCount)
    val bodyAspectRatio = identityBodyAspectRatio(pumpCount)
    val bodyWidth = size.width * bodyWidthRatio
    val bodyHeight = minOf(
        bodyWidth / bodyAspectRatio,
        size.height * IDENTITY_BODY_MAX_HEIGHT_RATIO
    )
    val bodyStartX = (size.width - bodyWidth) / IDENTITY_CENTER_DIVISOR
    val headFraction = (pumpIndex + IDENTITY_HEAD_CENTER_OFFSET) / pumpCount.toFloat()
    val startX = bodyStartX + bodyWidth * headFraction
    val startY = bodyHeight * HOSE_START_Y_RATIO
    val endY = size.height * HOSE_END_Y_RATIO
    val swayDirection = if (pumpIndex % EVEN_DIVISOR == 0) -IDENTITY_HEAD_WEIGHT else IDENTITY_HEAD_WEIGHT
    val sway = size.width * HOSE_SWAY_RATIO * swayDirection
    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(
            startX + sway,
            startY + (endY - startY) * HOSE_FIRST_CONTROL_Y_RATIO,
            startX - sway,
            startY + (endY - startY) * HOSE_SECOND_CONTROL_Y_RATIO,
            startX + sway * HOSE_END_SWAY_RATIO,
            endY
        )
    }
    val minDimension = size.minDimension

    drawPath(
        path = path,
        color = DosingPumpPalette.hoseShadow,
        style = Stroke(
            width = minDimension * HOSE_SHADOW_WIDTH_RATIO,
            cap = StrokeCap.Round
        )
    )
    drawPath(
        path = path,
        color = DosingPumpPalette.hoseBase,
        style = Stroke(
            width = minDimension * HOSE_BASE_WIDTH_RATIO,
            cap = StrokeCap.Round
        )
    )
    drawPath(
        path = path,
        color = DosingPumpPalette.hoseHighlight,
        style = Stroke(
            width = minDimension * HOSE_HIGHLIGHT_WIDTH_RATIO,
            cap = StrokeCap.Round
        )
    )
}

private fun identityBodyWidthRatio(pumpCount: Int): Float =
    if (pumpCount == DOSING_PRO_2_PUMP_COUNT) IDENTITY_PRO_2_BODY_WIDTH_RATIO else 1f

private fun identityBodyAspectRatio(pumpCount: Int): Float =
    if (pumpCount == DOSING_PRO_2_PUMP_COUNT) IDENTITY_PRO_2_BODY_ASPECT_RATIO else
        IDENTITY_PRO_4_BODY_ASPECT_RATIO

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val EVEN_DIVISOR = 2
private const val IDENTITY_CENTER_DIVISOR = 2f
private const val IDENTITY_HEAD_CENTER_OFFSET = 0.5f
private const val IDENTITY_HEAD_WEIGHT = 1f
private const val IDENTITY_HEAD_ASPECT_RATIO = 1f
private const val IDENTITY_PRO_2_BODY_WIDTH_RATIO = 0.62f
private const val IDENTITY_PRO_2_BODY_ASPECT_RATIO = 2.15f
private const val IDENTITY_PRO_4_BODY_ASPECT_RATIO = 4.15f
private const val IDENTITY_BODY_MAX_HEIGHT_RATIO = 0.46f
private const val HOSE_START_Y_RATIO = 0.78f
private const val HOSE_END_Y_RATIO = 0.94f
private const val HOSE_SWAY_RATIO = 0.018f
private const val HOSE_FIRST_CONTROL_Y_RATIO = 0.34f
private const val HOSE_SECOND_CONTROL_Y_RATIO = 0.72f
private const val HOSE_END_SWAY_RATIO = 0.58f
private const val HOSE_SHADOW_WIDTH_RATIO = 0.070f
private const val HOSE_BASE_WIDTH_RATIO = 0.048f
private const val HOSE_HIGHLIGHT_WIDTH_RATIO = 0.014f
private const val IDENTITY_OUTER_CORNER_RADIUS_DP = 8
private const val IDENTITY_DECK_CORNER_RADIUS_DP = 6
private const val IDENTITY_BODY_SHADOW_DP = 2
private const val IDENTITY_EDGE_WIDTH_DP = 1
private const val IDENTITY_OUTER_INSET_DP = 2
private const val IDENTITY_DECK_INSET_DP = 2
private const val IDENTITY_HEAD_GAP_DP = 2
private val IDENTITY_OUTER_SHAPE = RoundedCornerShape(IDENTITY_OUTER_CORNER_RADIUS_DP.dp)
private val IDENTITY_DECK_SHAPE = RoundedCornerShape(IDENTITY_DECK_CORNER_RADIUS_DP.dp)
private val IDENTITY_BODY_SHADOW = IDENTITY_BODY_SHADOW_DP.dp
private val IDENTITY_EDGE_WIDTH = IDENTITY_EDGE_WIDTH_DP.dp
private val IDENTITY_OUTER_INSET = IDENTITY_OUTER_INSET_DP.dp
private val IDENTITY_DECK_INSET = IDENTITY_DECK_INSET_DP.dp
private val IDENTITY_HEAD_GAP = IDENTITY_HEAD_GAP_DP.dp

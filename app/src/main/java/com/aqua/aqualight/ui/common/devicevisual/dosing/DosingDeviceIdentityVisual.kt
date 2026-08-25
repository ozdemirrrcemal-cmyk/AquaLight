package com.aqua.aqualight.ui.common.devicevisual.dosing

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp

/**
 * Canonical Dose Pro product-identity visual.
 *
 * Unlike the operational Dosing screen facade, this identity intentionally includes hoses.
 * It is presentation-only and is safe to reuse in Devices, Tank Devices, Select and Settings.
 */
@Composable
internal fun DosingDeviceIdentityVisual(
    pumpCount: Int = DOSING_PRO_4_PUMP_COUNT,
    modifier: Modifier = Modifier
) {
    val exactPumpCount = normalizedPumpCount(pumpCount)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val bodyWidth = maxWidth * BODY_WIDTH_RATIO
        val bodyHeight = maxHeight * BODY_HEIGHT_RATIO
        val bodyTopOffset = maxHeight * BODY_TOP_OFFSET_RATIO

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIdentityHoses(exactPumpCount)
        }

        Box(
            modifier = Modifier
                .width(bodyWidth)
                .height(bodyHeight)
                .offset(y = bodyTopOffset)
                .shadow(
                    elevation = IDENTITY_SHADOW_ELEVATION,
                    shape = RoundedCornerShape(bodyWidth * OUTER_CORNER_RATIO),
                    clip = false
                )
                .background(
                    brush = DosingPumpVisualPalette.outerShell,
                    shape = RoundedCornerShape(bodyWidth * OUTER_CORNER_RATIO)
                )
                .border(
                    width = IDENTITY_EDGE_WIDTH,
                    color = DosingPumpVisualPalette.outerEdge,
                    shape = RoundedCornerShape(bodyWidth * OUTER_CORNER_RATIO)
                )
                .padding(bodyWidth * OUTER_INSET_RATIO)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = DosingPumpVisualPalette.innerShell,
                        shape = RoundedCornerShape(bodyWidth * INNER_CORNER_RATIO)
                    )
                    .border(
                        width = IDENTITY_EDGE_WIDTH,
                        color = DosingPumpVisualPalette.innerEdge,
                        shape = RoundedCornerShape(bodyWidth * INNER_CORNER_RATIO)
                    )
                    .padding(bodyWidth * INNER_INSET_RATIO),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = DosingPumpVisualPalette.metalDeck,
                            shape = RoundedCornerShape(bodyWidth * DECK_CORNER_RATIO)
                        )
                        .border(
                            width = IDENTITY_EDGE_WIDTH,
                            color = DosingPumpVisualPalette.metalHighlight,
                            shape = RoundedCornerShape(bodyWidth * DECK_CORNER_RATIO)
                        )
                        .padding(bodyWidth * DECK_INSET_RATIO),
                    contentAlignment = Alignment.Center
                ) {
                    val rowWidth = if (exactPumpCount == DOSING_PRO_2_PUMP_COUNT) {
                        bodyWidth * PRO_2_HEAD_ROW_WIDTH_RATIO
                    } else {
                        bodyWidth
                    }
                    Row(
                        modifier = Modifier.width(rowWidth),
                        horizontalArrangement = Arrangement.spacedBy(
                            bodyWidth * HEAD_SPACING_RATIO
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(exactPumpCount) {
                            DosingPumpHeadVisual(
                                compactGeometry = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** XML/View bridge for device identity surfaces that still use ViewBinding layouts. */
class DosingDeviceIdentityVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var pumpCountState by mutableIntStateOf(DOSING_PRO_4_PUMP_COUNT)

    var pumpCount: Int
        get() = pumpCountState
        set(value) {
            pumpCountState = normalizedPumpCount(value)
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Composable
    override fun Content() {
        DosingDeviceIdentityVisual(
            pumpCount = pumpCountState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIdentityHoses(pumpCount: Int) {
    val startRatios = if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
        floatArrayOf(0.36f, 0.64f)
    } else {
        floatArrayOf(0.22f, 0.405f, 0.595f, 0.78f)
    }
    val endRatios = if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
        floatArrayOf(0.27f, 0.73f)
    } else {
        floatArrayOf(0.10f, 0.35f, 0.65f, 0.90f)
    }

    startRatios.indices.forEach { index ->
        val start = Offset(
            x = size.width * startRatios[index],
            y = size.height * HOSE_START_Y_RATIO
        )
        val end = Offset(
            x = size.width * endRatios[index],
            y = size.height * HOSE_END_Y_RATIO
        )
        val outwardDirection = if (end.x < start.x) -1f else 1f
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                start.x + size.width * HOSE_CONTROL_X_RATIO * outwardDirection,
                size.height * HOSE_CONTROL_1_Y_RATIO,
                end.x - size.width * HOSE_CONTROL_X_RATIO * outwardDirection,
                size.height * HOSE_CONTROL_2_Y_RATIO,
                end.x,
                end.y
            )
        }
        val shadowWidth = size.minDimension * HOSE_SHADOW_WIDTH_RATIO
        val highlightWidth = size.minDimension * HOSE_HIGHLIGHT_WIDTH_RATIO
        drawPath(
            path = path,
            color = DosingPumpVisualPalette.hoseShadow,
            style = Stroke(width = shadowWidth, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = DosingPumpVisualPalette.hoseHighlight,
            style = Stroke(width = highlightWidth, cap = StrokeCap.Round)
        )
    }
}

private fun normalizedPumpCount(pumpCount: Int): Int =
    if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
        DOSING_PRO_2_PUMP_COUNT
    } else {
        DOSING_PRO_4_PUMP_COUNT
    }

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val BODY_WIDTH_RATIO = 0.92f
private const val BODY_HEIGHT_RATIO = 0.61f
private const val BODY_TOP_OFFSET_RATIO = 0.08f
private const val OUTER_CORNER_RATIO = 0.10f
private const val INNER_CORNER_RATIO = 0.08f
private const val DECK_CORNER_RATIO = 0.065f
private const val OUTER_INSET_RATIO = 0.035f
private const val INNER_INSET_RATIO = 0.035f
private const val DECK_INSET_RATIO = 0.045f
private const val PRO_2_HEAD_ROW_WIDTH_RATIO = 0.58f
private const val HEAD_SPACING_RATIO = 0.025f
private const val HOSE_START_Y_RATIO = 0.48f
private const val HOSE_END_Y_RATIO = 0.96f
private const val HOSE_CONTROL_1_Y_RATIO = 0.66f
private const val HOSE_CONTROL_2_Y_RATIO = 0.82f
private const val HOSE_CONTROL_X_RATIO = 0.09f
private const val HOSE_SHADOW_WIDTH_RATIO = 0.070f
private const val HOSE_HIGHLIGHT_WIDTH_RATIO = 0.043f
private val IDENTITY_SHADOW_ELEVATION = 2.dp
private val IDENTITY_EDGE_WIDTH = 0.5.dp

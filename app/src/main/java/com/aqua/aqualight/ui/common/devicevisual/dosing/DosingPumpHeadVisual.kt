package com.aqua.aqualight.ui.common.devicevisual.dosing

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Presentation-only visual state. It deliberately owns no Dosing runtime/domain state. */
internal enum class DosingPumpHeadVisualState {
    IDLE,
    SELECTED,
    RUNNING,
    ERROR
}

/**
 * Canonical Dose Pro pump-head drawing.
 *
 * The default geometry is bit-for-bit equivalent to the existing operational Dosing visual.
 * Compact geometry only scales fixed dp details for card-sized identities and markers.
 */
@Composable
internal fun DosingPumpHeadVisual(
    visualState: DosingPumpHeadVisualState? = DosingPumpHeadVisualState.IDLE,
    onClick: (() -> Unit)? = null,
    contentDescriptionText: String? = null,
    stateDescriptionText: String? = null,
    compactGeometry: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val semanticModifier = if (contentDescriptionText == null && stateDescriptionText == null) {
        Modifier
    } else {
        Modifier.semantics {
            contentDescriptionText?.let { contentDescription = it }
            stateDescriptionText?.let { stateDescription = it }
        }
    }
    val pressedScale = if (pressed) {
        DosingPumpVisualPrimitives.pressedScale
    } else {
        DosingPumpVisualPrimitives.normalScale
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val geometryScale = if (compactGeometry) {
            (maxWidth.value / DosingPumpVisualPrimitives.compactReferenceHeadSizeDp)
                .coerceIn(MIN_COMPACT_GEOMETRY_SCALE, DosingPumpVisualPrimitives.normalScale)
        } else {
            DosingPumpVisualPrimitives.normalScale
        }
        val outerShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp * geometryScale)
        val faceShape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp * geometryScale)
        val edgeWidth = scaledDp(DosingPumpVisualPrimitives.edgeWidth, geometryScale)
        val frameInset = scaledDp(DosingPumpVisualPrimitives.pumpFrameInset, geometryScale)
        val pumpShadow = scaledDp(DosingPumpVisualPrimitives.pumpShadowElevation, geometryScale)
        val hubShadow = scaledDp(DosingPumpVisualPrimitives.hubShadowElevation, geometryScale)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressedScale
                    scaleY = pressedScale
                }
                .shadow(
                    elevation = pumpShadow,
                    shape = outerShape,
                    clip = false
                )
                .clip(outerShape)
                .background(brush = DosingPumpVisualPalette.pumpFrame)
                .then(semanticModifier)
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                    }
                )
                .padding(frameInset),
            contentAlignment = Alignment.Center
        ) {
            val hubSize = maxWidth * DosingPumpVisualPrimitives.hubSizeRatio
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = DosingPumpVisualPalette.pumpFace,
                        shape = faceShape
                    )
                    .border(
                        width = edgeWidth,
                        color = DosingPumpVisualPalette.faceEdge,
                        shape = faceShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(hubSize)
                        .shadow(
                            elevation = hubShadow,
                            shape = CircleShape,
                            clip = false
                        )
                        .background(brush = DosingPumpVisualPalette.hub, shape = CircleShape)
                        .border(
                            width = edgeWidth,
                            color = DosingPumpVisualPalette.hubEdge,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DosingPumpIndicator(
                        visualState = visualState,
                        modifier = Modifier.size(
                            hubSize * DosingPumpVisualPrimitives.indicatorCanvasRatio
                        )
                    )
                }
            }
        }
    }
}

/** XML/View bridge for the neutral channel marker used by legacy card layouts. */
class DosingPumpHeadVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Composable
    override fun Content() {
        DosingPumpHeadVisual(
            compactGeometry = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DosingPumpIndicator(
    visualState: DosingPumpHeadVisualState?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dosing-pump-indicator")
    val runningScale by transition.animateFloat(
        initialValue = PULSE_MIN_SCALE,
        targetValue = PULSE_MAX_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RUNNING_PULSE_DURATION_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dosing-pump-running-scale"
    )
    val errorScale by transition.animateFloat(
        initialValue = ERROR_PULSE_MIN_SCALE,
        targetValue = ERROR_PULSE_MAX_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ERROR_PULSE_DURATION_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dosing-pump-error-scale"
    )
    val pulseScale = when (visualState) {
        null,
        DosingPumpHeadVisualState.IDLE,
        DosingPumpHeadVisualState.SELECTED -> DosingPumpVisualPrimitives.normalScale
        DosingPumpHeadVisualState.RUNNING -> runningScale
        DosingPumpHeadVisualState.ERROR -> errorScale
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
    ) {
        visualState?.let { state -> drawDosingPumpIndicator(state) }
    }
}

internal fun DrawScope.drawDosingPumpIndicator(visualState: DosingPumpHeadVisualState) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val coreRadius = outerRadius * INDICATOR_CORE_RATIO

    when (visualState) {
        DosingPumpHeadVisualState.IDLE -> Unit
        DosingPumpHeadVisualState.SELECTED,
        DosingPumpHeadVisualState.RUNNING -> drawIndicatorGlow(
            center = center,
            outerRadius = outerRadius,
            glowColor = DosingPumpVisualPalette.runningGlow
        )
        DosingPumpHeadVisualState.ERROR -> drawIndicatorGlow(
            center = center,
            outerRadius = outerRadius,
            glowColor = DosingPumpVisualPalette.errorGlow
        )
    }

    val indicatorBrush = when (visualState) {
        DosingPumpHeadVisualState.IDLE -> DosingPumpVisualPalette.idleIndicator
        DosingPumpHeadVisualState.SELECTED,
        DosingPumpHeadVisualState.RUNNING -> DosingPumpVisualPalette.runningIndicator
        DosingPumpHeadVisualState.ERROR -> DosingPumpVisualPalette.errorIndicator
    }

    drawCircle(
        brush = indicatorBrush,
        radius = coreRadius,
        center = center
    )
    drawCircle(
        color = DosingPumpVisualPalette.indicatorEdge,
        radius = coreRadius,
        center = center,
        style = Stroke(width = INDICATOR_EDGE_WIDTH.toPx())
    )
}

private fun DrawScope.drawIndicatorGlow(
    center: Offset,
    outerRadius: Float,
    glowColor: Color
) {
    drawCircle(
        color = glowColor.copy(alpha = GLOW_OUTER_ALPHA),
        radius = outerRadius,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = GLOW_MIDDLE_ALPHA),
        radius = outerRadius * GLOW_MIDDLE_RADIUS_RATIO,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = GLOW_INNER_ALPHA),
        radius = outerRadius * GLOW_INNER_RADIUS_RATIO,
        center = center
    )
}

private fun scaledDp(value: Dp, scale: Float): Dp = value * scale

private const val MIN_COMPACT_GEOMETRY_SCALE = 0.08f
private const val PULSE_MIN_SCALE = 0.94f
private const val PULSE_MAX_SCALE = 1.08f
private const val ERROR_PULSE_MIN_SCALE = 0.92f
private const val ERROR_PULSE_MAX_SCALE = 1.12f
private const val RUNNING_PULSE_DURATION_MS = 1_450
private const val ERROR_PULSE_DURATION_MS = 720
private const val INDICATOR_CORE_RATIO = 0.36f
private const val GLOW_OUTER_ALPHA = 0.10f
private const val GLOW_MIDDLE_ALPHA = 0.20f
private const val GLOW_INNER_ALPHA = 0.34f
private const val GLOW_MIDDLE_RADIUS_RATIO = 0.72f
private const val GLOW_INNER_RADIUS_RATIO = 0.50f
private val INDICATOR_EDGE_WIDTH = 1.dp

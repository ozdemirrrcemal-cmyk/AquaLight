package com.aqua.aqualight.ui.common.devicevisual.dosing

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

@Immutable
data class DosingPumpHeadUiState(
    val channelNumber: Int,
    val visualState: DosingPumpVisualState? = null
)

enum class DosingPumpVisualState(
    @StringRes val stateLabelRes: Int
) {
    IDLE(R.string.device_dosing_pump_state_idle),
    SELECTED(R.string.device_dosing_pump_state_selected),
    RUNNING(R.string.device_dosing_pump_state_running),
    ERROR(R.string.device_dosing_pump_state_error)
}

/**
 * Shared operational Dose Pro front-face renderer.
 *
 * Geometry intentionally matches the original Dosing surface implementation. Keep this component
 * free of repository/runtime state so every feature consumes the same visual without creating a
 * second Dosing state owner.
 */
@Composable
fun DosingPumpDevice(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    require(
        pumpHeads.size == DOSING_PRO_2_PUMP_COUNT ||
            pumpHeads.size == DOSING_PRO_4_PUMP_COUNT
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = DEVICE_SHADOW_ELEVATION,
                shape = DEVICE_OUTER_SHAPE,
                clip = false
            )
            .background(
                brush = DosingPumpPalette.outerShell,
                shape = DEVICE_OUTER_SHAPE
            )
            .border(
                width = DEVICE_EDGE_WIDTH,
                color = DosingPumpPalette.outerEdge,
                shape = DEVICE_OUTER_SHAPE
            )
            .padding(DEVICE_OUTER_INSET)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = DosingPumpPalette.innerShell,
                    shape = DEVICE_INNER_SHAPE
                )
                .border(
                    width = DEVICE_EDGE_WIDTH,
                    color = DosingPumpPalette.innerEdge,
                    shape = DEVICE_INNER_SHAPE
                )
                .padding(DEVICE_INNER_INSET)
        ) {
            DosingPumpDeck(
                pumpHeads = pumpHeads,
                onPumpClick = onPumpClick
            )
        }
    }
}

@Composable
private fun DosingPumpDeck(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: ((Int) -> Unit)?
) {
    val isDosingPro2 = pumpHeads.size == DOSING_PRO_2_PUMP_COUNT

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = DosingPumpPalette.metalDeck,
                shape = DEVICE_DECK_SHAPE
            )
            .border(
                width = DEVICE_EDGE_WIDTH,
                color = DosingPumpPalette.metalHighlight,
                shape = DEVICE_DECK_SHAPE
            )
            .padding(DEVICE_DECK_INSET),
        contentAlignment = Alignment.Center
    ) {
        val pro2PumpHeadSize = if (isDosingPro2) {
            minOf(
                DOSING_PRO_2_PUMP_HEAD_MAX_SIZE,
                (maxWidth - PUMP_SPACING) / DOSING_PRO_2_PUMP_COUNT.toFloat()
            )
        } else {
            0.dp
        }

        Row(
            modifier = if (isDosingPro2) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PUMP_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pumpHeads.forEach { pumpHead ->
                val pumpModifier = if (isDosingPro2) {
                    Modifier.size(pro2PumpHeadSize)
                } else {
                    Modifier
                        .weight(NORMAL_SCALE)
                        .aspectRatio(NORMAL_SCALE)
                }
                DosingPumpHead(
                    pumpHead = pumpHead,
                    onClick = onPumpClick?.let { click ->
                        { click(pumpHead.channelNumber) }
                    },
                    modifier = pumpModifier
                )
            }
        }
    }
}

@Composable
private fun DosingPumpHead(
    pumpHead: DosingPumpHeadUiState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visualState = pumpHead.visualState
    val stateLabel = visualState?.let { state -> stringResource(state.stateLabelRes) }
    val semanticModifier = if (stateLabel == null) {
        Modifier
    } else {
        val pumpDescription = stringResource(
            R.string.device_dosing_pump_channel_content_description,
            pumpHead.channelNumber,
            stateLabel
        )
        Modifier.semantics {
            contentDescription = pumpDescription
            stateDescription = stateLabel
        }
    }
    val pressedScale = if (pressed) PRESSED_SCALE else NORMAL_SCALE

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .shadow(
                elevation = PUMP_SHADOW_ELEVATION,
                shape = PUMP_OUTER_SHAPE,
                clip = false
            )
            .clip(PUMP_OUTER_SHAPE)
            .background(brush = DosingPumpPalette.pumpFrame)
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
            .padding(PUMP_FRAME_INSET),
        contentAlignment = Alignment.Center
    ) {
        DosingPumpFace(
            visualState = visualState,
            hubSize = maxWidth * HUB_SIZE_RATIO
        )
    }
}

@Composable
private fun DosingPumpFace(
    visualState: DosingPumpVisualState?,
    hubSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = DosingPumpPalette.pumpFace,
                shape = PUMP_FACE_SHAPE
            )
            .border(
                width = DEVICE_EDGE_WIDTH,
                color = DosingPumpPalette.faceEdge,
                shape = PUMP_FACE_SHAPE
            ),
        contentAlignment = Alignment.Center
    ) {
        DosingPumpHub(
            visualState = visualState,
            hubSize = hubSize
        )
    }
}

@Composable
private fun DosingPumpHub(
    visualState: DosingPumpVisualState?,
    hubSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(hubSize)
            .shadow(
                elevation = HUB_SHADOW_ELEVATION,
                shape = CircleShape,
                clip = false
            )
            .background(brush = DosingPumpPalette.hub, shape = CircleShape)
            .border(
                width = DEVICE_EDGE_WIDTH,
                color = DosingPumpPalette.hubEdge,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        PumpIndicator(
            visualState = visualState,
            modifier = Modifier.size(hubSize * INDICATOR_CANVAS_RATIO)
        )
    }
}

@Composable
private fun PumpIndicator(
    visualState: DosingPumpVisualState?,
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
        DosingPumpVisualState.IDLE,
        DosingPumpVisualState.SELECTED -> NORMAL_SCALE
        DosingPumpVisualState.RUNNING -> runningScale
        DosingPumpVisualState.ERROR -> errorScale
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
    ) {
        visualState?.let { state -> drawPumpIndicator(state) }
    }
}

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val NORMAL_SCALE = 1f
private const val PRESSED_SCALE = 0.965f
private const val PULSE_MIN_SCALE = 0.94f
private const val PULSE_MAX_SCALE = 1.08f
private const val ERROR_PULSE_MIN_SCALE = 0.92f
private const val ERROR_PULSE_MAX_SCALE = 1.12f
private const val HUB_SIZE_RATIO = 0.42f
private const val INDICATOR_CANVAS_RATIO = 0.82f
private const val RUNNING_PULSE_DURATION_MS = 1_450
private const val ERROR_PULSE_DURATION_MS = 720
private const val DOSING_PRO_2_PUMP_HEAD_MAX_SIZE_DP = 104
private const val DEVICE_OUTER_CORNER_RADIUS_DP = 30
private const val DEVICE_INNER_CORNER_RADIUS_DP = 24
private const val DEVICE_DECK_CORNER_RADIUS_DP = 20
private const val PUMP_OUTER_CORNER_RADIUS_DP = 20
private const val PUMP_FACE_CORNER_RADIUS_DP = 15
private const val DEVICE_SHADOW_ELEVATION_DP = 18
private const val PUMP_SHADOW_ELEVATION_DP = 8
private const val HUB_SHADOW_ELEVATION_DP = 5
private const val DEVICE_EDGE_WIDTH_DP = 1
private const val DEVICE_OUTER_INSET_DP = 7
private const val DEVICE_INNER_INSET_DP = 7
private const val DEVICE_DECK_INSET_DP = 9
private const val PUMP_FRAME_INSET_DP = 7
private const val PUMP_SPACING_DP = 8
private val DOSING_PRO_2_PUMP_HEAD_MAX_SIZE = DOSING_PRO_2_PUMP_HEAD_MAX_SIZE_DP.dp
private val DEVICE_OUTER_SHAPE = RoundedCornerShape(DEVICE_OUTER_CORNER_RADIUS_DP.dp)
private val DEVICE_INNER_SHAPE = RoundedCornerShape(DEVICE_INNER_CORNER_RADIUS_DP.dp)
private val DEVICE_DECK_SHAPE = RoundedCornerShape(DEVICE_DECK_CORNER_RADIUS_DP.dp)
private val PUMP_OUTER_SHAPE = RoundedCornerShape(PUMP_OUTER_CORNER_RADIUS_DP.dp)
private val PUMP_FACE_SHAPE = RoundedCornerShape(PUMP_FACE_CORNER_RADIUS_DP.dp)
private val DEVICE_SHADOW_ELEVATION = DEVICE_SHADOW_ELEVATION_DP.dp
private val PUMP_SHADOW_ELEVATION = PUMP_SHADOW_ELEVATION_DP.dp
private val HUB_SHADOW_ELEVATION = HUB_SHADOW_ELEVATION_DP.dp
private val DEVICE_EDGE_WIDTH = DEVICE_EDGE_WIDTH_DP.dp
private val DEVICE_OUTER_INSET = DEVICE_OUTER_INSET_DP.dp
private val DEVICE_INNER_INSET = DEVICE_INNER_INSET_DP.dp
private val DEVICE_DECK_INSET = DEVICE_DECK_INSET_DP.dp
private val PUMP_FRAME_INSET = PUMP_FRAME_INSET_DP.dp
private val PUMP_SPACING = PUMP_SPACING_DP.dp

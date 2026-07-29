package com.aqua.aqualight.ui.tabs.devices.detail.dosing

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

@Immutable
data class DosingPumpHeadUiState(
    val channelNumber: Int,
    val visualState: DosingPumpVisualState = DosingPumpVisualState.IDLE
)

enum class DosingPumpVisualState(
    @StringRes val stateLabelRes: Int
) {
    IDLE(R.string.device_dosing_pump_state_idle),
    RUNNING(R.string.device_dosing_pump_state_running),
    ERROR(R.string.device_dosing_pump_state_error)
}

@Composable
fun DeviceDosingPumpScreen(
    pumpCount: Int,
    pumpStates: List<DosingPumpVisualState> = emptyList(),
    onPumpClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val supportedPumpCount = normalizeDosingPumpCount(pumpCount)
    val heads = List(supportedPumpCount) { index ->
        DosingPumpHeadUiState(
            channelNumber = index + 1,
            visualState = pumpStates.getOrElse(index) { DosingPumpVisualState.IDLE }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        val widthLimit = if (supportedPumpCount == DOSING_PRO_2_PUMP_COUNT) {
            DOSING_PRO_2_MAX_WIDTH
        } else {
            DOSING_PRO_4_MAX_WIDTH
        }
        val deviceWidth = if (maxWidth > widthLimit) widthLimit else maxWidth

        DosingPumpDevice(
            pumpHeads = heads,
            onPumpClick = onPumpClick,
            modifier = Modifier.width(deviceWidth)
        )
    }
}

@Composable
fun DosingPumpDevice(
    pumpHeads: List<DosingPumpHeadUiState>,
    onPumpClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(pumpHeads.size == DOSING_PRO_2_PUMP_COUNT || pumpHeads.size == DOSING_PRO_4_PUMP_COUNT)

    val outerShape = RoundedCornerShape(30.dp)
    val innerShape = RoundedCornerShape(24.dp)
    val deckShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .shadow(elevation = 18.dp, shape = outerShape, clip = false)
            .background(brush = DosingPumpPalette.outerShell, shape = outerShape)
            .border(width = 1.dp, color = DosingPumpPalette.outerEdge, shape = outerShape)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = DosingPumpPalette.innerShell, shape = innerShape)
                .border(width = 1.dp, color = DosingPumpPalette.innerEdge, shape = innerShape)
                .padding(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = DosingPumpPalette.metalDeck, shape = deckShape)
                    .border(width = 1.dp, color = DosingPumpPalette.metalHighlight, shape = deckShape)
                    .padding(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pumpHeads.forEach { pumpHead ->
                        DosingPumpHead(
                            pumpHead = pumpHead,
                            onClick = { onPumpClick(pumpHead.channelNumber) },
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

@Composable
private fun DosingPumpHead(
    pumpHead: DosingPumpHeadUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) PRESSED_SCALE else NORMAL_SCALE
    val stateLabel = stringResource(pumpHead.visualState.stateLabelRes)
    val pumpDescription = stringResource(
        R.string.device_dosing_pump_channel_content_description,
        pumpHead.channelNumber,
        stateLabel
    )
    val outerShape = RoundedCornerShape(20.dp)
    val faceShape = RoundedCornerShape(15.dp)

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(elevation = 8.dp, shape = outerShape, clip = false)
            .clip(outerShape)
            .background(brush = DosingPumpPalette.pumpFrame)
            .semantics {
                contentDescription = pumpDescription
                stateDescription = stateLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = DosingPumpPalette.pumpFace, shape = faceShape)
                .border(width = 1.dp, color = DosingPumpPalette.faceEdge, shape = faceShape),
            contentAlignment = Alignment.Center
        ) {
            val hubSize = maxWidth * HUB_SIZE_RATIO

            Box(
                modifier = Modifier
                    .size(hubSize)
                    .shadow(elevation = 5.dp, shape = CircleShape, clip = false)
                    .background(brush = DosingPumpPalette.hub, shape = CircleShape)
                    .border(width = 1.dp, color = DosingPumpPalette.hubEdge, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PumpIndicator(
                    visualState = pumpHead.visualState,
                    modifier = Modifier.size(hubSize * INDICATOR_CANVAS_RATIO)
                )
            }
        }
    }
}

@Composable
private fun PumpIndicator(
    visualState: DosingPumpVisualState,
    modifier: Modifier = Modifier
) {
    val pulseScale = when (visualState) {
        DosingPumpVisualState.IDLE -> NORMAL_SCALE
        DosingPumpVisualState.RUNNING,
        DosingPumpVisualState.ERROR -> {
            val transition = rememberInfiniteTransition(label = "dosing-pump-indicator")
            val duration = if (visualState == DosingPumpVisualState.ERROR) {
                ERROR_PULSE_DURATION_MS
            } else {
                RUNNING_PULSE_DURATION_MS
            }
            val scale by transition.animateFloat(
                initialValue = PULSE_MIN_SCALE,
                targetValue = PULSE_MAX_SCALE,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dosing-pump-indicator-scale"
            )
            scale
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2f
        val coreRadius = baseRadius * INDICATOR_CORE_RATIO

        when (visualState) {
            DosingPumpVisualState.IDLE -> Unit
            DosingPumpVisualState.RUNNING -> drawIndicatorGlow(
                center = center,
                baseRadius = baseRadius,
                glowColor = DosingPumpPalette.runningGlow
            )
            DosingPumpVisualState.ERROR -> drawIndicatorGlow(
                center = center,
                baseRadius = baseRadius,
                glowColor = DosingPumpPalette.errorGlow
            )
        }

        val coreBrush = when (visualState) {
            DosingPumpVisualState.IDLE -> DosingPumpPalette.idleIndicator
            DosingPumpVisualState.RUNNING -> DosingPumpPalette.runningIndicator
            DosingPumpVisualState.ERROR -> DosingPumpPalette.errorIndicator
        }

        drawCircle(
            brush = coreBrush,
            radius = coreRadius,
            center = center
        )
        drawCircle(
            color = DosingPumpPalette.indicatorEdge,
            radius = coreRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIndicatorGlow(
    center: Offset,
    baseRadius: Float,
    glowColor: Color
) {
    drawCircle(
        color = glowColor.copy(alpha = 0.10f),
        radius = baseRadius,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = 0.20f),
        radius = baseRadius * 0.72f,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = 0.34f),
        radius = baseRadius * 0.50f,
        center = center
    )
}

internal fun normalizeDosingPumpCount(pumpCount: Int): Int {
    return if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
        DOSING_PRO_2_PUMP_COUNT
    } else {
        DOSING_PRO_4_PUMP_COUNT
    }
}

private object DosingPumpPalette {
    val outerEdge = Color(0x52FFFFFF)
    val innerEdge = Color(0x1FFFFFFF)
    val metalHighlight = Color(0xA6FFFFFF)
    val faceEdge = Color(0x24FFFFFF)
    val hubEdge = Color(0x4DFFFFFF)
    val indicatorEdge = Color(0xB3000000)
    val runningGlow = Color(0xFF49F28F)
    val errorGlow = Color(0xFFFF5361)

    val outerShell = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF3A3F46),
            Color(0xFF15191E),
            Color(0xFF050608)
        )
    )
    val innerShell = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF15181C),
            Color(0xFF080A0D),
            Color(0xFF030405)
        )
    )
    val metalDeck = Brush.horizontalGradient(
        0.00f to Color(0xFF3B4047),
        0.11f to Color(0xFFAEB3B9),
        0.19f to Color(0xFF5E646C),
        0.30f to Color(0xFFD8DBDE),
        0.44f to Color(0xFF666C74),
        0.58f to Color(0xFFB8BDC3),
        0.73f to Color(0xFF555B63),
        0.87f to Color(0xFFD4D7DA),
        1.00f to Color(0xFF4B5057)
    )
    val pumpFrame = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF1F2F4),
            Color(0xFF8C9299),
            Color(0xFF292E34),
            Color(0xFFA3A8AE),
            Color(0xFF3B4046)
        )
    )
    val pumpFace = Brush.linearGradient(
        colors = listOf(
            Color(0xFF191C21),
            Color(0xFF050608),
            Color(0xFF101318)
        )
    )
    val hub = Brush.radialGradient(
        colors = listOf(
            Color(0xFF8A919A),
            Color(0xFF343941),
            Color(0xFF15181C),
            Color(0xFF050607)
        )
    )
    val idleIndicator = Brush.radialGradient(
        colors = listOf(
            Color(0xFFD5D9DE),
            Color(0xFF888F98),
            Color(0xFF5B626C),
            Color(0xFF2A2F35)
        )
    )
    val runningIndicator = Brush.radialGradient(
        colors = listOf(
            Color(0xFFEFFFF5),
            Color(0xFF85FFB4),
            Color(0xFF38EC80),
            Color(0xFF0F7C3B)
        )
    )
    val errorIndicator = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFFF3F4),
            Color(0xFFFF9BA4),
            Color(0xFFFF5361),
            Color(0xFF8C1723)
        )
    )
}

private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val NORMAL_SCALE = 1f
private const val PRESSED_SCALE = 0.965f
private const val PULSE_MIN_SCALE = 0.94f
private const val PULSE_MAX_SCALE = 1.08f
private const val HUB_SIZE_RATIO = 0.42f
private const val INDICATOR_CANVAS_RATIO = 0.82f
private const val INDICATOR_CORE_RATIO = 0.36f
private const val RUNNING_PULSE_DURATION_MS = 1_450
private const val ERROR_PULSE_DURATION_MS = 720
private val DOSING_PRO_2_MAX_WIDTH: Dp = 320.dp
private val DOSING_PRO_4_MAX_WIDTH: Dp = 760.dp

@Preview(name = "Dosing Pro 4", showBackground = true, backgroundColor = 0xFF080A0D)
@Composable
private fun DosingPro4Preview() {
    DeviceDosingPumpScreen(
        pumpCount = DOSING_PRO_4_PUMP_COUNT,
        pumpStates = listOf(
            DosingPumpVisualState.IDLE,
            DosingPumpVisualState.RUNNING,
            DosingPumpVisualState.ERROR,
            DosingPumpVisualState.IDLE
        )
    )
}

@Preview(name = "Dosing Pro 2", showBackground = true, backgroundColor = 0xFF080A0D)
@Composable
private fun DosingPro2Preview() {
    DeviceDosingPumpScreen(
        pumpCount = DOSING_PRO_2_PUMP_COUNT,
        pumpStates = listOf(
            DosingPumpVisualState.RUNNING,
            DosingPumpVisualState.ERROR
        )
    )
}

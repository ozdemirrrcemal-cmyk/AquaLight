package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import io.github.sceneview.Scene
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.sin

@Composable
internal fun CoolingFanRealtimeCard(
    fanPercent: Int?,
    colors: AquaDeviceCardColors,
    modifier: Modifier = Modifier
) {
    val resolvedPercent = fanPercent
        ?.coerceIn(CoolingFanVisualSpec.minimumPercent, CoolingFanVisualSpec.maximumPercent)
        ?: CoolingFanVisualSpec.previewPercent
    val visualRpm = fanPercent?.let {
        CoolingFanVisualSpec.minimumVisualRpm +
            (CoolingFanVisualSpec.maximumVisualRpm - CoolingFanVisualSpec.minimumVisualRpm) *
            resolvedPercent.toFloat() / CoolingFanVisualSpec.maximumPercent.toFloat()
    } ?: CoolingFanVisualSpec.previewVisualRpm

    var rotorAngleDegrees by remember { mutableFloatStateOf(CoolingFanVisualSpec.initialRotorAngle) }

    LaunchedEffect(visualRpm) {
        if (visualRpm <= CoolingFanVisualSpec.stoppedVisualRpm) return@LaunchedEffect

        var previousFrameNanos = withFrameNanos { it }
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameNanos ->
                val elapsedSeconds = (
                    (frameNanos - previousFrameNanos).toFloat() /
                        CoolingFanVisualSpec.nanosecondsPerSecond
                    ).coerceAtMost(CoolingFanVisualSpec.maximumFrameDeltaSeconds)
                rotorAngleDegrees = (
                    rotorAngleDegrees +
                        visualRpm * CoolingFanVisualSpec.degreesPerSecondPerRpm * elapsedSeconds
                    ) % CoolingFanVisualSpec.fullRotationDegrees
                previousFrameNanos = frameNanos
            }
        }
    }

    AquaCoolingDashboardCardSurface(
        modifier = modifier.height(CoolingFanVisualSpec.cardHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoolingFanScene(
                rotorAngleDegrees = rotorAngleDegrees,
                modifier = Modifier.fillMaxSize()
            )
            CoolingAirflowOverlay(
                phaseDegrees = rotorAngleDegrees,
                strength = resolvedPercent.toFloat() / CoolingFanVisualSpec.maximumPercent.toFloat(),
                accentColor = colors.accent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CoolingFanScene(
    rotorAngleDegrees: Float,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val targetNode = rememberNode(engine) {
        position = Position(z = CoolingFanVisualSpec.cameraTargetZ)
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(
            x = CoolingFanVisualSpec.cameraX,
            y = CoolingFanVisualSpec.cameraY,
            z = CoolingFanVisualSpec.cameraZ
        )
        lookAt(targetNode)
    }
    val bodyInstance = rememberModelInstance(
        modelLoader = modelLoader,
        assetFileLocation = CoolingFanVisualSpec.bodyAssetPath
    )
    val rotorInstance = rememberModelInstance(
        modelLoader = modelLoader,
        assetFileLocation = CoolingFanVisualSpec.rotorAssetPath
    )

    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = null,
        isOpaque = false,
        onGestureListener = null
    ) {
        bodyInstance?.let { instance ->
            ModelNode(modelInstance = instance)
        }
        rotorInstance?.let { instance ->
            ModelNode(
                modelInstance = instance,
                position = Position(y = CoolingFanVisualSpec.rotorCenterY),
                rotation = Rotation(z = rotorAngleDegrees)
            )
        }
    }
}

@Composable
private fun CoolingAirflowOverlay(
    phaseDegrees: Float,
    strength: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val normalizedStrength = strength.coerceIn(
        CoolingFanVisualSpec.minimumStrength,
        CoolingFanVisualSpec.maximumStrength
    )
    if (normalizedStrength <= CoolingFanVisualSpec.minimumVisibleAirflowStrength) return

    Canvas(modifier = modifier) {
        val phaseRadians = phaseDegrees / CoolingFanVisualSpec.fullRotationDegrees *
            (CoolingFanVisualSpec.twoPi).toFloat()
        val originY = size.height * CoolingFanVisualSpec.airflowOriginYFraction
        val endY = size.height * CoolingFanVisualSpec.airflowEndYFraction
        val startX = size.width * CoolingFanVisualSpec.airflowStartXFraction
        val endX = size.width * CoolingFanVisualSpec.airflowEndXFraction

        repeat(CoolingFanVisualSpec.airflowStreamCount) { streamIndex ->
            val streamFraction = streamIndex.toFloat() /
                (CoolingFanVisualSpec.airflowStreamCount - 1).toFloat()
            val wave = sin(
                phaseRadians * CoolingFanVisualSpec.airflowPhaseMultiplier +
                    streamIndex * CoolingFanVisualSpec.airflowPhaseOffset
            ).toFloat()
            val verticalOffset = streamFraction * size.height *
                CoolingFanVisualSpec.airflowStreamSpacingFraction
            val lateralOffset = wave * size.width * CoolingFanVisualSpec.airflowWaveFraction
            val path = Path().apply {
                moveTo(startX + lateralOffset, originY + verticalOffset)
                cubicTo(
                    size.width * CoolingFanVisualSpec.airflowControlOneXFraction + lateralOffset,
                    size.height * CoolingFanVisualSpec.airflowControlOneYFraction + verticalOffset,
                    size.width * CoolingFanVisualSpec.airflowControlTwoXFraction - lateralOffset,
                    size.height * CoolingFanVisualSpec.airflowControlTwoYFraction + verticalOffset,
                    endX - lateralOffset,
                    endY + verticalOffset
                )
            }
            drawPath(
                path = path,
                color = accentColor.copy(
                    alpha = CoolingFanVisualSpec.airflowBaseAlpha * normalizedStrength *
                        (CoolingFanVisualSpec.airflowLeadingAlpha -
                            streamFraction * CoolingFanVisualSpec.airflowTrailingAlphaReduction)
                ),
                style = Stroke(
                    width = CoolingFanVisualSpec.airflowStrokeWidth.toPx()
                )
            )
        }
    }
}

private object CoolingFanVisualSpec {
    const val minimumPercent = 0
    const val maximumPercent = 100
    const val previewPercent = 68

    const val stoppedVisualRpm = 0f
    const val minimumVisualRpm = 420f
    const val maximumVisualRpm = 1_800f
    const val previewVisualRpm = 1_080f
    const val initialRotorAngle = 12f
    const val fullRotationDegrees = 360f
    const val degreesPerSecondPerRpm = 6f
    const val nanosecondsPerSecond = 1_000_000_000f
    const val maximumFrameDeltaSeconds = 0.05f

    const val bodyAssetPath = "models/cooling_fan_body.glb"
    const val rotorAssetPath = "models/cooling_fan_rotor.glb"
    const val rotorCenterY = 0.12f

    const val cameraX = 2.65f
    const val cameraY = -4.15f
    const val cameraZ = 2.55f
    const val cameraTargetZ = -0.30f

    val cardHeight = 224.dp

    const val minimumStrength = 0f
    const val maximumStrength = 1f
    const val minimumVisibleAirflowStrength = 0.01f
    const val twoPi = 6.283185307179586
    const val airflowStreamCount = 4
    const val airflowOriginYFraction = 0.57f
    const val airflowEndYFraction = 0.86f
    const val airflowStartXFraction = 0.30f
    const val airflowEndXFraction = 0.70f
    const val airflowControlOneXFraction = 0.37f
    const val airflowControlOneYFraction = 0.67f
    const val airflowControlTwoXFraction = 0.61f
    const val airflowControlTwoYFraction = 0.76f
    const val airflowStreamSpacingFraction = 0.025f
    const val airflowWaveFraction = 0.012f
    const val airflowPhaseMultiplier = 0.35f
    const val airflowPhaseOffset = 0.92f
    const val airflowBaseAlpha = 0.18f
    const val airflowLeadingAlpha = 1f
    const val airflowTrailingAlphaReduction = 0.35f
    val airflowStrokeWidth = 1.35.dp
}

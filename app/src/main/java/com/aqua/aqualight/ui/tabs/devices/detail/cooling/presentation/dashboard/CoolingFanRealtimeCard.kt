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
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
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
    val visualRpm = when {
        fanPercent == null -> CoolingFanVisualSpec.previewVisualRpm
        resolvedPercent == CoolingFanVisualSpec.minimumPercent -> CoolingFanVisualSpec.stoppedVisualRpm
        else -> CoolingFanVisualSpec.minimumVisualRpm +
            (CoolingFanVisualSpec.maximumVisualRpm - CoolingFanVisualSpec.minimumVisualRpm) *
            resolvedPercent.toFloat() / CoolingFanVisualSpec.maximumPercent.toFloat()
    }

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
    val materialLoader = rememberMaterialLoader(engine)
    val targetNode = rememberNode(engine) {
        position = Position(y = CoolingFanVisualSpec.cameraTargetY)
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(
            x = CoolingFanVisualSpec.cameraX,
            y = CoolingFanVisualSpec.cameraY,
            z = CoolingFanVisualSpec.cameraZ
        )
        lookAt(targetNode)
    }
    val graphite = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(CoolingFanVisualSpec.graphiteColor),
            metallic = CoolingFanVisualSpec.graphiteMetallic,
            roughness = CoolingFanVisualSpec.graphiteRoughness
        )
    }
    val machinedMetal = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(CoolingFanVisualSpec.machinedMetalColor),
            metallic = CoolingFanVisualSpec.machinedMetallic,
            roughness = CoolingFanVisualSpec.machinedMetalRoughness
        )
    }
    val deepBlack = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(CoolingFanVisualSpec.deepBlackColor),
            metallic = CoolingFanVisualSpec.deepBlackMetallic,
            roughness = CoolingFanVisualSpec.deepBlackRoughness
        )
    }
    val aqua = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(CoolingFanVisualSpec.aquaColor),
            metallic = CoolingFanVisualSpec.aquaMetallic,
            roughness = CoolingFanVisualSpec.aquaRoughness
        )
    }

    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        cameraManipulator = null,
        isOpaque = false,
        onGestureListener = null
    ) {
        CubeNode(
            size = Size(x = 2.55f, y = 0.40f, z = 1.58f),
            materialInstance = graphite
        )
        CubeNode(
            size = Size(x = 2.38f, y = 0.035f, z = 1.44f),
            materialInstance = machinedMetal,
            position = Position(y = 0.218f)
        )

        CylinderNode(
            radius = 0.76f,
            height = 0.038f,
            sideCount = CoolingFanVisualSpec.roundSideCount,
            materialInstance = machinedMetal,
            position = Position(y = 0.255f, z = -0.03f)
        )
        CylinderNode(
            radius = 0.725f,
            height = 0.035f,
            sideCount = CoolingFanVisualSpec.roundSideCount,
            materialInstance = aqua,
            position = Position(y = 0.278f, z = -0.03f)
        )
        CylinderNode(
            radius = 0.675f,
            height = 0.043f,
            sideCount = CoolingFanVisualSpec.roundSideCount,
            materialInstance = deepBlack,
            position = Position(y = 0.300f, z = -0.03f)
        )

        Node(
            position = Position(y = 0.334f, z = -0.03f),
            rotation = Rotation(y = rotorAngleDegrees)
        ) {
            repeat(CoolingFanVisualSpec.bladeCount) { bladeIndex ->
                val bladeRotation = bladeIndex * CoolingFanVisualSpec.bladeStepDegrees +
                    CoolingFanVisualSpec.bladePitchDegrees
                Node(rotation = Rotation(y = bladeRotation)) {
                    CubeNode(
                        size = Size(x = 0.56f, y = 0.042f, z = 0.18f),
                        materialInstance = deepBlack,
                        position = Position(x = 0.35f),
                        rotation = Rotation(y = CoolingFanVisualSpec.bladeSkewDegrees)
                    )
                }
            }
            CylinderNode(
                radius = 0.18f,
                height = 0.075f,
                sideCount = CoolingFanVisualSpec.roundSideCount,
                materialInstance = machinedMetal,
                position = Position(y = 0.035f)
            )
        }

        CubeNode(
            size = Size(x = 1.92f, y = 0.33f, z = 0.045f),
            materialInstance = deepBlack,
            position = Position(y = -0.03f, z = 0.808f)
        )
        listOf(-0.17f, 0.17f).forEach { y ->
            CubeNode(
                size = Size(x = 2.02f, y = 0.045f, z = 0.05f),
                materialInstance = machinedMetal,
                position = Position(y = y, z = 0.835f)
            )
        }
        listOf(-0.72f, -0.36f, 0f, 0.36f, 0.72f).forEach { x ->
            CubeNode(
                size = Size(x = 0.018f, y = 0.27f, z = 0.024f),
                materialInstance = machinedMetal,
                position = Position(x = x, y = -0.03f, z = 0.842f)
            )
        }
        listOf(-0.12f, -0.06f, 0f, 0.06f, 0.12f).forEach { y ->
            CubeNode(
                size = Size(x = 1.84f, y = 0.014f, z = 0.024f),
                materialInstance = machinedMetal,
                position = Position(y = y - 0.03f, z = 0.844f)
            )
        }

        CubeNode(
            size = Size(x = 0.78f, y = 0.20f, z = 0.58f),
            materialInstance = graphite,
            position = Position(x = 0.55f, y = -0.31f, z = 0.20f)
        )
        CubeNode(
            size = Size(x = 0.30f, y = 0.76f, z = 0.40f),
            materialInstance = graphite,
            position = Position(x = 0.55f, y = -0.74f, z = 0.20f)
        )
        CubeNode(
            size = Size(x = 0.72f, y = 0.14f, z = 0.72f),
            materialInstance = machinedMetal,
            position = Position(x = 0.55f, y = -1.19f, z = 0.20f)
        )
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
            CoolingFanVisualSpec.twoPi.toFloat()
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
                style = Stroke(width = CoolingFanVisualSpec.airflowStrokeWidth.toPx())
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

    const val bladeCount = 6
    const val bladeStepDegrees = fullRotationDegrees / bladeCount
    const val bladePitchDegrees = 12f
    const val bladeSkewDegrees = 18f
    const val roundSideCount = 36

    const val cameraX = 2.80f
    const val cameraY = 2.45f
    const val cameraZ = 4.25f
    const val cameraTargetY = -0.28f

    const val graphiteColor = 0xFF202832
    const val machinedMetalColor = 0xFF65707D
    const val deepBlackColor = 0xFF040A10
    const val aquaColor = 0xFF21D7F3
    const val graphiteMetallic = 0.90f
    const val graphiteRoughness = 0.23f
    const val machinedMetallic = 0.96f
    const val machinedMetalRoughness = 0.16f
    const val deepBlackMetallic = 0.72f
    const val deepBlackRoughness = 0.18f
    const val aquaMetallic = 0.30f
    const val aquaRoughness = 0.14f

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

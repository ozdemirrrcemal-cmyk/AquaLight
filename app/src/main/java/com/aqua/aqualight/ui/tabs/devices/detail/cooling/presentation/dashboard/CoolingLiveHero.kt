package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.fanMotionDegreesPerSecond
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState
import kotlinx.coroutines.isActive

@Composable
internal fun CoolingLiveHero(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    modifier: Modifier = Modifier
) {
    val presentation = state.toCoolingHeroPresentation()
    val motion = presentation.resolveMotion()
    val motionPhases = coolingHeroMotionPhases(motion)
    val shape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .height(AquaCoolingDashboardGeometry.liveHeroHeight)
            .clip(shape)
            .background(colors.surface)
            .border(AquaCoolingDashboardGeometry.liveHeroOutlineWidth, colors.outline, shape)
    ) {
        CoolingHeroScene(presentation, motion, motionPhases, colors)
        CoolingHeroDevice(
            presentation = presentation,
            motion = motion,
            motionPhases = motionPhases,
            modifier = Modifier
                .width(maxWidth * DEVICE_WIDTH_FRACTION)
                .aspectRatio(DEVICE_ASPECT_RATIO)
                .align(Alignment.TopEnd)
                .offset(
                    x = AquaCoolingDashboardGeometry.liveHeroDeviceEndOffset,
                    y = AquaCoolingDashboardGeometry.liveHeroDeviceTopOffset
                )
        )
    }
}

@Composable
private fun CoolingHeroScene(
    presentation: CoolingHeroPresentation,
    motion: CoolingHeroMotion,
    motionPhases: CoolingHeroMotionPhases,
    colors: AquaDeviceCardColors
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCoolingHeroScene(
            primaryMotionPhase = motionPhases.waterPrimary,
            secondaryMotionPhase = motionPhases.waterSecondary,
            motionIntensity = motion.intensity,
            status = presentation.status,
            colors = colors
        )
    }
}

@Composable
private fun CoolingHeroDevice(
    presentation: CoolingHeroPresentation,
    motion: CoolingHeroMotion,
    motionPhases: CoolingHeroMotionPhases,
    modifier: Modifier
) {
    val deviceImage = ImageBitmap.imageResource(R.drawable.ic_device_cooling)
    val deviceAlpha = presentation.deviceAlpha()

    Box(modifier = modifier) {
        Image(
            bitmap = deviceImage,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(deviceAlpha)
        )
        if (motion.isActive && motionPhases.isAnimating) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(deviceAlpha)
            ) {
                drawCoolingFanRotor(
                    deviceImage = deviceImage,
                    rotationPhase = motionPhases.fan
                )
            }
        }
    }
}

@Composable
private fun coolingHeroMotionPhases(motion: CoolingHeroMotion): CoolingHeroMotionPhases {
    val fanPhase = remember { mutableFloatStateOf(NO_MOTION) }
    val primaryWaterPhase = remember { mutableFloatStateOf(NO_MOTION) }
    val secondaryWaterPhase = remember { mutableFloatStateOf(SECONDARY_WATER_INITIAL_PHASE) }
    val currentIntensity = rememberUpdatedState(motion.intensity)
    val animationActive = motion.isActive && ValueAnimator.areAnimatorsEnabled()

    LaunchedEffect(animationActive) {
        if (!animationActive) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { frameNanos -> frameNanos }
        while (isActive) {
            val frameNanos = withFrameNanos { currentFrameNanos -> currentFrameNanos }
            val elapsedNanos = (frameNanos - previousFrameNanos)
                .coerceIn(NO_ELAPSED_NANOS, MAX_FRAME_GAP_NANOS)
            val elapsedSeconds = elapsedNanos * NANOSECONDS_TO_SECONDS
            val intensity = currentIntensity.value.coerceIn(NO_MOTION, UNIT_FLOAT)
            fanPhase.floatValue = wrapPhase(
                fanPhase.floatValue +
                    fanMotionDegreesPerSecond(intensity) * elapsedSeconds / FULL_ROTATION_DEGREES
            )
            val waterCyclesPerSecond = waterCyclesPerSecond(intensity)
            primaryWaterPhase.floatValue = wrapPhase(
                primaryWaterPhase.floatValue + waterCyclesPerSecond * elapsedSeconds
            )
            secondaryWaterPhase.floatValue = wrapPhase(
                secondaryWaterPhase.floatValue +
                    waterCyclesPerSecond * SECONDARY_WATER_SPEED_RATIO * elapsedSeconds
            )
            previousFrameNanos = frameNanos
        }
    }
    return CoolingHeroMotionPhases(
        waterPrimary = primaryWaterPhase.floatValue,
        waterSecondary = secondaryWaterPhase.floatValue,
        fan = fanPhase.floatValue,
        isAnimating = animationActive
    )
}

private fun waterCyclesPerSecond(intensity: Float): Float {
    val durationMillis = SLOWEST_WATER_MOTION_MILLIS -
        (SLOWEST_WATER_MOTION_MILLIS - FASTEST_WATER_MOTION_MILLIS) * intensity
    return MILLISECONDS_PER_SECOND / durationMillis
}

private fun wrapPhase(value: Float): Float = value % UNIT_FLOAT

private fun CoolingHeroPresentation.deviceAlpha(): Float = when (status) {
    CoolingHeroVisualStatus.COOLING -> UNIT_FLOAT
    CoolingHeroVisualStatus.STANDBY -> AquaCoolingDashboardAlpha.liveHeroDeviceStandby
    CoolingHeroVisualStatus.ATTENTION,
    CoolingHeroVisualStatus.WAITING_FOR_DATA ->
        AquaCoolingDashboardAlpha.liveHeroDeviceUnavailable
    CoolingHeroVisualStatus.OFFLINE -> AquaCoolingDashboardAlpha.liveHeroDeviceOffline
}

private data class CoolingHeroMotionPhases(
    val waterPrimary: Float,
    val waterSecondary: Float,
    val fan: Float,
    val isAnimating: Boolean
)

private const val DEVICE_WIDTH_FRACTION = 0.62f
private const val DEVICE_ASPECT_RATIO = 1.3128655f
private const val SLOWEST_WATER_MOTION_MILLIS = 5_400f
private const val FASTEST_WATER_MOTION_MILLIS = 2_700f
private const val SECONDARY_WATER_INITIAL_PHASE = 0.37f
private const val SECONDARY_WATER_SPEED_RATIO = 0.618f
private const val MILLISECONDS_PER_SECOND = 1_000f
private const val FULL_ROTATION_DEGREES = 360f
private const val NANOSECONDS_TO_SECONDS = 1e-9f
private const val NO_ELAPSED_NANOS = 0L
private const val MAX_FRAME_GAP_NANOS = 50_000_000L
private const val NO_MOTION = 0f
private const val UNIT_FLOAT = 1f

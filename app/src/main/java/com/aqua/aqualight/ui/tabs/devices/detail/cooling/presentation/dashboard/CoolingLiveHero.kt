package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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
import com.aqua.aqualight.ui.common.cooling.fanMotionDurationMillis
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState

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
        CoolingHeroScene(presentation, motion, motionPhases.water, colors)
        CoolingHeroDevice(
            presentation = presentation,
            motion = motion,
            fanMotionPhase = motionPhases.fan,
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
    motionPhase: Float,
    colors: AquaDeviceCardColors
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCoolingHeroScene(
            motionPhase = motionPhase,
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
    fanMotionPhase: Float,
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
        if (motion.isActive) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(deviceAlpha)
            ) {
                drawCoolingFanRotor(
                    deviceImage = deviceImage,
                    rotationPhase = fanMotionPhase
                )
            }
        }
    }
}

@Composable
private fun coolingHeroMotionPhases(motion: CoolingHeroMotion): CoolingHeroMotionPhases {
    if (!motion.isActive) return CoolingHeroMotionPhases(NO_MOTION, NO_MOTION)
    val waterDuration = (
        SLOWEST_WATER_MOTION_MILLIS -
            (SLOWEST_WATER_MOTION_MILLIS - FASTEST_WATER_MOTION_MILLIS) * motion.intensity
        ).toInt()
    val fanDuration = fanMotionDurationMillis(motion.intensity)
    val transition = rememberInfiniteTransition(label = "cooling-hero-motion")
    val waterPhase by transition.animateFloat(
        initialValue = NO_MOTION,
        targetValue = UNIT_FLOAT,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waterDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cooling-hero-water-phase"
    )
    val fanPhase by transition.animateFloat(
        initialValue = NO_MOTION,
        targetValue = UNIT_FLOAT,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = fanDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cooling-hero-fan-phase"
    )
    return CoolingHeroMotionPhases(waterPhase, fanPhase)
}

private fun CoolingHeroPresentation.deviceAlpha(): Float = when (status) {
    CoolingHeroVisualStatus.COOLING -> UNIT_FLOAT
    CoolingHeroVisualStatus.STANDBY -> AquaCoolingDashboardAlpha.liveHeroDeviceStandby
    CoolingHeroVisualStatus.ATTENTION,
    CoolingHeroVisualStatus.WAITING_FOR_DATA ->
        AquaCoolingDashboardAlpha.liveHeroDeviceUnavailable
    CoolingHeroVisualStatus.OFFLINE -> AquaCoolingDashboardAlpha.liveHeroDeviceOffline
}

private data class CoolingHeroMotionPhases(
    val water: Float,
    val fan: Float
)

private const val DEVICE_WIDTH_FRACTION = 0.62f
private const val DEVICE_ASPECT_RATIO = 1.3128655f
private const val SLOWEST_WATER_MOTION_MILLIS = 5400
private const val FASTEST_WATER_MOTION_MILLIS = 3000
private const val NO_MOTION = 0f
private const val UNIT_FLOAT = 1f

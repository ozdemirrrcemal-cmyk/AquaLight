package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun CoolingLiveHero(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val presentation = state.toCoolingHeroPresentation()
    val motion = presentation.resolveMotion(
        allowWaitingMotion = booleanResource(R.bool.device_cooling_waiting_motion_enabled)
    )
    val motionPhases = coolingHeroMotionPhases(motion)
    val statusLabel = stringResource(presentation.status.labelResource())
    val temperatureText = coolingTemperatureText(presentation.temperatureC)
    val detailText = coolingHeroDetailText(presentation)
    val description = stringResource(
        R.string.device_cooling_hero_content_description,
        statusLabel,
        temperatureText,
        detailText
    )
    val copy = CoolingHeroCopy(statusLabel, temperatureText, detailText)
    val shape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .height(AquaCoolingDashboardGeometry.liveHeroHeight)
            .clip(shape)
            .background(colors.surface)
            .border(AquaCoolingDashboardGeometry.liveHeroOutlineWidth, colors.outline, shape)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        CoolingHeroScene(presentation, motion, motionPhases.water)
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
        CoolingHeroStatusPanel(
            presentation = presentation,
            copy = copy,
            motionPhase = motionPhases.water,
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun CoolingHeroScene(
    presentation: CoolingHeroPresentation,
    motion: CoolingHeroMotion,
    motionPhase: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCoolingHeroScene(
            motionPhase = motionPhase,
            motionIntensity = motion.intensity,
            status = presentation.status
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
private fun BoxScope.CoolingHeroStatusPanel(
    presentation: CoolingHeroPresentation,
    copy: CoolingHeroCopy,
    motionPhase: Float,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val panelShape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)
    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = AquaCoolingDashboardGeometry.liveHeroStatusPanelStartPadding)
            .width(AquaCoolingDashboardGeometry.liveHeroStatusPanelWidth)
            .clip(panelShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.surface.copy(alpha = AquaCoolingDashboardAlpha.liveHeroPanelTop),
                        colors.mediaSurface.copy(
                            alpha = AquaCoolingDashboardAlpha.liveHeroPanelBottom
                        )
                    )
                ),
                shape = panelShape
            )
            .border(
                AquaCoolingDashboardGeometry.liveHeroOutlineWidth,
                colors.outline.copy(alpha = AquaCoolingDashboardAlpha.liveHeroPanelOutline),
                panelShape
            )
            .padding(
                horizontal = AquaCoolingDashboardGeometry.liveHeroStatusPanelHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.liveHeroStatusPanelVerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.liveHeroStatusGap)
    ) {
        CoolingHeroStatusLabel(presentation, copy.statusLabel, motionPhase, colors, typography)
        BasicText(
            text = copy.temperatureText,
            style = typography.title.copy(
                color = colors.primaryText,
                fontSize = AquaCoolingDashboardTypography.liveHeroTemperatureSize
            )
        )
        BasicText(
            text = copy.detailText,
            style = typography.caption.copy(
                color = colors.secondaryText,
                fontSize = AquaCoolingDashboardTypography.liveHeroDetailSize
            )
        )
    }
}

@Composable
private fun CoolingHeroStatusLabel(
    presentation: CoolingHeroPresentation,
    statusLabel: String,
    motionPhase: Float,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val tone = presentation.status.toneColor(colors)
    val pulse = if (presentation.isCooling) {
        PULSE_BASE_ALPHA + PULSE_RANGE_ALPHA *
            ((
                sin((motionPhase * FULL_CIRCLE_RADIANS).toDouble()).toFloat() + UNIT_FLOAT
                ) / HALF_DIVISOR)
    } else {
        UNIT_FLOAT
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.liveHeroStatusGap)
    ) {
        Box(
            modifier = Modifier
                .size(AquaCoolingDashboardGeometry.liveHeroStatusDotSize)
                .clip(CircleShape)
                .background(tone.copy(alpha = pulse))
        )
        BasicText(
            text = statusLabel,
            style = typography.caption.copy(
                color = tone,
                fontSize = AquaCoolingDashboardTypography.liveHeroStatusSize
            )
        )
    }
}

@Composable
private fun coolingHeroDetailText(presentation: CoolingHeroPresentation): String {
    val percent = presentation.fanPercent
    return if (percent != null) {
        stringResource(
            R.string.device_cooling_hero_fan_mode_format,
            percent.coerceIn(MINIMUM_PERCENT, MAXIMUM_PERCENT),
            coolingModeLabel(presentation.mode)
        )
    } else {
        stringResource(R.string.device_cooling_hero_fan_unavailable)
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

/**
 * Fan angular velocity follows the applied PWM output proportionally.
 * At 100% output one visual revolution takes [FULL_OUTPUT_FAN_MOTION_MILLIS]; halving the
 * firmware-reported output doubles the period. The caller invokes this only for active motion.
 */
internal fun fanMotionDurationMillis(outputIntensity: Float): Int {
    val normalizedOutput = outputIntensity.coerceIn(MINIMUM_ACTIVE_FAN_INTENSITY, UNIT_FLOAT)
    return (FULL_OUTPUT_FAN_MOTION_MILLIS / normalizedOutput).roundToInt()
}

@StringRes
private fun CoolingHeroVisualStatus.labelResource(): Int = when (this) {
    CoolingHeroVisualStatus.COOLING -> R.string.device_cooling_automatic_status_cooling
    CoolingHeroVisualStatus.STANDBY -> R.string.device_cooling_status_ready
    CoolingHeroVisualStatus.ATTENTION -> R.string.device_cooling_status_warning
    CoolingHeroVisualStatus.WAITING_FOR_DATA -> R.string.device_cooling_hero_waiting_for_data
    CoolingHeroVisualStatus.OFFLINE -> R.string.device_cooling_status_offline
}

private fun CoolingHeroVisualStatus.toneColor(colors: AquaDeviceCardColors) = when (this) {
    CoolingHeroVisualStatus.COOLING -> colors.accent
    CoolingHeroVisualStatus.STANDBY -> AquaCoolingDashboardPalette.success
    CoolingHeroVisualStatus.ATTENTION -> colors.warning
    CoolingHeroVisualStatus.WAITING_FOR_DATA -> colors.secondaryText
    CoolingHeroVisualStatus.OFFLINE -> colors.secondaryText
}

private fun CoolingHeroPresentation.deviceAlpha(): Float = when (status) {
    CoolingHeroVisualStatus.COOLING -> UNIT_FLOAT
    CoolingHeroVisualStatus.STANDBY -> AquaCoolingDashboardAlpha.liveHeroDeviceStandby
    CoolingHeroVisualStatus.ATTENTION,
    CoolingHeroVisualStatus.WAITING_FOR_DATA ->
        AquaCoolingDashboardAlpha.liveHeroDeviceUnavailable
    CoolingHeroVisualStatus.OFFLINE -> AquaCoolingDashboardAlpha.liveHeroDeviceOffline
}

private data class CoolingHeroCopy(
    val statusLabel: String,
    val temperatureText: String,
    val detailText: String
)

private data class CoolingHeroMotionPhases(
    val water: Float,
    val fan: Float
)

private const val DEVICE_WIDTH_FRACTION = 0.62f
private const val DEVICE_ASPECT_RATIO = 1.3128655f
private const val FULL_CIRCLE_RADIANS = 6.2831855f
private const val PULSE_BASE_ALPHA = 0.72f
private const val PULSE_RANGE_ALPHA = 0.28f
private const val HALF_DIVISOR = 2f
private const val SLOWEST_WATER_MOTION_MILLIS = 5400
private const val FASTEST_WATER_MOTION_MILLIS = 3000
private const val FULL_OUTPUT_FAN_MOTION_MILLIS = 620
private const val MINIMUM_ACTIVE_FAN_INTENSITY = 0.01f
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
private const val NO_MOTION = 0f
private const val UNIT_FLOAT = 1f

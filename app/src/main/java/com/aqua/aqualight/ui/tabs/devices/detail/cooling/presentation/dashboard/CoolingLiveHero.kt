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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState
import kotlin.math.sin

@Composable
internal fun CoolingLiveHero(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val presentation = state.toCoolingHeroPresentation()
    val motionPhase = coolingHeroMotionPhase(presentation)
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
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        CoolingHeroScene(presentation, motionPhase)
        CoolingHeroDevice(
            presentation = presentation,
            motionPhase = motionPhase,
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
            motionPhase = motionPhase,
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun CoolingHeroScene(presentation: CoolingHeroPresentation, motionPhase: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCoolingHeroScene(
            motionPhase = motionPhase,
            motionIntensity = presentation.motionIntensity,
            status = presentation.status
        )
    }
}

@Composable
private fun CoolingHeroDevice(
    presentation: CoolingHeroPresentation,
    motionPhase: Float,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.ic_device_cooling),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(presentation.deviceAlpha())
        )
        if (presentation.isCooling) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringTopLeft = Offset(
                    size.width * FAN_RING_LEFT_FRACTION,
                    size.height * FAN_RING_TOP_FRACTION
                )
                val ringSize = Size(
                    size.width * FAN_RING_WIDTH_FRACTION,
                    size.height * FAN_RING_HEIGHT_FRACTION
                )
                drawArc(
                    color = AquaCoolingDashboardPalette.accent.copy(
                        alpha = AquaCoolingDashboardAlpha.liveHeroDeviceGlow
                    ),
                    startAngle = motionPhase * FULL_ROTATION_DEGREES,
                    sweepAngle = FAN_RING_HIGHLIGHT_DEGREES,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(
                        width = AquaCoolingDashboardGeometry.liveHeroDeviceHighlightWidth.toPx(),
                        cap = StrokeCap.Round
                    )
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
            .background(colors.surface.copy(alpha = AquaCoolingDashboardAlpha.liveHeroPanel))
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
private fun coolingHeroMotionPhase(presentation: CoolingHeroPresentation): Float {
    if (!presentation.isCooling) return NO_MOTION
    val duration = (
        SLOWEST_MOTION_MILLIS -
            (SLOWEST_MOTION_MILLIS - FASTEST_MOTION_MILLIS) * presentation.motionIntensity
        ).toInt()
    val transition = rememberInfiniteTransition(label = "cooling-hero-motion")
    val phase by transition.animateFloat(
        initialValue = NO_MOTION,
        targetValue = UNIT_FLOAT,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cooling-hero-phase"
    )
    return phase
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

private const val DEVICE_WIDTH_FRACTION = 0.62f
private const val DEVICE_ASPECT_RATIO = 1.3128655f
private const val FAN_RING_LEFT_FRACTION = 0.25f
private const val FAN_RING_TOP_FRACTION = 0.10f
private const val FAN_RING_WIDTH_FRACTION = 0.44f
private const val FAN_RING_HEIGHT_FRACTION = 0.37f
private const val FAN_RING_HIGHLIGHT_DEGREES = 72f
private const val FULL_ROTATION_DEGREES = 360f
private const val FULL_CIRCLE_RADIANS = 6.2831855f
private const val PULSE_BASE_ALPHA = 0.72f
private const val PULSE_RANGE_ALPHA = 0.28f
private const val HALF_DIVISOR = 2f
private const val SLOWEST_MOTION_MILLIS = 5400
private const val FASTEST_MOTION_MILLIS = 3000
private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
private const val NO_MOTION = 0f
private const val UNIT_FLOAT = 1f

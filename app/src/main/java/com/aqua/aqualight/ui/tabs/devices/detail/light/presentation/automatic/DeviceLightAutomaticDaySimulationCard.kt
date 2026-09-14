package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightAutomaticDaySimulationCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val preview = state.previewProgram
    val schedule = preview?.toSimulationSchedule()
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticEditorGeometry.simulationHeight),
        contentPadding = AquaDeviceCardGeometry.edgeToEdgeContentPadding
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.device_light_hero_card),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = DeviceLightAutomaticEditorAlpha.imageScrim))
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background((preview?.scene?.channels ?: emptyMap()).simulationColor().copy(
                        alpha = DeviceLightAutomaticEditorAlpha.imageColorOverlay
                    ))
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DeviceLightAutomaticEditorGeometry.simulationContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SimulationCopy(
                    state = state,
                    complete = preview != null,
                    visuals = visuals,
                    modifier = Modifier.weight(SIMULATION_COPY_WEIGHT)
                )
                DayDial(schedule, visuals, Modifier.size(DeviceLightAutomaticEditorGeometry.dialSize))
            }
        }
    }
}

@Composable
private fun SimulationCopy(
    state: DeviceLightAutomaticProgramEditorUiState,
    complete: Boolean,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    Column(modifier.padding(end = DeviceLightAutomaticEditorGeometry.simulationCopyGap)) {
        BasicText(
            text = state.source?.productDisplayName
                ?: stringResource(R.string.device_light_auto_editor_device_placeholder),
            style = visuals.typography.title.copy(color = visuals.colors.card.primaryText),
            maxLines = PRODUCT_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(DeviceLightAutomaticEditorGeometry.simulationTitleTopGap))
        SimulationStatusPill(state, complete, visuals)
        Spacer(Modifier.height(DeviceLightAutomaticEditorGeometry.simulationCopyGap))
        BasicText(
            text = stringResource(R.string.device_light_auto_editor_simulation_description),
            style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText),
            maxLines = SIMULATION_DESCRIPTION_MAX_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SimulationStatusPill(
    state: DeviceLightAutomaticProgramEditorUiState,
    complete: Boolean,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val labelRes = when {
        !complete -> R.string.device_light_auto_editor_incomplete
        state.draft.enabled -> R.string.device_light_auto_editor_active
        else -> R.string.device_light_auto_editor_passive
    }
    val accent = if (complete && state.draft.enabled) {
        visuals.colors.action
    } else {
        visuals.colors.card.secondaryText
    }
    Row(
        modifier = Modifier
            .height(DeviceLightAutomaticEditorGeometry.simulationStatusHeight)
            .clip(CircleShape)
            .background(accent.copy(alpha = DeviceLightAutomaticEditorAlpha.selectedSurface))
            .padding(horizontal = DeviceLightAutomaticEditorGeometry.simulationStatusHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(DeviceLightAutomaticEditorGeometry.simulationStatusDotSize)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.simulationStatusGap))
        BasicText(
            text = stringResource(labelRes),
            style = visuals.typography.micro.copy(color = accent)
        )
    }
}

private fun DeviceLightAutomaticProgram.toSimulationSchedule(): DaySimulationSchedule =
    DaySimulationSchedule(
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        rampDurationMs = rampDurationMs,
        durationMs = occupiedDuration(startTimeMs, endTimeMs)
    )

private fun Map<
    DeviceLightAutomaticChannel,
    Int
    >.simulationColor(): Color {
    val red = this[DeviceLightAutomaticChannel.RED].orZeroPercent()
    val green = this[DeviceLightAutomaticChannel.GREEN].orZeroPercent()
    val blue = this[DeviceLightAutomaticChannel.BLUE].orZeroPercent()
    val white = this[DeviceLightAutomaticChannel.WHITE].orZeroPercent()
    return Color(
        red = mixWithWhite(red, white),
        green = mixWithWhite(green, white),
        blue = mixWithWhite(blue, white)
    )
}

private fun Int?.orZeroPercent(): Float =
    (this ?: ZERO_PERCENT).toFloat() / MAX_PERCENT

private fun mixWithWhite(channel: Float, white: Float): Float =
    (channel * DeviceLightAutomaticDialSpec.colorMixWeight +
        white * DeviceLightAutomaticDialSpec.whiteMixWeight).coerceIn(
        DeviceLightAutomaticDialSpec.minimumOverlayChannel,
        DeviceLightAutomaticDialSpec.maximumOverlayChannel
    )

private fun occupiedDuration(startTimeMs: Long, endTimeMs: Long): Long =
    if (endTimeMs > startTimeMs) endTimeMs - startTimeMs
    else MILLIS_PER_DAY - startTimeMs + endTimeMs

private const val SIMULATION_COPY_WEIGHT = 1f
private const val PRODUCT_TITLE_MAX_LINES = 2
private const val SIMULATION_DESCRIPTION_MAX_LINES = 4
private const val ZERO_PERCENT = 0
private const val MAX_PERCENT = 100f
private const val MILLIS_PER_DAY = 86_400_000L

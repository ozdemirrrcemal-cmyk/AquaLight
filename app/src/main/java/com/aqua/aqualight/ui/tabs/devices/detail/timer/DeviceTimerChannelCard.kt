package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography

@Composable
@Suppress("LongParameterList")
internal fun DeviceTimerChannelCard(
    channel: DeviceTimerChannelUiState,
    interactionEnabled: Boolean,
    programEnabled: Boolean,
    temporaryOverrideEnabled: Boolean,
    mutationPending: Boolean,
    onRegimeSelected: (DeviceTimerChannelRegime) -> Unit,
    onProgramClick: () -> Unit,
    onTemporaryOverrideClick: (DeviceTimerChannelRegime) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaTimerDashboardColors()
    val typography = aquaTimerDashboardTypography(colors)
    val stateLabel = timerOperatingStateLabel(channel.operatingState)
    val name = channel.displayName.ifBlank { channel.defaultName }
    val description = stringResource(
        R.string.device_timer_channel_card_content_description,
        channel.channelNumber,
        name,
        stateLabel
    )
    val contentAlpha = if (interactionEnabled || mutationPending) {
        AquaTimerInteractionStyle.enabledContentAlpha
    } else {
        AquaTimerInteractionStyle.disabledContentAlpha
    }

    AquaDeviceCardSurface(
        modifier = modifier
            .semantics { contentDescription = description }
            .alpha(contentAlpha)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                AquaTimerDashboardGeometry.channelSectionGap
            )
        ) {
            TimerChannelHeader(
                channel = channel,
                name = name,
                stateLabel = stateLabel,
                colors = colors,
                typography = typography
            )
            TimerRegimeSelector(
                selected = channel.regime,
                enabled = interactionEnabled && !mutationPending,
                onSelected = onRegimeSelected,
                colors = colors,
                typography = typography
            )
            TimerDivider(colors)
            TimerChannelMetadata(
                channel = channel,
                mutationPending = mutationPending,
                colors = colors,
                typography = typography
            )
            TimerChannelActions(
                programEnabled = programEnabled && !mutationPending,
                temporaryOverrideEnabled = temporaryOverrideEnabled && !mutationPending,
                onProgramClick = onProgramClick,
                onTemporaryOverrideClick = onTemporaryOverrideClick,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun TimerChannelHeader(
    channel: DeviceTimerChannelUiState,
    name: String,
    stateLabel: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelHeaderGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerChannelProductIcon(colors)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelTextGap)
        ) {
            BasicText(
                text = stringResource(
                    R.string.device_timer_channel_title,
                    channel.channelNumber,
                    name
                ),
                style = typography.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = timerRegimeLabel(channel.regime),
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TimerStatePill(
            label = stateLabel,
            active = channel.operatingState == DeviceTimerOperatingState.ON,
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun TimerChannelProductIcon(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .size(AquaTimerDashboardGeometry.channelIconContainerSize)
            .clip(CircleShape)
            .background(colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.iconBackground))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.mediaOutline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_device_timer),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.channelIconSize),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TimerChannelMetadata(
    channel: DeviceTimerChannelUiState,
    mutationPending: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.metadataGap)
    ) {
        TimerMetadataRow(
            text = timerScheduleSummary(channel),
            tint = colors.secondaryText,
            typography = typography
        )
        TimerMetadataRow(
            text = timerRuntimeSummary(channel, mutationPending),
            tint = if (mutationPending) colors.accent else colors.secondaryText,
            typography = typography
        )
        if (channel.outputHealth == DeviceTimerOutputHealth.HARDWARE_FAULT) {
            TimerMetadataRow(
                text = stringResource(R.string.device_timer_output_fault),
                tint = colors.danger,
                typography = typography
            )
        }
    }
}

@Composable
private fun timerScheduleSummary(channel: DeviceTimerChannelUiState): String {
    val activeName = channel.activeScheduleName?.takeIf(String::isNotBlank)
    return when {
        activeName != null -> stringResource(R.string.device_timer_active_schedule, activeName)
        channel.scheduleCount == 0 -> stringResource(R.string.device_timer_schedule_none)
        else -> pluralStringResource(
            R.plurals.device_timer_schedule_count,
            channel.scheduleCount,
            channel.scheduleCount
        )
    }
}

@Composable
private fun timerRuntimeSummary(
    channel: DeviceTimerChannelUiState,
    mutationPending: Boolean
): String {
    val transitionAt = channel.nextTransitionAtEpochMillis
    return when {
        mutationPending -> stringResource(R.string.device_timer_updating)
        channel.temporaryOverrideActive -> timerTemporaryOverrideSummary(channel)
        transitionAt == null || channel.nextTransitionType == DeviceTimerNextTransitionType.NONE ->
            stringResource(R.string.device_timer_next_none)
        else -> timerNextTransitionSummary(channel, transitionAt)
    }
}

@Composable
private fun timerTemporaryOverrideSummary(channel: DeviceTimerChannelUiState): String {
    val remainingMinutes = (
        (channel.temporaryOverrideRemainingMillis + MILLIS_PER_MINUTE - 1L) /
            MILLIS_PER_MINUTE
        ).coerceAtLeast(1L).toInt()
    val duration = pluralStringResource(
        R.plurals.device_timer_duration_minutes,
        remainingMinutes,
        remainingMinutes
    )
    return if (channel.operatingState == DeviceTimerOperatingState.ON) {
        stringResource(R.string.device_timer_temporary_on, duration)
    } else {
        stringResource(R.string.device_timer_temporary_off, duration)
    }
}

@Composable
private fun timerNextTransitionSummary(
    channel: DeviceTimerChannelUiState,
    transitionAt: Long
): String {
    val time = LocaleFormatter.formatTime(LocalContext.current, transitionAt)
    return when (channel.nextTransitionType) {
        DeviceTimerNextTransitionType.ON -> stringResource(R.string.device_timer_next_on, time)
        DeviceTimerNextTransitionType.OFF -> stringResource(R.string.device_timer_next_off, time)
        DeviceTimerNextTransitionType.NONE -> stringResource(R.string.device_timer_next_none)
    }
}

@Composable
internal fun timerRegimeLabel(regime: DeviceTimerChannelRegime): String = when (regime) {
    DeviceTimerChannelRegime.AUTO -> stringResource(R.string.device_timer_mode_auto)
    DeviceTimerChannelRegime.ON -> stringResource(R.string.device_timer_mode_on)
    DeviceTimerChannelRegime.OFF -> stringResource(R.string.device_timer_mode_off)
}

@Composable
private fun timerOperatingStateLabel(state: DeviceTimerOperatingState): String = when (state) {
    DeviceTimerOperatingState.ON -> stringResource(R.string.device_timer_state_on)
    DeviceTimerOperatingState.OFF -> stringResource(R.string.device_timer_state_off)
}

private const val MILLIS_PER_MINUTE = 60_000L

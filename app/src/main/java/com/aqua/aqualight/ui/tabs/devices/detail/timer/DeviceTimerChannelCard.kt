package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

@Composable
@Suppress("LongParameterList")
internal fun DeviceTimerChannelCard(
    channel: DeviceTimerChannelUiState,
    interactionEnabled: Boolean,
    powerEnabled: Boolean,
    mutationPending: Boolean,
    onChannelClick: () -> Unit,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaTimerDashboardColors()
    val typography = aquaTimerDashboardTypography(colors)
    val stateLabel = timerOperatingStateLabel(channel.operatingState)
    val name = channel.effectiveName
    val description = stringResource(
        R.string.device_timer_channel_card_content_description,
        channel.channelNumber,
        name,
        stateLabel
    )
    val powerInteractionEnabled = powerEnabled && !mutationPending
    val detailsDescription = stringResource(
        R.string.device_timer_channel_details_description,
        name
    )

    AquaDeviceCardSurface(
        modifier = modifier
            .semantics { contentDescription = description }
            .alpha(
                if (interactionEnabled || mutationPending) 1f
                else AquaTimerInteractionStyle.disabledContentAlpha
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelSectionGap)
        ) {
            TimerChannelCardHeader(
                channel = channel,
                name = name,
                stateLabel = stateLabel,
                powerEnabled = powerInteractionEnabled,
                onPowerClick = onPowerClick,
                colors = colors,
                typography = typography
            )
            TimerDivider(colors)
            TimerChannelCardFooter(
                channel = channel,
                interactionEnabled = interactionEnabled,
                mutationPending = mutationPending,
                detailsDescription = detailsDescription,
                onChannelClick = onChannelClick,
                colors = colors,
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
}

@Composable
@Suppress("LongParameterList")
private fun TimerChannelCardFooter(
    channel: DeviceTimerChannelUiState,
    interactionEnabled: Boolean,
    mutationPending: Boolean,
    detailsDescription: String,
    onChannelClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerMetadataRow(
            text = timerRuntimeSummary(channel, mutationPending),
            tint = if (mutationPending) colors.accent else colors.secondaryText,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(AquaTimerDashboardGeometry.metadataActionSize)
                .clip(CircleShape)
                .clickable(
                    enabled = interactionEnabled && !mutationPending,
                    role = Role.Button,
                    onClick = onChannelClick
                )
                .semantics { contentDescription = detailsDescription },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(AquaTimerDashboardGeometry.metadataIconSize),
                colorFilter = ColorFilter.tint(colors.secondaryText)
            )
        }
    }
}

@Composable
internal fun TimerChannelProductIcon(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .size(AquaTimerDashboardGeometry.channelIconContainerSize)
            .clip(CircleShape)
            .background(colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.iconBackground))
            .border(AquaDeviceCardGeometry.outlineWidth, colors.mediaOutline, CircleShape),
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
@Suppress("LongParameterList")
internal fun TimerPowerButton(
    active: Boolean,
    programMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    modifier: Modifier = Modifier
) {
    val tint = if (active) colors.accent else colors.secondaryText
    val description = stringResource(
        when {
            programMode && active -> R.string.device_timer_program_power_turn_off_description
            programMode -> R.string.device_timer_program_power_turn_on_description
            active -> R.string.device_timer_manual_power_turn_off_description
            else -> R.string.device_timer_manual_power_turn_on_description
        }
    )
    Box(
        modifier = modifier
            .size(AquaTimerDashboardGeometry.channelPowerContainerSize)
            .clip(CircleShape)
            .background(tint.copy(alpha = AquaTimerDashboardAlpha.powerSurface))
            .border(AquaTimerDashboardGeometry.channelPowerGlowWidth, tint, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_timer_power),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.channelPowerIconSize),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

internal val DeviceTimerChannelUiState.effectiveName: String
    get() = displayName.ifBlank { defaultName }

@Composable
internal fun timerScheduleSummary(channel: DeviceTimerChannelUiState): String {
    val activeName = channel.activeScheduleName?.takeIf(String::isNotBlank)
    return when {
        channel.regime != DeviceTimerChannelRegime.AUTO ->
            stringResource(R.string.device_timer_work_mode_manual)
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
internal fun timerRuntimeSummary(
    channel: DeviceTimerChannelUiState,
    mutationPending: Boolean
): String {
    val transitionAt = channel.nextTransitionAtEpochMillis
    return when {
        mutationPending -> stringResource(R.string.device_timer_updating)
        channel.temporaryOverrideActive -> timerTemporaryOverrideSummary(channel)
        channel.regime != DeviceTimerChannelRegime.AUTO ->
            stringResource(R.string.device_timer_manual_programs_paused)
        transitionAt == null || channel.nextTransitionType == DeviceTimerNextTransitionType.NONE ->
            stringResource(R.string.device_timer_next_none)
        else -> timerNextTransitionSummary(channel, transitionAt)
    }
}

@Composable
private fun timerTemporaryOverrideSummary(channel: DeviceTimerChannelUiState): String {
    val reportedRemainingMillis = channel.temporaryOverrideRemainingMillis
    val remainingMillis by produceState(
        initialValue = reportedRemainingMillis.coerceAtLeast(0L),
        channel.temporaryOverrideActive,
        reportedRemainingMillis
    ) {
        if (!channel.temporaryOverrideActive) {
            value = 0L
            return@produceState
        }
        val startedAt = TimeSource.Monotonic.markNow()
        while (value > 0L) {
            delay(COUNTDOWN_TICK_MILLIS)
            value = (reportedRemainingMillis - startedAt.elapsedNow().inWholeMilliseconds)
                .coerceAtLeast(0L)
        }
    }
    val remainingMinutes = (
        (remainingMillis + MILLIS_PER_MINUTE - 1L) /
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
internal fun timerOperatingStateLabel(state: DeviceTimerOperatingState): String = when (state) {
    DeviceTimerOperatingState.ON -> stringResource(R.string.device_timer_state_on)
    DeviceTimerOperatingState.OFF -> stringResource(R.string.device_timer_state_off)
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val COUNTDOWN_TICK_MILLIS = 1_000L

package com.aqua.aqualight.ui.tabs.devices.detail.timer

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardTypography
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography

@Composable
internal fun DeviceTimerSummaryCard(
    control: DeviceTimerControlUiState,
    modifier: Modifier = Modifier
) {
    val colors = aquaTimerDashboardColors()
    val typography = aquaTimerDashboardTypography(colors)
    AquaDeviceCardSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                AquaTimerDashboardGeometry.summaryContentGap
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimerSummaryIcon(colors)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    AquaTimerDashboardGeometry.summaryTextGap
                )
            ) {
                BasicText(
                    text = stringResource(R.string.device_timer_dashboard_channels_title),
                    style = typography.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = pluralStringResource(
                        R.plurals.device_timer_dashboard_active_summary,
                        control.activeChannelCount,
                        control.channels.size
                    ),
                    style = typography.body.copy(
                        color = colors.primaryText,
                        fontSize = AquaTimerDashboardTypography.summaryValueSize
                    )
                )
                BasicText(
                    text = stringResource(R.string.device_timer_dashboard_independent_message),
                    style = typography.caption.copy(color = colors.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (control.readOnly) {
                    BasicText(
                        text = stringResource(R.string.device_timer_dashboard_read_only),
                        style = typography.micro.copy(color = colors.warning),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerSummaryIcon(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .size(AquaTimerDashboardGeometry.summaryIconContainerSize)
            .clip(CircleShape)
            .background(colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.iconBackground))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.mediaOutline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_device_timer),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.summaryIconSize),
            contentScale = ContentScale.Fit
        )
    }
}

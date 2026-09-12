package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle

@Composable
@Suppress("LongParameterList")
internal fun TimerChannelDetailRow(
    @DrawableRes iconRes: Int,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)
            .padding(AquaTimerDashboardGeometry.detailRowPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.detailRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.detailRowIconSize),
            colorFilter = ColorFilter.tint(colors.primaryText)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = value,
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.metadataIconSize),
            colorFilter = ColorFilter.tint(colors.secondaryText)
        )
    }
}

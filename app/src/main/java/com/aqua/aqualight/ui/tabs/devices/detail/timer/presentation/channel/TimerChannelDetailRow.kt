package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.channel

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
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.TimerCardStyle

@Composable
internal fun TimerChannelDetailRow(
    content: TimerChannelDetailContent,
    onClick: () -> Unit,
    style: TimerCardStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(
                style.colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground)
            )
            .clickable(enabled = content.enabled, role = Role.Button, onClick = onClick)
            .alpha(if (content.enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)
            .padding(AquaTimerDashboardGeometry.detailRowPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.detailRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(content.iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.detailRowIconSize),
            colorFilter = ColorFilter.tint(style.colors.primaryText)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = content.title,
                style = style.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = content.value,
                style = style.typography.caption.copy(color = style.colors.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.metadataIconSize),
            colorFilter = ColorFilter.tint(style.colors.secondaryText)
        )
    }
}

internal data class TimerChannelDetailContent(
    @DrawableRes val iconRes: Int,
    val title: String,
    val value: String,
    val enabled: Boolean
)

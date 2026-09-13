package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.TimerCardStyle

@Composable
internal fun TimerEditorButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    style: TimerCardStyle
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(
                style.colors.accent.copy(alpha = AquaTimerDashboardAlpha.actionBackground)
            )
            .border(
                AquaDeviceCardGeometry.outlineWidth,
                style.colors.accent,
                AquaTimerDashboardGeometry.actionShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.actionPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = style.typography.caption.copy(color = style.colors.accent)
        )
    }
}

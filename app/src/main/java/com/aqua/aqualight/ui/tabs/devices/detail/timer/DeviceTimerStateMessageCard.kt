package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography

@Composable
internal fun DeviceTimerStateMessageCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = aquaTimerDashboardColors()
    val typography = aquaTimerDashboardTypography(colors)
    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaTimerDashboardGeometry.messageMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.messageGap)
        ) {
            BasicText(text = title, style = typography.title)
            BasicText(
                text = message,
                style = typography.body.copy(color = colors.secondaryText)
            )
        }
    }
}

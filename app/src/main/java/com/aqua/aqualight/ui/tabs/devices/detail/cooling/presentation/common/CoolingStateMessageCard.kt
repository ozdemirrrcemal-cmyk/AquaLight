package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography

/** Shared presentation for typed Cooling read states. */
@Composable
internal fun CoolingStateMessageCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    AquaCoolingDashboardCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingHistoryGeometry.messageCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.messageGap)
        ) {
            BasicText(
                text = title,
                style = typography.title.copy(color = colors.primaryText)
            )
            BasicText(
                text = message,
                style = typography.body.copy(color = colors.secondaryText)
            )
            if (retryLabel != null && onRetry != null) {
                BasicText(
                    text = retryLabel,
                    style = typography.body.copy(color = colors.accent),
                    modifier = Modifier
                        .clip(AquaCoolingHistoryGeometry.retryShape)
                        .background(
                            colors.accent.copy(alpha = AquaCoolingHistoryAlpha.retryBackground)
                        )
                        .clickable(role = Role.Button, onClick = onRetry)
                        .padding(
                            horizontal = AquaCoolingHistoryGeometry.retryHorizontalPadding,
                            vertical = AquaCoolingHistoryGeometry.retryVerticalPadding
                        )
                )
            }
        }
    }
}

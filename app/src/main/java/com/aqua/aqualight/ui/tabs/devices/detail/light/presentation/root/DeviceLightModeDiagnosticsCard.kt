package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeDiagnostic
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardTypography

@Composable
internal fun DeviceLightModeDiagnosticsCard(
    diagnostic: DeviceLightModeDiagnostic,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    AquaDeviceCardSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.device_light_mode_diagnostics_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer {
                BasicText(
                    text = diagnostic.toDiagnosticText(),
                    style = typography.body.copy(
                        color = colors.secondaryText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                )
            }
        }
    }
}

internal fun DeviceLightModeDiagnostic.toDiagnosticText(): String = buildString {
    append("stage=")
    append(stage)
    append('\n')
    append("requestedMode=")
    append(requestedMode)
    details.forEach { detail ->
        append('\n')
        append(detail)
    }
}

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingError

@Composable
internal fun DeviceCoolingProgramAvailabilityScreen(
    loadState: DeviceCoolingProgramLoadState,
    commandFailure: DeviceCoolingCommandFailure?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (loadState == DeviceCoolingProgramLoadState.CONTENT) return

    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val content = commandFailure?.toProgramAvailabilityContent()
        ?: programAvailabilityContent(loadState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AquaCoolingProgramGeometry.screenHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        AquaCoolingDashboardCardSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.sectionGap)
            ) {
                BasicText(
                    text = stringResource(content.titleRes),
                    style = typography.title.copy(color = colors.primaryText)
                )
                BasicText(
                    text = stringResource(content.messageRes),
                    style = typography.body.copy(color = colors.secondaryText)
                )
                if (content.retryAvailable) {
                    BasicText(
                        text = stringResource(R.string.device_cooling_program_retry),
                        style = typography.body.copy(color = colors.accent),
                        modifier = Modifier
                            .clip(AquaCoolingProgramGeometry.inlineActionShape)
                            .clickable(role = Role.Button, onClick = onRetry)
                            .padding(
                                horizontal = AquaCoolingProgramGeometry.inlineActionHorizontalPadding,
                                vertical = AquaCoolingProgramGeometry.inlineActionVerticalPadding
                            )
                    )
                }
            }
        }
    }
}

private data class ProgramAvailabilityContent(
    val titleRes: Int,
    val messageRes: Int,
    val retryAvailable: Boolean
)

private fun DeviceCoolingCommandFailure.toProgramAvailabilityContent(): ProgramAvailabilityContent {
    val message = toCommercialCoolingError()
    return ProgramAvailabilityContent(
        titleRes = message.titleRes,
        messageRes = message.messageRes,
        retryAvailable = true
    )
}

private fun programAvailabilityContent(
    loadState: DeviceCoolingProgramLoadState
): ProgramAvailabilityContent = when (loadState) {
    DeviceCoolingProgramLoadState.IDLE,
    DeviceCoolingProgramLoadState.LOADING -> ProgramAvailabilityContent(
        titleRes = R.string.device_cooling_program_loading_title,
        messageRes = R.string.device_cooling_program_loading_message,
        retryAvailable = false
    )
    DeviceCoolingProgramLoadState.UNSUPPORTED -> ProgramAvailabilityContent(
        titleRes = R.string.device_cooling_program_unsupported_title,
        messageRes = R.string.device_cooling_program_unsupported_message,
        retryAvailable = false
    )
    DeviceCoolingProgramLoadState.UNAVAILABLE,
    DeviceCoolingProgramLoadState.NOT_CONNECTED -> ProgramAvailabilityContent(
        titleRes = R.string.device_cooling_program_unavailable_title,
        messageRes = R.string.device_cooling_program_unavailable_message,
        retryAvailable = true
    )
    DeviceCoolingProgramLoadState.ERROR -> ProgramAvailabilityContent(
        titleRes = R.string.device_cooling_program_load_failed_title,
        messageRes = R.string.device_cooling_program_load_failed_message,
        retryAvailable = true
    )
    DeviceCoolingProgramLoadState.CONTENT -> error("Content uses the Fan Program editor screen.")
}

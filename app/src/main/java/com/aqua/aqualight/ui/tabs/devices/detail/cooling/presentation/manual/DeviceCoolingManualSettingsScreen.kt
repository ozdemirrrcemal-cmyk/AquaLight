package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSlider
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSliderState
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingError

@Composable
internal fun DeviceCoolingManualSettingsScreen(
    state: DeviceCoolingManualSettingsUiState,
    onTargetPercentChanged: (Int) -> Unit,
    onTargetPercentChangeFinished: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            top = AquaCoolingDashboardGeometry.screenTopPadding,
            end = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            bottom = AquaCoolingDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.cardGap)
    ) {
        item(key = "manual-target") {
            val percent = state.targetPercent
            val capabilities = state.capabilities
            if (percent != null && capabilities != null) {
                AquaDeviceCardSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = AquaCoolingDashboardGeometry.compactCardMinimumHeight
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(
                            AquaDeviceCardGeometry.compactGap
                        )
                    ) {
                        BasicText(
                            text = stringResource(R.string.device_cooling_manual_target_title),
                            style = typography.title
                        )
                        BasicText(
                            text = stringResource(
                                R.string.device_cooling_percent_value_format,
                                percent
                            ),
                            style = typography.title.copy(
                                color = colors.primaryText,
                                fontSize = AquaCoolingDashboardTypography.gaugeValueSize
                            )
                        )
                        BasicText(
                            text = stringResource(
                                R.string.device_cooling_manual_target_description
                            ),
                            style = typography.caption.copy(color = colors.secondaryText)
                        )
                        val step = capabilities.stepPercent
                        if (step != null) {
                            AquaCoolingFanPercentSlider(
                                state = AquaCoolingFanPercentSliderState(
                                    percent = percent,
                                    enabled = state.canWrite,
                                    stepPercent = step,
                                    minimumPercent = capabilities.minimumPercent,
                                    maximumPercent = capabilities.maximumPercent
                                ),
                                colors = colors,
                                onValueChanged = onTargetPercentChanged,
                                onValueChangeFinished = onTargetPercentChangeFinished
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BasicText(
                                    text = stringResource(
                                        R.string.device_cooling_percent_value_format,
                                        capabilities.minimumPercent
                                    ),
                                    style = typography.micro.copy(
                                        color = colors.secondaryText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                BasicText(
                                    text = stringResource(
                                        R.string.device_cooling_percent_value_format,
                                        capabilities.maximumPercent
                                    ),
                                    style = typography.micro.copy(
                                        color = colors.secondaryText,
                                        textAlign = TextAlign.End
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        val supportMessage = state.manualSupportMessageRes()
                        if (supportMessage != null) {
                            BasicText(
                                text = stringResource(supportMessage),
                                style = typography.micro.copy(
                                    color = if (
                                        state.mutationState is CoolingMutationState.OperationError
                                    ) {
                                        colors.danger
                                    } else {
                                        colors.secondaryText
                                    }
                                )
                            )
                        }
                    }
                }
            } else {
                CoolingManualAvailabilityCard(state = state, onRetry = onRetry)
            }
        }
    }
}

@StringRes
private fun DeviceCoolingManualSettingsUiState.manualSupportMessageRes(): Int? = when (
    val mutation = mutationState
) {
    is CoolingMutationState.OperationError -> mutation.failure.toCommercialCoolingError().messageRes
    CoolingMutationState.Idle,
    CoolingMutationState.Saving,
    CoolingMutationState.Saved,
    CoolingMutationState.ValidationError -> when {
        !isManualMode -> R.string.device_cooling_error_manual_mode_required_message
        !canWrite && mutationState != CoolingMutationState.Saving ->
            R.string.device_cooling_manual_read_only
        else -> null
    }
}

@Composable
private fun CoolingManualAvailabilityCard(
    state: DeviceCoolingManualSettingsUiState,
    onRetry: () -> Unit
) {
    val resources = when (state.controlState) {
        CoolingDataState.Initial,
        CoolingDataState.Loading -> ManualStateResources(
            title = R.string.device_cooling_manual_loading_title,
            message = R.string.device_cooling_manual_loading_message,
            retry = false
        )
        CoolingDataState.Unsupported -> ManualStateResources(
            title = R.string.device_cooling_manual_unsupported_title,
            message = R.string.device_cooling_manual_unsupported_message,
            retry = false
        )
        CoolingDataState.Unavailable -> ManualStateResources(
            title = R.string.device_cooling_manual_unavailable_title,
            message = R.string.device_cooling_manual_unavailable_message,
            retry = true
        )
        is CoolingDataState.OperationError,
        is CoolingDataState.Content,
        is CoolingDataState.Empty -> ManualStateResources(
            title = R.string.device_cooling_manual_invalid_title,
            message = R.string.device_cooling_manual_invalid_message,
            retry = true
        )
    }
    CoolingStateMessageCard(
        title = stringResource(resources.title),
        message = stringResource(resources.message),
        retryLabel = if (resources.retry) {
            stringResource(R.string.device_cooling_state_retry)
        } else {
            null
        },
        onRetry = if (resources.retry) onRetry else null
    )
}

private data class ManualStateResources(
    @StringRes
    val title: Int,
    @StringRes
    val message: Int,
    val retry: Boolean
)

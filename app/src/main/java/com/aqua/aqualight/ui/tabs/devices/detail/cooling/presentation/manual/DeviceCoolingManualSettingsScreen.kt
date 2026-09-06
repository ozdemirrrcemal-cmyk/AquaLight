package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSlider
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSliderState
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPreview
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingError

@Composable
internal fun DeviceCoolingManualSettingsScreen(
    state: DeviceCoolingManualSettingsUiState,
    onTargetPercentChanged: (Int) -> Unit,
    onTargetPercentChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = state.targetPercent ?: return
    if (state.capabilities == null) return
    val colors = aquaCoolingDashboardColors()
    val visuals = ManualTargetVisuals(
        colors = colors,
        typography = aquaCoolingDashboardTypography(colors)
    )
    val actions = ManualTargetActions(
        onTargetPercentChanged = onTargetPercentChanged,
        onTargetPercentChangeFinished = onTargetPercentChangeFinished
    )
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
            CoolingManualTargetCard(
                state = state,
                percent = percent,
                visuals = visuals,
                actions = actions
            )
        }
    }
}

@Composable
private fun CoolingManualTargetCard(
    state: DeviceCoolingManualSettingsUiState,
    percent: Int,
    visuals: ManualTargetVisuals,
    actions: ManualTargetActions
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.compactCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingManualTargetCopy(percent = percent, visuals = visuals)
            CoolingManualFanPreview(percent = percent, visuals = visuals)
            CoolingManualTargetSlider(
                state = state,
                percent = percent,
                visuals = visuals,
                actions = actions
            )
            CoolingManualSupportMessage(state = state, visuals = visuals)
        }
    }
}

@Composable
private fun CoolingManualFanPreview(percent: Int, visuals: ManualTargetVisuals) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
    ) {
        AquaCoolingFanPreview(
            percent = percent,
            colors = visuals.colors,
            contentDescription = stringResource(
                R.string.device_cooling_manual_fan_preview_description,
                percent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaCoolingDashboardGeometry.manualFanPreviewHeight)
        )
        BasicText(
            text = stringResource(R.string.device_cooling_manual_fan_preview),
            style = visuals.typography.micro.copy(
                color = visuals.colors.secondaryText,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun CoolingManualTargetCopy(percent: Int, visuals: ManualTargetVisuals) {
    BasicText(
        text = stringResource(R.string.device_cooling_manual_target_title),
        style = visuals.typography.title
    )
    BasicText(
        text = stringResource(R.string.device_cooling_percent_value_format, percent),
        style = visuals.typography.title.copy(
            color = visuals.colors.primaryText,
            fontSize = AquaCoolingDashboardTypography.gaugeValueSize
        )
    )
    BasicText(
        text = stringResource(R.string.device_cooling_manual_target_description),
        style = visuals.typography.caption.copy(color = visuals.colors.secondaryText)
    )
}

@Composable
private fun CoolingManualTargetSlider(
    state: DeviceCoolingManualSettingsUiState,
    percent: Int,
    visuals: ManualTargetVisuals,
    actions: ManualTargetActions
) {
    val capabilities = state.capabilities ?: return
    val step = capabilities.stepPercent ?: return
    AquaCoolingFanPercentSlider(
        state = AquaCoolingFanPercentSliderState(
            percent = percent,
            enabled = state.canWrite,
            stepPercent = step,
            minimumPercent = capabilities.minimumPercent,
            maximumPercent = capabilities.maximumPercent
        ),
        colors = visuals.colors,
        onValueChanged = actions.onTargetPercentChanged,
        onValueChangeFinished = actions.onTargetPercentChangeFinished
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(
                R.string.device_cooling_percent_value_format,
                capabilities.minimumPercent
            ),
            style = visuals.typography.micro.copy(color = visuals.colors.secondaryText),
            modifier = Modifier.weight(1f)
        )
        BasicText(
            text = stringResource(
                R.string.device_cooling_percent_value_format,
                capabilities.maximumPercent
            ),
            style = visuals.typography.micro.copy(
                color = visuals.colors.secondaryText,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CoolingManualSupportMessage(
    state: DeviceCoolingManualSettingsUiState,
    visuals: ManualTargetVisuals
) {
    val supportMessage = state.manualSupportMessageRes() ?: return
    val textColor = if (state.mutationState is CoolingMutationState.OperationError) {
        visuals.colors.danger
    } else {
        visuals.colors.secondaryText
    }
    BasicText(
        text = stringResource(supportMessage),
        style = visuals.typography.micro.copy(color = textColor)
    )
}

private data class ManualTargetVisuals(
    val colors: AquaDeviceCardColors,
    val typography: AquaDeviceCardTypography
)

private data class ManualTargetActions(
    val onTargetPercentChanged: (Int) -> Unit,
    val onTargetPercentChangeFinished: () -> Unit
)

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

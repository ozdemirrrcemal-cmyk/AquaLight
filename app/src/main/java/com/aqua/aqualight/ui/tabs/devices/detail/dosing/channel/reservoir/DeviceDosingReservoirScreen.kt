package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSection
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuToggle
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuValueRow
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton

@Immutable
internal data class DeviceDosingReservoirUiState(
    val trackingEnabled: Boolean,
    val capacityValue: String,
    val remainingValue: String,
    val accountingCertain: Boolean,
    val saveEnabled: Boolean,
    val refillAvailable: Boolean,
    val busy: Boolean
)

internal data class DeviceDosingReservoirActions(
    val onTrackingEnabledChange: (Boolean) -> Unit,
    val onCapacityClick: () -> Unit,
    val onRefillClick: (() -> Unit)?,
    val onSaveClick: (() -> Unit)?
)

/** Reservoir editor mirrors only the firmware-owned capacity, remaining and accounting state. */
@Composable
internal fun DeviceDosingReservoirScreen(
    state: DeviceDosingReservoirUiState,
    actions: DeviceDosingReservoirActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = AquaDeviceMenuGeometry.screenHorizontalPadding,
            top = AquaDeviceMenuGeometry.screenTopPadding,
            end = AquaDeviceMenuGeometry.screenHorizontalPadding,
            bottom = AquaDeviceMenuGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
    ) {
        item(key = RESERVOIR_TRACKING_KEY) {
            ReservoirTrackingSection(state, actions)
        }
        item(key = RESERVOIR_VOLUME_KEY) {
            ReservoirVolumeSection(state, actions.onCapacityClick)
        }
        if (state.trackingEnabled) {
            item(key = RESERVOIR_ACCOUNTING_KEY) {
                ReservoirAccountingSection(state)
            }
        }
        if (state.refillAvailable) {
            item(key = RESERVOIR_REFILL_KEY) {
                AquaGuidedFlowButton(
                    text = stringResource(R.string.device_dosing_reservoir_refill_action),
                    onClick = { actions.onRefillClick?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy && actions.onRefillClick != null
                )
            }
        }
        item(key = RESERVOIR_SAVE_KEY) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_dosing_detail_save_reservoir),
                onClick = { actions.onSaveClick?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.saveEnabled && !state.busy && actions.onSaveClick != null
            )
        }
    }
}

@Composable
private fun ReservoirTrackingSection(
    state: DeviceDosingReservoirUiState,
    actions: DeviceDosingReservoirActions
) {
    ReservoirSection(R.string.device_dosing_detail_reservoir_tracking_section) {
        ReservoirToggleRow(
            titleRes = R.string.device_dosing_detail_reservoir_tracking_enabled,
            descriptionRes = R.string.device_dosing_detail_reservoir_tracking_enabled_description,
            checked = state.trackingEnabled,
            enabled = !state.busy,
            onCheckedChange = actions.onTrackingEnabledChange
        )
    }
}

@Composable
private fun ReservoirVolumeSection(
    state: DeviceDosingReservoirUiState,
    onCapacityClick: () -> Unit
) {
    ReservoirSection(
        titleRes = R.string.device_dosing_detail_reservoir_volume_section,
        enabled = state.trackingEnabled
    ) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_container_volume),
            value = state.capacityValue,
            modifier = Modifier.clickable(
                enabled = state.trackingEnabled && !state.busy,
                role = Role.Button,
                onClick = onCapacityClick
            ),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider()
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_available_volume),
            value = state.remainingValue,
            tone = if (state.accountingCertain) {
                AquaDeviceMenuTone.NEUTRAL
            } else {
                AquaDeviceMenuTone.DANGER
            }
        )
    }
}

@Composable
private fun ReservoirAccountingSection(state: DeviceDosingReservoirUiState) {
    ReservoirSection(R.string.device_dosing_reservoir_accounting_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_reservoir_accounting_state),
            value = stringResource(
                if (state.accountingCertain) {
                    R.string.device_dosing_reservoir_accounting_certain
                } else {
                    R.string.device_dosing_reservoir_accounting_uncertain
                }
            ),
            tone = if (state.accountingCertain) {
                AquaDeviceMenuTone.NEUTRAL
            } else {
                AquaDeviceMenuTone.DANGER
            }
        )
    }
}

@Composable
private fun ReservoirSection(
    @StringRes titleRes: Int,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AquaDeviceMenuSection(
        title = stringResource(titleRes),
        enabled = enabled,
        content = content
    )
}

@Composable
private fun ReservoirToggleRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val stateLabel = stringResource(
        if (checked) R.string.device_dosing_detail_state_on else R.string.device_dosing_detail_state_off
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(AquaDeviceMenuGeometry.sectionContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AquaDeviceMenuGeometry.compactGap)
        ) {
            BasicText(text = stringResource(titleRes), style = typography.rowTitle)
            BasicText(
                text = stringResource(descriptionRes),
                modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                style = typography.rowBody
            )
        }
        AquaDeviceMenuToggle(
            checked = checked,
            contentDescription = stringResource(
                R.string.device_dosing_detail_toggle_description,
                stateLabel
            )
        )
    }
}

private const val RESERVOIR_TRACKING_KEY = "dosing-reservoir-tracking"
private const val RESERVOIR_VOLUME_KEY = "dosing-reservoir-volume"
private const val RESERVOIR_ACCOUNTING_KEY = "dosing-reservoir-accounting"
private const val RESERVOIR_REFILL_KEY = "dosing-reservoir-refill"
private const val RESERVOIR_SAVE_KEY = "dosing-reservoir-save"

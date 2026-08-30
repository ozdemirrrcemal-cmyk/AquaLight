package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityRejection
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
    val capacityRejection: DeviceDosingReservoirCapacityRejection?,
    val lowLevelAlertEnabled: Boolean,
    val reservoirNeedsAttention: Boolean,
    val editorEnabled: Boolean,
    val canSave: Boolean,
    val canRefill: Boolean,
    val lowLevelAlertNotificationAvailability: DeviceDosingReservoirNotificationAvailability
)

internal data class DeviceDosingReservoirActions(
    val onTrackingEnabledChange: (Boolean) -> Unit,
    val onCapacityClick: () -> Unit,
    val onRefillClick: () -> Unit,
    val onLowLevelAlertEnabledChange: (Boolean) -> Unit,
    val onRepairLowLevelAlertNotifications: () -> Unit,
    val onSaveClick: () -> Unit
)

private data class ReservoirSupportingAction(
    val text: String,
    val onClick: () -> Unit
)

private data class ReservoirToggleContent(
    @StringRes val titleRes: Int,
    val description: String
)

private data class ReservoirAlertPresentation(
    @StringRes val descriptionRes: Int,
    @StringRes val actionRes: Int?
)

/** Reservoir Monitoring child feature rendered only from the central application snapshot/draft. */
@Composable
internal fun DeviceDosingReservoirScreen(
    state: DeviceDosingReservoirUiState,
    actions: DeviceDosingReservoirActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(
            start = AquaDeviceMenuGeometry.screenHorizontalPadding,
            top = AquaDeviceMenuGeometry.screenTopPadding,
            end = AquaDeviceMenuGeometry.screenHorizontalPadding,
            bottom = AquaDeviceMenuGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
    ) {
        item(key = RESERVOIR_TRACKING_KEY) { ReservoirTrackingSection(state, actions) }
        item(key = RESERVOIR_VOLUME_KEY) { ReservoirVolumeSection(state, actions) }
        item(key = RESERVOIR_ALERTS_KEY) { ReservoirAlertsSection(state, actions) }
        item(key = RESERVOIR_SAVE_KEY) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_dosing_detail_save_reservoir),
                onClick = actions.onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave
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
            content = ReservoirToggleContent(
                titleRes = R.string.device_dosing_detail_reservoir_tracking_enabled,
                description = stringResource(
                    R.string.device_dosing_detail_reservoir_tracking_enabled_description
                )
            ),
            checked = state.trackingEnabled,
            enabled = state.editorEnabled,
            onCheckedChange = actions.onTrackingEnabledChange
        )
    }
}

@Composable
private fun ReservoirVolumeSection(
    state: DeviceDosingReservoirUiState,
    actions: DeviceDosingReservoirActions
) {
    ReservoirSection(
        titleRes = R.string.device_dosing_detail_reservoir_volume_section,
        enabled = state.trackingEnabled
    ) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_container_volume),
            value = state.capacityValue,
            description = state.capacityRejection?.let { rejection ->
                stringResource(rejection.messageRes)
            },
            modifier = Modifier.clickable(
                enabled = state.trackingEnabled && state.editorEnabled,
                role = Role.Button,
                onClick = actions.onCapacityClick
            ),
            tone = if (state.capacityRejection == null) {
                AquaDeviceMenuTone.ACCENT
            } else {
                AquaDeviceMenuTone.DANGER
            }
        )
        AquaDeviceMenuDivider()
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_available_volume),
            value = state.remainingValue,
            tone = if (state.reservoirNeedsAttention) {
                AquaDeviceMenuTone.DANGER
            } else {
                AquaDeviceMenuTone.NEUTRAL
            }
        )
        AquaDeviceMenuDivider()
        AquaGuidedFlowButton(
            text = stringResource(R.string.device_dosing_reservoir_refill),
            onClick = actions.onRefillClick,
            modifier = Modifier.fillMaxWidth().padding(AquaDeviceMenuGeometry.sectionContentPadding),
            enabled = state.canRefill
        )
    }
}

private val DeviceDosingReservoirCapacityRejection.messageRes: Int
    @StringRes get() = when (this) {
        DeviceDosingReservoirCapacityRejection.REQUIRED ->
            R.string.device_dosing_detail_container_volume_required
        DeviceDosingReservoirCapacityRejection.INVALID_NUMBER ->
            R.string.device_dosing_detail_container_volume_invalid
        DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED ->
            R.string.device_dosing_detail_container_volume_positive
        DeviceDosingReservoirCapacityRejection.UNSUPPORTED_PRECISION ->
            R.string.device_dosing_detail_container_volume_precision
        DeviceDosingReservoirCapacityRejection.OUT_OF_RANGE ->
            R.string.device_dosing_detail_container_volume_range
    }

@Composable
private fun ReservoirAlertsSection(
    state: DeviceDosingReservoirUiState,
    actions: DeviceDosingReservoirActions
) {
    val presentation = state.alertPresentation()
    ReservoirSection(
        titleRes = R.string.device_dosing_detail_reservoir_alerts_section,
        enabled = state.trackingEnabled
    ) {
        ReservoirToggleRow(
            content = ReservoirToggleContent(
                titleRes = R.string.device_dosing_detail_low_level_alert,
                description = stringResource(presentation.descriptionRes)
            ),
            checked = state.lowLevelAlertEnabled,
            enabled = state.trackingEnabled && state.editorEnabled,
            onCheckedChange = actions.onLowLevelAlertEnabledChange,
            supportingAction = presentation.actionRes?.let { actionRes ->
                ReservoirSupportingAction(
                    text = stringResource(actionRes),
                    onClick = actions.onRepairLowLevelAlertNotifications
                )
            }
        )
    }
}

private fun DeviceDosingReservoirUiState.alertPresentation(): ReservoirAlertPresentation {
    val notificationBlocked = trackingEnabled && lowLevelAlertEnabled &&
        lowLevelAlertNotificationAvailability != DeviceDosingReservoirNotificationAvailability.AVAILABLE
    val ownerPreferenceDisabled = notificationBlocked &&
        lowLevelAlertNotificationAvailability ==
        DeviceDosingReservoirNotificationAvailability.OWNER_PREFERENCE_DISABLED
    return ReservoirAlertPresentation(
        descriptionRes = when {
            !trackingEnabled -> R.string.device_dosing_detail_low_level_alert_tracking_disabled_description
            ownerPreferenceDisabled -> R.string.notification_feature_owner_preference_disabled
            notificationBlocked -> R.string.notification_feature_enabled_android_blocked
            else -> R.string.device_dosing_detail_low_level_alert_description
        },
        actionRes = when {
            ownerPreferenceDisabled -> R.string.notification_feature_enable_action
            notificationBlocked -> R.string.notification_feature_open_settings_action
            else -> null
        }
    )
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
    content: ReservoirToggleContent,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingAction: ReservoirSupportingAction? = null
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
            modifier = Modifier.weight(1f).padding(end = AquaDeviceMenuGeometry.compactGap)
        ) {
            BasicText(text = stringResource(content.titleRes), style = typography.rowTitle)
            BasicText(
                text = content.description,
                modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                style = typography.rowBody
            )
            supportingAction?.let { action ->
                Box(
                    modifier = Modifier
                        .padding(top = AquaDeviceMenuGeometry.rowTextGap)
                        .defaultMinSize(minHeight = AquaDeviceMenuGeometry.inlineActionMinHeight)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = action.onClick
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicText(
                        text = action.text,
                        style = typography.rowTitle.copy(color = colors.accent)
                    )
                }
            }
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
private const val RESERVOIR_ALERTS_KEY = "dosing-reservoir-alerts"
private const val RESERVOIR_SAVE_KEY = "dosing-reservoir-save"

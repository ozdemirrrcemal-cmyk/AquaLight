package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuActionRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuHeroCard
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowAction
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowContent
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSectionSurface
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSwitchRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography

@Immutable
internal data class DeviceDosingChannelDetailUiState(
    val lastCalibrationDate: String,
    val missedDoseRecoveryEnabled: Boolean
)

internal data class DeviceDosingChannelDetailActions(
    val onMenuItemClick: (DosingDetailMenuItem) -> Unit,
    val onRecalibrateClick: () -> Unit,
    val onMissedDoseRecoveryChange: (Boolean) -> Unit,
    val onManualDoseClick: () -> Unit,
    val onResetChannelClick: () -> Unit
)

/** Channel-level control center. Child features own their own destinations and state. */
@Composable
internal fun DeviceDosingChannelDetailScreen(
    state: DeviceDosingChannelDetailUiState,
    actions: DeviceDosingChannelDetailActions,
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
        item(key = DETAIL_HERO_KEY) {
            DosingDetailHero()
        }
        items(
            items = DOSING_DETAIL_MENU_SECTIONS,
            key = DosingDetailMenuSection::titleRes
        ) { section ->
            DosingDetailSection(section = section, state = state, actions = actions)
        }
    }
}

@Composable
private fun DosingDetailHero() {
    AquaDeviceMenuHeroCard(
        eyebrow = stringResource(R.string.device_dosing_detail_hero_eyebrow),
        title = stringResource(R.string.device_dosing_detail_hero_title),
        description = stringResource(R.string.device_dosing_detail_hero_description)
    )
}

@Composable
private fun DosingDetailSection(
    section: DosingDetailMenuSection,
    state: DeviceDosingChannelDetailUiState,
    actions: DeviceDosingChannelDetailActions
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(section.titleRes),
            modifier = Modifier.padding(
                start = AquaDeviceMenuGeometry.rowHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.sectionLabelBottomSpacing
            ),
            style = typography.sectionLabel
        )
        AquaDeviceMenuSectionSurface(modifier = Modifier.fillMaxWidth()) {
            if (section.hasCalibrationAction) {
                DosingCalibrationAction(
                    lastCalibrationDate = state.lastCalibrationDate,
                    onClick = actions.onRecalibrateClick
                )
                if (section.items.isNotEmpty()) AquaDeviceMenuDivider()
            }
            section.items.forEachIndexed { index, item ->
                if (index > 0) AquaDeviceMenuDivider()
                AquaDeviceMenuRow(
                    content = AquaDeviceMenuRowContent(
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descriptionRes),
                        iconRes = item.iconRes,
                        tone = item.tone
                    ),
                    onClick = { actions.onMenuItemClick(item) }
                )
            }
            if (section.hasMissedDoseRecoverySwitch) {
                DosingMissedDoseRecoverySwitch(
                    checked = state.missedDoseRecoveryEnabled,
                    onCheckedChange = actions.onMissedDoseRecoveryChange
                )
            }
            if (section.hasManualDoseAction) {
                if (section.items.isNotEmpty()) AquaDeviceMenuDivider()
                DosingManualDoseAction(onClick = actions.onManualDoseClick)
            }
            if (section.hasResetChannelAction) {
                if (section.items.isNotEmpty() || section.hasManualDoseAction) {
                    AquaDeviceMenuDivider()
                }
                DosingResetChannelAction(onClick = actions.onResetChannelClick)
            }
        }
    }
}

@Composable
private fun DosingCalibrationAction(
    lastCalibrationDate: String,
    onClick: () -> Unit
) {
    AquaDeviceMenuActionRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_calibration_title),
            description = stringResource(
                R.string.device_dosing_detail_last_calibrated_compact,
                lastCalibrationDate
            ),
            iconRes = R.drawable.ic_dosing_calibration_24
        ),
        action = AquaDeviceMenuRowAction(
            text = stringResource(R.string.device_dosing_detail_recalibrate),
            onClick = onClick
        )
    )
}

@Composable
private fun DosingMissedDoseRecoverySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateLabel = stringResource(
        if (checked) R.string.device_dosing_detail_state_on else R.string.device_dosing_detail_state_off
    )

    AquaDeviceMenuDivider()
    AquaDeviceMenuSwitchRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_missed_dose_title),
            description = stringResource(R.string.device_dosing_detail_missed_dose_description),
            iconRes = R.drawable.ic_dosing_recovery_24
        ),
        checked = checked,
        toggleContentDescription = stringResource(
            R.string.device_dosing_detail_missed_dose_toggle_description,
            stateLabel
        ),
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun DosingManualDoseAction(onClick: () -> Unit) {
    AquaDeviceMenuRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_manual_title),
            description = stringResource(R.string.device_dosing_detail_manual_description),
            iconRes = R.drawable.ic_dosing_manual_24
        ),
        onClick = onClick,
        showTrailingIcon = false
    )
}

@Composable
private fun DosingResetChannelAction(onClick: () -> Unit) {
    AquaDeviceMenuRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_reset_title),
            description = stringResource(R.string.device_dosing_detail_reset_description),
            iconRes = R.drawable.ic_dosing_reset_24,
            tone = AquaDeviceMenuTone.DANGER
        ),
        onClick = onClick,
        showTrailingIcon = false
    )
}

private const val DETAIL_HERO_KEY = "dosing-detail-hero"

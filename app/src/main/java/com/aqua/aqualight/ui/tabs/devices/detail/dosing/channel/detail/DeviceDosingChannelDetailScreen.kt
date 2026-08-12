@file:Suppress("LongParameterList")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuActionRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuHeroCard
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowContent
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSectionSurface
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSwitchRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography

/** Channel control shell with child destinations and direct actions. */
@Composable
internal fun DeviceDosingChannelDetailScreen(
    lastCalibrationDate: String,
    modifier: Modifier = Modifier,
    onMenuItemClick: ((DosingDetailMenuItem) -> Unit)? = null,
    onRecalibrateClick: (() -> Unit)? = null,
    onManualDoseClick: (() -> Unit)? = null,
    onResetChannelClick: (() -> Unit)? = null
) {
    val colors = aquaDeviceMenuColors()
    var missedDoseRecoveryEnabled by rememberSaveable { mutableStateOf(false) }
    val directActions = DosingDetailDirectActions(
        onRecalibrateClick = onRecalibrateClick,
        onManualDoseClick = onManualDoseClick,
        onResetChannelClick = onResetChannelClick
    )

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
            DosingDetailSection(
                section = section,
                onMenuItemClick = onMenuItemClick,
                directActions = directActions,
                lastCalibrationDate = lastCalibrationDate,
                missedDoseRecoveryEnabled = missedDoseRecoveryEnabled,
                onMissedDoseRecoveryChange = { enabled ->
                    missedDoseRecoveryEnabled = enabled
                }
            )
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
    onMenuItemClick: ((DosingDetailMenuItem) -> Unit)?,
    directActions: DosingDetailDirectActions,
    lastCalibrationDate: String,
    missedDoseRecoveryEnabled: Boolean,
    onMissedDoseRecoveryChange: (Boolean) -> Unit
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
                    lastCalibrationDate = lastCalibrationDate,
                    onClick = directActions.onRecalibrateClick
                )
                if (section.items.isNotEmpty()) {
                    AquaDeviceMenuDivider()
                }
            }
            section.items.forEachIndexed { index, item ->
                if (index > 0) {
                    AquaDeviceMenuDivider()
                }
                AquaDeviceMenuRow(
                    content = AquaDeviceMenuRowContent(
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descriptionRes),
                        iconRes = item.iconRes,
                        tone = item.tone
                    ),
                    onClick = onMenuItemClick?.let { callback ->
                        { callback(item) }
                    }
                )
            }
            if (section.hasMissedDoseRecoverySwitch) {
                DosingMissedDoseRecoverySwitch(
                    checked = missedDoseRecoveryEnabled,
                    onCheckedChange = onMissedDoseRecoveryChange
                )
            }
            if (section.hasManualDoseAction) {
                if (section.items.isNotEmpty()) {
                    AquaDeviceMenuDivider()
                }
                DosingManualDoseAction(onClick = directActions.onManualDoseClick)
            }
            if (section.hasResetChannelAction) {
                if (section.items.isNotEmpty() || section.hasManualDoseAction) {
                    AquaDeviceMenuDivider()
                }
                DosingResetChannelAction(onClick = directActions.onResetChannelClick)
            }
        }
    }
}

@Composable
private fun DosingCalibrationAction(
    lastCalibrationDate: String,
    onClick: (() -> Unit)?
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
        actionText = stringResource(R.string.device_dosing_detail_recalibrate),
        onActionClick = { onClick?.invoke() },
        actionEnabled = onClick != null
    )
}

@Composable
private fun DosingMissedDoseRecoverySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateLabel = stringResource(
        if (checked) {
            R.string.device_dosing_detail_state_on
        } else {
            R.string.device_dosing_detail_state_off
        }
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
private fun DosingManualDoseAction(onClick: (() -> Unit)?) {
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
private fun DosingResetChannelAction(onClick: (() -> Unit)?) {
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

private data class DosingDetailDirectActions(
    val onRecalibrateClick: (() -> Unit)?,
    val onManualDoseClick: (() -> Unit)?,
    val onResetChannelClick: (() -> Unit)?
)

internal data class DosingDetailMenuSection(
    @StringRes val titleRes: Int,
    val items: List<DosingDetailMenuItem>,
    val hasCalibrationAction: Boolean = false,
    val hasMissedDoseRecoverySwitch: Boolean = false,
    val hasManualDoseAction: Boolean = false,
    val hasResetChannelAction: Boolean = false
)

internal enum class DosingDetailMenuItem(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val tone: AquaDeviceMenuTone = AquaDeviceMenuTone.ACCENT
) {
    DOSING_PLAN(
        routeKey = "dosing-plan",
        titleRes = R.string.device_dosing_detail_plan_title,
        descriptionRes = R.string.device_dosing_detail_plan_description,
        iconRes = R.drawable.ic_dosing_schedule_24
    ),
    RESERVOIR(
        routeKey = "reservoir",
        titleRes = R.string.device_dosing_detail_reservoir_title,
        descriptionRes = R.string.device_dosing_detail_reservoir_description,
        iconRes = R.drawable.ic_care_fertilizer_24
    );

    companion object {
        private val byRouteKey = entries.associateBy(DosingDetailMenuItem::routeKey)

        fun fromRouteKey(routeKey: String): DosingDetailMenuItem? =
            byRouteKey[routeKey.trim()]
    }
}

internal val DOSING_DETAIL_MENU_SECTIONS = listOf(
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_planning_section,
        items = listOf(DosingDetailMenuItem.DOSING_PLAN),
        hasMissedDoseRecoverySwitch = true
    ),
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_accuracy_section,
        items = listOf(DosingDetailMenuItem.RESERVOIR),
        hasCalibrationAction = true
    ),
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_control_section,
        items = emptyList(),
        hasManualDoseAction = true,
        hasResetChannelAction = true
    )
)

private const val DETAIL_HERO_KEY = "dosing-detail-hero"

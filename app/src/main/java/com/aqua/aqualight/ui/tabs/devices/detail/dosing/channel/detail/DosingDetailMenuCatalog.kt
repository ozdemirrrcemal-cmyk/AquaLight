package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone

internal enum class DosingDetailMenuItem(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val tone: AquaDeviceMenuTone = AquaDeviceMenuTone.ACCENT
) {
    DOSING_PLAN(
        titleRes = R.string.device_dosing_detail_plan_title,
        descriptionRes = R.string.device_dosing_detail_plan_description,
        iconRes = R.drawable.ic_dosing_schedule_24
    ),
    RESERVOIR(
        titleRes = R.string.device_dosing_detail_reservoir_title,
        descriptionRes = R.string.device_dosing_detail_reservoir_description,
        iconRes = R.drawable.ic_care_fertilizer_24
    )
}

internal data class DosingDetailMenuSection(
    @StringRes val titleRes: Int,
    val items: List<DosingDetailMenuItem>,
    val hasCalibrationAction: Boolean = false,
    val hasMissedDoseRecoverySwitch: Boolean = false,
    val hasManualDoseAction: Boolean = false,
    val hasResetChannelAction: Boolean = false
)

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

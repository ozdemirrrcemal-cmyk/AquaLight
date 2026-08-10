@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuChoiceChip
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSectionSurface
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuToggle
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuValueRow
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton

/** Static child-screen catalog; it deliberately performs no device or persistence work. */
@Composable
internal fun DeviceDosingChannelMenuScreen(
    item: DosingDetailMenuItem,
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
        item(key = item.routeKey) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
            ) {
                when (item) {
                    DosingDetailMenuItem.DOSING_PLAN -> DosingPlanPreview()
                    DosingDetailMenuItem.CALIBRATION -> CalibrationPreview()
                    DosingDetailMenuItem.RESERVOIR -> ReservoirPreview()
                    DosingDetailMenuItem.MANUAL_DOSE -> ManualDosePreview()
                    DosingDetailMenuItem.RESET_CHANNEL -> ResetChannelPreview()
                }
            }
        }
    }
}

@Composable
private fun DosingPlanPreview() {
    PreviewSection(R.string.device_dosing_detail_schedule_status_section) {
        PreviewToggleRow(
            titleRes = R.string.device_dosing_detail_activate_schedule,
            descriptionRes = R.string.device_dosing_detail_activate_schedule_description,
            checked = true
        )
    }
    PreviewSection(R.string.device_dosing_detail_amount_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_daily_dose),
            value = stringResource(R.string.device_dosing_detail_value_zero_ml),
            description = stringResource(R.string.device_dosing_detail_daily_dose_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
    }
    PreviewSection(R.string.device_dosing_detail_schedule_section) {
        PreviewChoiceGroup(
            labelRes = R.string.device_dosing_detail_schedule_mode,
            choices = listOf(
                PreviewChoice(R.string.device_dosing_detail_schedule_single, selected = true),
                PreviewChoice(R.string.device_dosing_detail_schedule_hourly),
                PreviewChoice(R.string.device_dosing_detail_schedule_custom)
            )
        )
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_timer),
            value = stringResource(R.string.device_dosing_detail_value_unset_time)
        )
    }
    PreviewSection(R.string.device_dosing_detail_recurrence_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_recurrence),
            value = stringResource(R.string.device_dosing_channel_every_day),
            tone = AquaDeviceMenuTone.ACCENT
        )
        PreviewWeekdays()
    }
    PreviewAction(R.string.device_dosing_detail_save_plan)
}

@Composable
private fun CalibrationPreview() {
    PreviewSection(R.string.device_dosing_detail_calibration_status_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_last_calibrated),
            value = stringResource(R.string.device_dosing_detail_not_calibrated),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_flow_accuracy),
            value = stringResource(R.string.device_dosing_detail_value_unavailable)
        )
    }
    PreviewSection(R.string.device_dosing_detail_calibration_flow_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_guided_calibration),
            value = stringResource(R.string.device_dosing_detail_six_steps),
            description = stringResource(R.string.device_dosing_detail_guided_calibration_description)
        )
    }
    PreviewAction(R.string.device_dosing_detail_start_recalibration)
}

@Composable
private fun ReservoirPreview() {
    PreviewSection(R.string.device_dosing_detail_reservoir_volume_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_container_volume),
            value = stringResource(R.string.device_dosing_detail_value_container_ml),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_available_volume),
            value = stringResource(R.string.device_dosing_detail_value_container_ml)
        )
    }
    PreviewSection(R.string.device_dosing_detail_reservoir_alerts_section) {
        PreviewToggleRow(
            titleRes = R.string.device_dosing_detail_low_level_alert,
            descriptionRes = R.string.device_dosing_detail_low_level_alert_description,
            checked = true
        )
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_alert_threshold),
            value = stringResource(R.string.device_dosing_detail_value_twenty_percent),
            tone = AquaDeviceMenuTone.ACCENT
        )
    }
    PreviewAction(R.string.device_dosing_detail_save_reservoir)
}

@Composable
private fun ManualDosePreview() {
    PreviewSection(R.string.device_dosing_detail_manual_amount_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_manual_amount),
            value = stringResource(R.string.device_dosing_detail_value_zero_ml),
            description = stringResource(R.string.device_dosing_detail_manual_amount_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_single_dose_limit),
            value = stringResource(R.string.device_dosing_detail_value_ten_ml)
        )
    }
    PreviewSection(R.string.device_dosing_detail_manual_safety_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_delivery_mode),
            value = stringResource(R.string.device_dosing_detail_one_time_dose),
            description = stringResource(R.string.device_dosing_detail_delivery_mode_description)
        )
    }
    PreviewAction(R.string.device_dosing_detail_dispense_dose)
}

@Composable
private fun ResetChannelPreview() {
    ResetWarning()
    PreviewAction(R.string.device_dosing_detail_reset_action)
}

@Composable
private fun ResetWarning() {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    AquaDeviceMenuSectionSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AquaDeviceMenuGeometry.sectionContentPadding),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_dosing_detail_reset_warning_title),
                style = typography.heroTitle.copy(color = colors.danger)
            )
            BasicText(
                text = stringResource(R.string.device_dosing_detail_reset_warning_description),
                style = typography.heroBody
            )
        }
    }
}

@Composable
private fun PreviewSection(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(titleRes),
            modifier = Modifier.padding(
                start = AquaDeviceMenuGeometry.rowHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.sectionLabelBottomSpacing
            ),
            style = typography.sectionLabel
        )
        AquaDeviceMenuSectionSurface(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun PreviewToggleRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val state = stringResource(
        if (checked) {
            R.string.device_dosing_detail_state_on
        } else {
            R.string.device_dosing_detail_state_off
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                state
            )
        )
    }
}

@Composable
private fun PreviewChoiceGroup(
    @StringRes labelRes: Int,
    choices: List<PreviewChoice>
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AquaDeviceMenuGeometry.sectionContentPadding),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
    ) {
        BasicText(text = stringResource(labelRes), style = typography.rowTitle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
        ) {
            choices.forEach { choice ->
                AquaDeviceMenuChoiceChip(
                    text = stringResource(choice.labelRes),
                    selected = choice.selected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreviewWeekdays() {
    val weekdayRows = listOf(
        listOf(
            R.string.device_dosing_weekday_mon,
            R.string.device_dosing_weekday_tue,
            R.string.device_dosing_weekday_wed,
            R.string.device_dosing_weekday_thu
        ),
        listOf(
            R.string.device_dosing_weekday_fri,
            R.string.device_dosing_weekday_sat,
            R.string.device_dosing_weekday_sun
        )
    )
    Column(
        modifier = Modifier.padding(
            start = AquaDeviceMenuGeometry.sectionContentPadding,
            end = AquaDeviceMenuGeometry.sectionContentPadding,
            bottom = AquaDeviceMenuGeometry.sectionContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
    ) {
        weekdayRows.forEach { weekdays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
            ) {
                weekdays.forEach { weekdayRes ->
                    AquaDeviceMenuChoiceChip(
                        text = stringResource(weekdayRes),
                        selected = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(WEEKDAY_COLUMN_COUNT - weekdays.size) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun PreviewAction(@StringRes labelRes: Int) {
    AquaGuidedFlowButton(
        text = stringResource(labelRes),
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false
    )
}

private data class PreviewChoice(
    @StringRes val labelRes: Int,
    val selected: Boolean = false
)

private const val WEEKDAY_COLUMN_COUNT = 4

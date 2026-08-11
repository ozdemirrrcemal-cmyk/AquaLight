@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuChoiceChip
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSelectionRow
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
    modifier: Modifier = Modifier,
    reservoirCapacityValue: String = "",
    onReservoirCapacityClick: (() -> Unit)? = null
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
                    DosingDetailMenuItem.DOSING_PLAN -> DosingPlanContent()
                    DosingDetailMenuItem.RESERVOIR -> ReservoirContent(
                        capacityValue = reservoirCapacityValue,
                        onCapacityClick = onReservoirCapacityClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DosingPlanContent() {
    DetailSection(R.string.device_dosing_detail_schedule_status_section) {
        DetailToggleRow(
            titleRes = R.string.device_dosing_detail_activate_schedule,
            descriptionRes = R.string.device_dosing_detail_activate_schedule_description,
            checked = true
        )
    }
    DetailSection(R.string.device_dosing_detail_amount_section) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_daily_dose),
            value = stringResource(R.string.device_dosing_detail_value_zero_ml),
            description = stringResource(R.string.device_dosing_detail_daily_dose_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
    }
    DetailSection(R.string.device_dosing_detail_schedule_section) {
        DosingScheduleOptions()
    }
    DetailSection(R.string.device_dosing_detail_recurrence_section) {
        DosingRecurrenceOptions()
    }
    DetailAction(R.string.device_dosing_detail_save_plan)
}

private fun ReservoirContent(
    capacityValue: String,
    onCapacityClick: (() -> Unit)?
) {
    var reservoirTrackingEnabled by rememberSaveable { mutableStateOf(false) }
    var lowLevelAlertEnabled by rememberSaveable { mutableStateOf(true) }

    DetailSection(R.string.device_dosing_detail_reservoir_tracking_section) {
        DetailToggleRow(
            titleRes = R.string.device_dosing_detail_reservoir_tracking_enabled,
            descriptionRes = R.string.device_dosing_detail_reservoir_tracking_enabled_description,
            checked = reservoirTrackingEnabled,
            onCheckedChange = { enabled ->
                reservoirTrackingEnabled = enabled
            }
        )
    }
    DetailSection(
        titleRes = R.string.device_dosing_detail_reservoir_volume_section,
        enabled = reservoirTrackingEnabled
    ) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_container_volume),
            value = capacityValue,
            modifier = Modifier.clickable(
                enabled = reservoirTrackingEnabled && onCapacityClick != null,
                role = Role.Button,
                onClick = { onCapacityClick?.invoke() }
            ),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider()
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_available_volume),
            value = stringResource(R.string.device_dosing_detail_value_unavailable)
        )
    }
    DetailSection(
        titleRes = R.string.device_dosing_detail_reservoir_alerts_section,
        enabled = reservoirTrackingEnabled
    ) {
        DetailToggleRow(
            titleRes = R.string.device_dosing_detail_low_level_alert,
            descriptionRes = R.string.device_dosing_detail_low_level_alert_description,
            checked = lowLevelAlertEnabled,
            enabled = reservoirTrackingEnabled,
            onCheckedChange = { enabled ->
                lowLevelAlertEnabled = enabled
            }
        )
    }
    DetailAction(R.string.device_dosing_detail_save_reservoir)
}

@Composable
private fun DetailSection(
    @StringRes titleRes: Int,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA)
    ) {
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
private fun DetailToggleRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
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
    val interactionModifier = onCheckedChange?.let { callback ->
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = callback
        )
    } ?: Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
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
private fun DosingScheduleOptions() {
    DOSING_PLAN_SCHEDULE_OPTIONS.forEachIndexed { index, option ->
        if (index > 0) {
            AquaDeviceMenuDivider(
                startIndent = AquaDeviceMenuGeometry.selectionDividerIndent
            )
        }
        AquaDeviceMenuSelectionRow(
            text = stringResource(option.labelRes),
            selected = option.selected
        )
    }
}

@Composable
private fun DosingRecurrenceOptions() {
    AquaDeviceMenuSelectionRow(
        text = stringResource(R.string.device_dosing_channel_every_day),
        selected = true,
        showTrailingIcon = false
    )
    AquaDeviceMenuDivider(
        startIndent = AquaDeviceMenuGeometry.selectionDividerIndent
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AquaDeviceMenuGeometry.sectionContentPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
    ) {
        DOSING_PLAN_WEEKDAY_LABELS.forEach { weekdayRes ->
            AquaDeviceMenuChoiceChip(
                text = stringResource(weekdayRes),
                selected = true,
                modifier = Modifier.weight(1f),
                compact = true
            )
        }
    }
}

@Composable
private fun DetailAction(@StringRes labelRes: Int) {
    AquaGuidedFlowButton(
        text = stringResource(labelRes),
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false
    )
}

internal data class DosingPlanScheduleOption(
    @StringRes val labelRes: Int,
    val selected: Boolean = false
)

internal val DOSING_PLAN_SCHEDULE_OPTIONS = listOf(
    DosingPlanScheduleOption(
        labelRes = R.string.device_dosing_detail_schedule_single,
        selected = true
    ),
    DosingPlanScheduleOption(R.string.device_dosing_detail_schedule_hourly),
    DosingPlanScheduleOption(R.string.device_dosing_detail_schedule_custom),
    DosingPlanScheduleOption(R.string.device_dosing_detail_schedule_timer)
)

internal val DOSING_PLAN_WEEKDAY_LABELS = listOf(
    R.string.device_dosing_weekday_mon,
    R.string.device_dosing_weekday_tue,
    R.string.device_dosing_weekday_wed,
    R.string.device_dosing_weekday_thu,
    R.string.device_dosing_weekday_fri,
    R.string.device_dosing_weekday_sat,
    R.string.device_dosing_weekday_sun
)

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.42f

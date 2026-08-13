package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuActionRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuHeroCard
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowAction
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowContent
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSection
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuValueRow
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract

@Composable
internal fun DeviceDosingTimerScheduleScreen(
    state: DeviceDosingTimerScheduleUiState,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TimerScheduleContent(state, onAction, Modifier.weight(1f))
        TimerScheduleFooter(state.actionEnabled, onAction)
    }
}

@Composable
private fun TimerScheduleContent(
    state: DeviceDosingTimerScheduleUiState,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AquaDeviceMenuGeometry.screenHorizontalPadding,
            top = AquaDeviceMenuGeometry.screenTopPadding,
            end = AquaDeviceMenuGeometry.screenHorizontalPadding,
            bottom = AquaDeviceMenuGeometry.sectionGap
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
    ) {
        item(key = TIMER_HERO_KEY) { TimerScheduleHero() }
        item(key = TIMER_SUMMARY_KEY) {
            TimerScheduleSummary(state.doses, state.maxDoseCount)
        }
        state.validationMessage?.let { message ->
            item(key = TIMER_VALIDATION_KEY) { TimerScheduleValidation(message) }
        }
        item(key = TIMER_DOSES_KEY) {
            TimerDosesSection(state.doses, state.maxDoseCount, onAction)
        }
    }
}

@Composable
private fun TimerScheduleHero() {
    AquaDeviceMenuHeroCard(
        eyebrow = stringResource(R.string.device_dosing_timer_hero_eyebrow),
        title = stringResource(R.string.device_dosing_timer_hero_title),
        description = stringResource(R.string.device_dosing_timer_hero_description)
    )
}

@Composable
private fun TimerScheduleSummary(
    doses: List<DeviceDosingTimerDose>,
    maxDoseCount: Int
) {
    val totalDoseMicroliters = DeviceDosingTimerScheduleContract.totalDoseMicroliters(doses)
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_timer_summary_section)) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_timer_daily_total),
            value = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                DeviceDosingScheduleAmountContract.milliliters(totalDoseMicroliters)
            ),
            description = stringResource(R.string.device_dosing_timer_daily_total_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_timer_application_count),
            value = stringResource(
                R.string.device_dosing_timer_application_count_format,
                doses.size,
                maxDoseCount
            ),
            description = stringResource(R.string.device_dosing_timer_application_count_description)
        )
    }
}

@Composable
private fun TimerScheduleValidation(message: String) {
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_schedule_validation_section)) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_schedule_validation_title),
            value = stringResource(R.string.device_dosing_schedule_validation_symbol),
            description = message,
            tone = AquaDeviceMenuTone.DANGER
        )
    }
}

@Composable
private fun TimerDosesSection(
    doses: List<DeviceDosingTimerDose>,
    maxDoseCount: Int,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit
) {
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_timer_doses_section)) {
        AquaDeviceMenuActionRow(
            content = AquaDeviceMenuRowContent(
                title = stringResource(R.string.device_dosing_timer_doses_title),
                description = stringResource(R.string.device_dosing_timer_doses_description),
                iconRes = R.drawable.ic_dosing_schedule_24
            ),
            action = AquaDeviceMenuRowAction(
                text = stringResource(R.string.device_dosing_schedule_add),
                onClick = { onAction(DeviceDosingTimerScheduleAction.Add) },
                enabled = doses.size < maxDoseCount
            )
        )
        if (doses.isEmpty()) {
            EmptyTimerDoses()
        } else {
            doses.forEachIndexed { index, dose ->
                AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
                TimerDoseRow(index = index, dose = dose, onAction = onAction)
            }
        }
    }
}

@Composable
private fun EmptyTimerDoses() {
    AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
    AquaDeviceMenuValueRow(
        label = stringResource(R.string.device_dosing_timer_empty_title),
        value = stringResource(R.string.device_dosing_detail_value_unavailable),
        description = stringResource(R.string.device_dosing_timer_empty_description)
    )
}

@Composable
private fun TimerDoseRow(
    index: Int,
    dose: DeviceDosingTimerDose,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit
) {
    val context = LocalContext.current
    val time = LocaleFormatter.formatTimeOfDay24Hour(
        context,
        DeviceDosingTimerScheduleContract.minutesOfDay(dose.startTimeMs)
    )
    AquaDeviceMenuActionRow(
        content = AquaDeviceMenuRowContent(
            title = time,
            description = stringResource(
                R.string.device_dosing_timer_entry_amount_format,
                DeviceDosingScheduleAmountContract.milliliters(dose.amountMicroliters)
            ),
            iconRes = R.drawable.ic_dosing_schedule_24
        ),
        action = AquaDeviceMenuRowAction(
            text = stringResource(R.string.common_delete),
            onClick = { onAction(DeviceDosingTimerScheduleAction.Remove(index)) }
        ),
        onClick = { onAction(DeviceDosingTimerScheduleAction.Edit(index)) }
    )
}

@Composable
private fun TimerScheduleFooter(
    enabled: Boolean,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit
) {
    val colors = aquaDeviceMenuColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(
                start = AquaDeviceMenuGeometry.screenHorizontalPadding,
                top = AquaDeviceMenuGeometry.compactGap,
                end = AquaDeviceMenuGeometry.screenHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.screenHorizontalPadding
            )
    ) {
        AquaGuidedFlowButton(
            text = stringResource(R.string.device_dosing_timer_use_action),
            onClick = { onAction(DeviceDosingTimerScheduleAction.Save) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

private const val TIMER_HERO_KEY = "timer-dose-hero"
private const val TIMER_SUMMARY_KEY = "timer-dose-summary"
private const val TIMER_VALIDATION_KEY = "timer-dose-validation"
private const val TIMER_DOSES_KEY = "timer-dose-entries"

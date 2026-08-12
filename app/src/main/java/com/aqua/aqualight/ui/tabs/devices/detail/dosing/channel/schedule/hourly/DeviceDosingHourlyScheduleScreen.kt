package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

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
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuEditableValueRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuHeroCard
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSection
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuValueRow
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton

@Composable
internal fun DeviceDosingHourlyScheduleScreen(
    state: DeviceDosingHourlyScheduleUiState,
    onMinuteClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = aquaDeviceMenuColors()
    val minuteOfHour = DeviceDosingHourlyScheduleContract.minuteOfHour(state.startTimeMs)
    val window = HourlyDoseWindow(
        first = LocaleFormatter.formatTimeOfDay24Hour(context, minuteOfHour),
        last = LocaleFormatter.formatTimeOfDay24Hour(
            context,
            (DeviceDosingHourlyScheduleContract.DOSES_PER_DAY - 1) *
                DeviceDosingHourlyScheduleContract.MINUTES_PER_HOUR + minuteOfHour
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        HourlyScheduleContent(
            state = state,
            minuteOfHour = minuteOfHour,
            window = window,
            onMinuteClick = onMinuteClick,
            modifier = Modifier.weight(1f)
        )
        HourlyScheduleFooter(state.actionEnabled, onSaveClick)
    }
}

@Composable
private fun HourlyScheduleContent(
    state: DeviceDosingHourlyScheduleUiState,
    minuteOfHour: Int,
    window: HourlyDoseWindow,
    onMinuteClick: () -> Unit,
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
        item(key = HOURLY_HERO_KEY) { HourlyScheduleHero() }
        item(key = HOURLY_SUMMARY_KEY) { HourlyScheduleSummary(state.dailyDoseMicroliters) }
        item(key = HOURLY_TIMING_KEY) {
            HourlyScheduleTiming(minuteOfHour, window, onMinuteClick)
        }
    }
}

@Composable
private fun HourlyScheduleHero() {
    AquaDeviceMenuHeroCard(
        eyebrow = stringResource(R.string.device_dosing_hourly_hero_eyebrow),
        title = stringResource(R.string.device_dosing_hourly_hero_title),
        description = stringResource(R.string.device_dosing_hourly_hero_description)
    )
}

@Composable
private fun HourlyScheduleSummary(dailyDoseMicroliters: Long) {
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_hourly_summary_section)) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_daily_dose),
            value = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                DeviceDosingHourlyScheduleContract.dailyDoseMl(dailyDoseMicroliters)
            ),
            description = stringResource(R.string.device_dosing_hourly_daily_amount_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_hourly_frequency),
            value = stringResource(R.string.device_dosing_hourly_dose_count),
            description = stringResource(R.string.device_dosing_hourly_frequency_description)
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_hourly_average_dose),
            value = stringResource(
                R.string.device_dosing_hourly_average_dose_format,
                DeviceDosingHourlyScheduleContract.averageDoseMl(dailyDoseMicroliters)
            ),
            description = stringResource(R.string.device_dosing_hourly_average_dose_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
    }
}

@Composable
private fun HourlyScheduleTiming(
    minuteOfHour: Int,
    window: HourlyDoseWindow,
    onMinuteClick: () -> Unit
) {
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_hourly_timing_section)) {
        AquaDeviceMenuEditableValueRow(
            label = stringResource(R.string.device_dosing_hourly_minute_title),
            value = stringResource(R.string.common_time_picker_minute_of_hour_preview, minuteOfHour),
            description = stringResource(R.string.device_dosing_hourly_minute_description),
            iconRes = R.drawable.ic_dosing_schedule_24,
            onClick = onMinuteClick
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_hourly_window_title),
            value = stringResource(
                R.string.device_dosing_hourly_window_format,
                window.first,
                window.last
            ),
            description = stringResource(R.string.device_dosing_hourly_window_description)
        )
    }
}

@Composable
private fun HourlyScheduleFooter(enabled: Boolean, onSaveClick: () -> Unit) {
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
            text = stringResource(R.string.device_dosing_hourly_use_action),
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

private data class HourlyDoseWindow(val first: String, val last: String)

private const val HOURLY_HERO_KEY = "hourly-dose-hero"
private const val HOURLY_SUMMARY_KEY = "hourly-dose-summary"
private const val HOURLY_TIMING_KEY = "hourly-dose-timing"

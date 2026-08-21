package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single

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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract

@Composable
internal fun DeviceDosingSingleScheduleScreen(
    state: DeviceDosingSingleScheduleUiState,
    onTimeClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = aquaDeviceMenuColors()
    val timeText = LocaleFormatter.formatTimeOfDay24Hour(
        context,
        DeviceDosingSingleScheduleContract.minutesOfDay(state.startTimeMs)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SingleScheduleContent(
            state = state,
            timeText = timeText,
            onTimeClick = onTimeClick,
            modifier = Modifier.weight(1f)
        )
        SingleScheduleFooter(
            enabled = state.actionEnabled,
            onSaveClick = onSaveClick
        )
    }
}

@Composable
private fun SingleScheduleContent(
    state: DeviceDosingSingleScheduleUiState,
    timeText: String,
    onTimeClick: () -> Unit,
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
        item(key = SINGLE_HERO_KEY) { SingleScheduleHero() }
        item(key = SINGLE_SUMMARY_KEY) { SingleScheduleSummary(state.dailyDoseMicroliters) }
        item(key = SINGLE_TIME_KEY) { SingleScheduleTime(timeText, onTimeClick) }
    }
}

@Composable
private fun SingleScheduleHero() {
    AquaDeviceMenuHeroCard(
        eyebrow = stringResource(R.string.device_dosing_single_hero_eyebrow),
        title = stringResource(R.string.device_dosing_single_hero_title),
        description = stringResource(R.string.device_dosing_single_hero_description)
    )
}

@Composable
private fun SingleScheduleSummary(dailyDoseMicroliters: Long) {
    val context = LocalContext.current
    val dailyDose = DeviceDosingScheduleAmountContract.formatDisplay(
        dailyDoseMicroliters,
        context.resources.configuration.locales[0]
    )
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_single_summary_section)) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_daily_dose),
            value = stringResource(
                R.string.device_dosing_single_daily_dose_value_format,
                dailyDose
            ),
            description = stringResource(R.string.device_dosing_single_daily_amount_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_single_frequency),
            value = stringResource(R.string.device_dosing_single_one_dose),
            description = stringResource(R.string.device_dosing_single_frequency_description)
        )
    }
}

@Composable
private fun SingleScheduleTime(timeText: String, onTimeClick: () -> Unit) {
    AquaDeviceMenuSection(title = stringResource(R.string.device_dosing_single_time_section)) {
        AquaDeviceMenuEditableValueRow(
            label = stringResource(R.string.device_dosing_single_time_title),
            value = timeText,
            description = stringResource(R.string.device_dosing_single_time_description),
            iconRes = R.drawable.ic_dosing_schedule_24,
            onClick = onTimeClick
        )
    }
}

@Composable
private fun SingleScheduleFooter(enabled: Boolean, onSaveClick: () -> Unit) {
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
            text = stringResource(R.string.device_dosing_single_use_action),
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

private const val SINGLE_HERO_KEY = "single-dose-hero"
private const val SINGLE_SUMMARY_KEY = "single-dose-summary"
private const val SINGLE_TIME_KEY = "single-dose-time"

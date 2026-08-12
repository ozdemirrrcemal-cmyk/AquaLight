@file:Suppress("LongMethod", "MatchingDeclarationName")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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

@Immutable
internal data class DeviceDosingCustomScheduleUiState(
    val dailyDoseMicroliters: Long,
    val periods: List<DeviceDosingCustomPeriod>,
    val validationMessage: String? = null,
    val actionEnabled: Boolean = dailyDoseMicroliters > 0L && periods.isNotEmpty()
)

internal sealed interface DeviceDosingCustomScheduleAction {
    data object Add : DeviceDosingCustomScheduleAction
    data class Edit(val index: Int) : DeviceDosingCustomScheduleAction
    data class Remove(val index: Int) : DeviceDosingCustomScheduleAction
    data object Save : DeviceDosingCustomScheduleAction
}

/** Custom-period editor with state hoisted to the process-safe Fragment boundary. */
@Composable
internal fun DeviceDosingCustomScheduleScreen(
    state: DeviceDosingCustomScheduleUiState,
    onAction: (DeviceDosingCustomScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = aquaDeviceMenuColors()
    val totalDoseCount = DeviceDosingCustomScheduleContract.totalDoseCount(state.periods)
    val averageDoseMl = DeviceDosingCustomScheduleContract.averageDoseMl(
        dailyDoseMicroliters = state.dailyDoseMicroliters,
        periods = state.periods
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = AquaDeviceMenuGeometry.screenHorizontalPadding,
                top = AquaDeviceMenuGeometry.screenTopPadding,
                end = AquaDeviceMenuGeometry.screenHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
        ) {
            item(key = CUSTOM_HERO_KEY) {
                AquaDeviceMenuHeroCard(
                    eyebrow = stringResource(R.string.device_dosing_custom_hero_eyebrow),
                    title = stringResource(R.string.device_dosing_custom_hero_title),
                    description = stringResource(R.string.device_dosing_custom_hero_description)
                )
            }
            item(key = CUSTOM_SUMMARY_KEY) {
                CustomScheduleSummary(
                    dailyDoseMicroliters = state.dailyDoseMicroliters,
                    totalDoseCount = totalDoseCount,
                    averageDoseMl = averageDoseMl
                )
            }
            state.validationMessage?.let { message ->
                item(key = CUSTOM_VALIDATION_KEY) {
                    AquaDeviceMenuSection(
                        title = stringResource(R.string.device_dosing_schedule_validation_section)
                    ) {
                        AquaDeviceMenuValueRow(
                            label = stringResource(R.string.device_dosing_schedule_validation_title),
                            value = stringResource(R.string.device_dosing_schedule_validation_symbol),
                            description = message,
                            tone = AquaDeviceMenuTone.DANGER
                        )
                    }
                }
            }
            item(key = CUSTOM_PERIODS_KEY) {
                AquaDeviceMenuSection(
                    title = stringResource(R.string.device_dosing_custom_periods_section)
                ) {
                    AquaDeviceMenuActionRow(
                        content = AquaDeviceMenuRowContent(
                            title = stringResource(R.string.device_dosing_custom_periods_title),
                            description = stringResource(
                                R.string.device_dosing_custom_periods_capacity,
                                totalDoseCount,
                                DeviceDosingCustomScheduleContract.MAX_DOSES_PER_DAY
                            ),
                            iconRes = R.drawable.ic_dosing_schedule_24
                        ),
                        action = AquaDeviceMenuRowAction(
                            text = stringResource(R.string.device_dosing_schedule_add),
                            onClick = { onAction(DeviceDosingCustomScheduleAction.Add) },
                            enabled = totalDoseCount <
                                DeviceDosingCustomScheduleContract.MAX_DOSES_PER_DAY
                        )
                    )
                    if (state.periods.isEmpty()) {
                        AquaDeviceMenuDivider(
                            startIndent = AquaDeviceMenuGeometry.sectionContentPadding
                        )
                        AquaDeviceMenuValueRow(
                            label = stringResource(R.string.device_dosing_custom_empty_title),
                            value = stringResource(R.string.device_dosing_detail_value_unavailable),
                            description = stringResource(R.string.device_dosing_custom_empty_description)
                        )
                    } else {
                        state.periods.forEachIndexed { index, period ->
                            AquaDeviceMenuDivider(
                                startIndent = AquaDeviceMenuGeometry.sectionContentPadding
                            )
                            val startTime = LocaleFormatter.formatTimeOfDay24Hour(
                                context,
                                DeviceDosingCustomScheduleContract.minutesOfDay(period.startTimeMs)
                            )
                            val endTime = LocaleFormatter.formatTimeOfDay24Hour(
                                context,
                                DeviceDosingCustomScheduleContract.minutesOfDay(period.endTimeMs)
                            )
                            AquaDeviceMenuActionRow(
                                content = AquaDeviceMenuRowContent(
                                    title = stringResource(
                                        R.string.device_dosing_custom_period_time_format,
                                        startTime,
                                        endTime
                                    ),
                                    description = pluralStringResource(
                                        R.plurals.device_dosing_custom_period_dose_count_format,
                                        period.doseCount,
                                        period.doseCount
                                    ),
                                    iconRes = R.drawable.ic_dosing_schedule_24
                                ),
                                action = AquaDeviceMenuRowAction(
                                    text = stringResource(R.string.common_delete),
                                    onClick = {
                                        onAction(DeviceDosingCustomScheduleAction.Remove(index))
                                    }
                                ),
                                onClick = {
                                    onAction(DeviceDosingCustomScheduleAction.Edit(index))
                                }
                            )
                        }
                    }
                }
            }
        }

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
                text = stringResource(R.string.device_dosing_custom_use_action),
                onClick = { onAction(DeviceDosingCustomScheduleAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.actionEnabled
            )
        }
    }
}

@Composable
private fun CustomScheduleSummary(
    dailyDoseMicroliters: Long,
    totalDoseCount: Int,
    averageDoseMl: Double
) {
    AquaDeviceMenuSection(
        title = stringResource(R.string.device_dosing_custom_summary_section)
    ) {
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_detail_daily_dose),
            value = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                DeviceDosingScheduleAmountContract.milliliters(dailyDoseMicroliters)
            ),
            description = stringResource(R.string.device_dosing_custom_daily_amount_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_custom_total_applications),
            value = pluralStringResource(
                R.plurals.device_dosing_custom_total_applications_format,
                totalDoseCount,
                totalDoseCount
            ),
            description = stringResource(R.string.device_dosing_custom_total_applications_description)
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.sectionContentPadding)
        AquaDeviceMenuValueRow(
            label = stringResource(R.string.device_dosing_custom_average_dose),
            value = stringResource(
                R.string.device_dosing_custom_average_dose_format,
                averageDoseMl
            ),
            description = stringResource(R.string.device_dosing_custom_average_dose_description),
            tone = AquaDeviceMenuTone.ACCENT
        )
    }
}

private const val CUSTOM_HERO_KEY = "custom-dose-hero"
private const val CUSTOM_SUMMARY_KEY = "custom-dose-summary"
private const val CUSTOM_VALIDATION_KEY = "custom-dose-validation"
private const val CUSTOM_PERIODS_KEY = "custom-dose-periods"

@file:Suppress("LongMethod", "MatchingDeclarationName")

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
import androidx.compose.runtime.Immutable
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

@Immutable
internal data class DeviceDosingTimerScheduleUiState(
    val doses: List<DeviceDosingTimerDose>,
    val validationMessage: String? = null,
    val actionEnabled: Boolean = doses.isNotEmpty()
)

internal sealed interface DeviceDosingTimerScheduleAction {
    data object Add : DeviceDosingTimerScheduleAction
    data class Edit(val index: Int) : DeviceDosingTimerScheduleAction
    data class Remove(val index: Int) : DeviceDosingTimerScheduleAction
    data object Save : DeviceDosingTimerScheduleAction
}

/** Independent time-and-amount editor; the daily total is derived from its entries. */
@Composable
internal fun DeviceDosingTimerScheduleScreen(
    state: DeviceDosingTimerScheduleUiState,
    onAction: (DeviceDosingTimerScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = aquaDeviceMenuColors()
    val totalDoseMicroliters = DeviceDosingTimerScheduleContract.totalDoseMicroliters(state.doses)

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
            item(key = TIMER_HERO_KEY) {
                AquaDeviceMenuHeroCard(
                    eyebrow = stringResource(R.string.device_dosing_timer_hero_eyebrow),
                    title = stringResource(R.string.device_dosing_timer_hero_title),
                    description = stringResource(R.string.device_dosing_timer_hero_description)
                )
            }
            item(key = TIMER_SUMMARY_KEY) {
                AquaDeviceMenuSection(
                    title = stringResource(R.string.device_dosing_timer_summary_section)
                ) {
                    AquaDeviceMenuValueRow(
                        label = stringResource(R.string.device_dosing_timer_daily_total),
                        value = stringResource(
                            R.string.device_dosing_channel_daily_dose_format,
                            DeviceDosingScheduleAmountContract.milliliters(
                                totalDoseMicroliters
                            )
                        ),
                        description = stringResource(
                            R.string.device_dosing_timer_daily_total_description
                        ),
                        tone = AquaDeviceMenuTone.ACCENT
                    )
                    AquaDeviceMenuDivider(
                        startIndent = AquaDeviceMenuGeometry.sectionContentPadding
                    )
                    AquaDeviceMenuValueRow(
                        label = stringResource(R.string.device_dosing_timer_application_count),
                        value = stringResource(
                            R.string.device_dosing_timer_application_count_format,
                            state.doses.size,
                            DeviceDosingTimerScheduleContract.MAX_DOSES_PER_DAY
                        ),
                        description = stringResource(
                            R.string.device_dosing_timer_application_count_description
                        )
                    )
                }
            }
            state.validationMessage?.let { message ->
                item(key = TIMER_VALIDATION_KEY) {
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
            item(key = TIMER_DOSES_KEY) {
                AquaDeviceMenuSection(
                    title = stringResource(R.string.device_dosing_timer_doses_section)
                ) {
                    AquaDeviceMenuActionRow(
                        content = AquaDeviceMenuRowContent(
                            title = stringResource(R.string.device_dosing_timer_doses_title),
                            description = stringResource(R.string.device_dosing_timer_doses_description),
                            iconRes = R.drawable.ic_dosing_schedule_24
                        ),
                        action = AquaDeviceMenuRowAction(
                            text = stringResource(R.string.device_dosing_schedule_add),
                            onClick = { onAction(DeviceDosingTimerScheduleAction.Add) },
                            enabled = state.doses.size <
                                DeviceDosingTimerScheduleContract.MAX_DOSES_PER_DAY
                        )
                    )
                    if (state.doses.isEmpty()) {
                        AquaDeviceMenuDivider(
                            startIndent = AquaDeviceMenuGeometry.sectionContentPadding
                        )
                        AquaDeviceMenuValueRow(
                            label = stringResource(R.string.device_dosing_timer_empty_title),
                            value = stringResource(R.string.device_dosing_detail_value_unavailable),
                            description = stringResource(R.string.device_dosing_timer_empty_description)
                        )
                    } else {
                        state.doses.forEachIndexed { index, dose ->
                            AquaDeviceMenuDivider(
                                startIndent = AquaDeviceMenuGeometry.sectionContentPadding
                            )
                            val time = LocaleFormatter.formatTimeOfDay24Hour(
                                context,
                                DeviceDosingTimerScheduleContract.minutesOfDay(dose.startTimeMs)
                            )
                            AquaDeviceMenuActionRow(
                                content = AquaDeviceMenuRowContent(
                                    title = time,
                                    description = stringResource(
                                        R.string.device_dosing_timer_entry_amount_format,
                                        DeviceDosingScheduleAmountContract.milliliters(
                                            dose.amountMicroliters
                                        )
                                    ),
                                    iconRes = R.drawable.ic_dosing_schedule_24
                                ),
                                action = AquaDeviceMenuRowAction(
                                    text = stringResource(R.string.common_delete),
                                    onClick = {
                                        onAction(DeviceDosingTimerScheduleAction.Remove(index))
                                    }
                                ),
                                onClick = {
                                    onAction(DeviceDosingTimerScheduleAction.Edit(index))
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
                text = stringResource(R.string.device_dosing_timer_use_action),
                onClick = { onAction(DeviceDosingTimerScheduleAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.actionEnabled
            )
        }
    }
}

private const val TIMER_HERO_KEY = "timer-dose-hero"
private const val TIMER_SUMMARY_KEY = "timer-dose-summary"
private const val TIMER_VALIDATION_KEY = "timer-dose-validation"
private const val TIMER_DOSES_KEY = "timer-dose-entries"

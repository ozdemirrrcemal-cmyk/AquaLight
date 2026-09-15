package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun AdaptationSettingsCard(
    state: DeviceLightAdaptationUiState,
    actions: DeviceLightAdaptationActions,
    visuals: DeviceLightAdaptationVisuals
) {
    val snapshot = state.snapshot ?: return
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.controlGap)) {
            BasicText(
                text = stringResource(R.string.device_light_adaptation_setup_title),
                style = visuals.typography.title
            )
            AdaptationSettingControl(
                content = state.startPercentControl(),
                enabled = state.contentEnabled && !state.operationInProgress,
                onValueChanged = actions.onStartPercentChanged,
                visuals = visuals
            )
            AdaptationSettingControl(
                content = state.durationControl(),
                enabled = state.contentEnabled && !state.operationInProgress,
                onValueChanged = actions.onDurationDaysChanged,
                visuals = visuals
            )
            if (!snapshot.clockReady) {
                BasicText(
                    text = stringResource(R.string.device_light_adaptation_clock_blocked),
                    style = visuals.typography.caption.copy(color = visuals.colors.card.warning)
                )
            }
        }
    }
}

@Composable
private fun AdaptationSettingControl(
    content: AdaptationSettingContent,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    visuals: DeviceLightAdaptationVisuals
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = stringResource(content.labelRes),
                style = visuals.typography.body,
                modifier = Modifier.weight(CONTENT_WEIGHT)
            )
            BasicText(
                text = content.valueText,
                style = visuals.typography.title.copy(
                    color = visuals.colors.action,
                    textAlign = TextAlign.End
                )
            )
        }
        DeviceLightAdaptationSlider(
            state = DeviceLightAdaptationSliderState(
                value = content.value,
                minimum = content.minimum,
                maximum = content.maximum,
                step = content.step,
                enabled = enabled,
                contentDescription = stringResource(content.descriptionRes),
                stateDescription = content.valueText
            ),
            onValueChanged = onValueChanged,
            visuals = visuals,
            modifier = Modifier.fillMaxWidth()
        )
        BasicText(
            text = content.helperText,
            style = visuals.typography.micro,
            modifier = Modifier.padding(top = DeviceLightAdaptationGeometry.helperTopGap)
        )
    }
}

@Composable
internal fun AdaptationSetupSummaryCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    val snapshot = state.snapshot ?: return
    AdaptationSummaryCard(
        titleRes = R.string.device_light_adaptation_summary_title,
        rows = setupSummaryRows(snapshot),
        visuals = visuals
    )
}

@Composable
internal fun AdaptationActiveSummaryCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    val snapshot = state.snapshot ?: return
    AdaptationSummaryCard(
        titleRes = R.string.device_light_adaptation_program_summary,
        rows = activeSummaryRows(snapshot),
        visuals = visuals
    )
}

@Composable
private fun AdaptationSummaryCard(
    titleRes: Int,
    rows: List<AdaptationSummaryRow>,
    visuals: DeviceLightAdaptationVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.summaryRowGap)) {
            BasicText(text = stringResource(titleRes), style = visuals.typography.title)
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DeviceLightAdaptationGeometry.dividerHeight)
                            .background(
                                visuals.colors.card.outline.copy(
                                    alpha = DeviceLightAdaptationAlpha.divider
                                )
                            )
                    )
                }
                AdaptationSummaryRow(row, visuals)
            }
        }
    }
}

@Composable
private fun AdaptationSummaryRow(
    row: AdaptationSummaryRow,
    visuals: DeviceLightAdaptationVisuals
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = stringResource(row.labelRes),
            style = visuals.typography.caption,
            modifier = Modifier.width(DeviceLightAdaptationGeometry.summaryLabelWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = row.value,
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.weight(CONTENT_WEIGHT),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeviceLightAdaptationUiState.startPercentControl(): AdaptationSettingContent {
    val policy = requireNotNull(snapshot).policy
    return AdaptationSettingContent(
        labelRes = R.string.device_light_adaptation_start_level,
        descriptionRes = R.string.device_light_adaptation_start_level_description,
        value = selectedStartPercent,
        minimum = policy.startPercentMin,
        maximum = policy.startPercentMax,
        step = policy.startPercentStep,
        valueText = stringResource(R.string.device_light_adaptation_percent, selectedStartPercent),
        helperText = stringResource(
            R.string.device_light_adaptation_percent_range,
            policy.startPercentMin,
            policy.startPercentMax,
            policy.startPercentStep
        )
    )
}

@Composable
private fun DeviceLightAdaptationUiState.durationControl(): AdaptationSettingContent {
    val policy = requireNotNull(snapshot).policy
    return AdaptationSettingContent(
        labelRes = R.string.device_light_adaptation_duration,
        descriptionRes = R.string.device_light_adaptation_duration_description,
        value = selectedDurationDays,
        minimum = policy.durationDaysMin,
        maximum = policy.durationDaysMax,
        step = policy.durationDaysStep,
        valueText = pluralStringResource(
            R.plurals.device_light_adaptation_days_value,
            selectedDurationDays,
            selectedDurationDays
        ),
        helperText = stringResource(
            R.string.device_light_adaptation_day_range_text,
            stringResource(
                R.string.device_light_adaptation_day_range,
                policy.durationDaysMin,
                policy.durationDaysMax
            ),
            stringResource(R.string.device_light_adaptation_day_unit)
        )
    )
}

@Composable
private fun setupSummaryRows(snapshot: DeviceLightAdaptationSnapshot) = listOf(
    AdaptationSummaryRow(
        R.string.device_light_adaptation_target,
        stringResource(R.string.device_light_adaptation_percent, snapshot.targetPercent)
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_modes,
        stringResource(R.string.device_light_adaptation_modes_value)
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_starts,
        stringResource(R.string.device_light_adaptation_starts_value)
    )
)

@Composable
private fun activeSummaryRows(snapshot: DeviceLightAdaptationSnapshot) = listOf(
    AdaptationSummaryRow(
        R.string.device_light_adaptation_start_level,
        stringResource(R.string.device_light_adaptation_percent, snapshot.startPercent)
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_target,
        stringResource(R.string.device_light_adaptation_percent, snapshot.targetPercent)
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_total_duration,
        pluralStringResource(
            R.plurals.device_light_adaptation_days_value,
            snapshot.durationDays,
            snapshot.durationDays
        )
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_end_date,
        snapshot.endDateText()
    ),
    AdaptationSummaryRow(
        R.string.device_light_adaptation_modes,
        stringResource(R.string.device_light_adaptation_modes_value)
    )
)

private data class AdaptationSettingContent(
    val labelRes: Int,
    val descriptionRes: Int,
    val value: Int,
    val minimum: Int,
    val maximum: Int,
    val step: Int,
    val valueText: String,
    val helperText: String
)

private data class AdaptationSummaryRow(val labelRes: Int, val value: String)

private const val CONTENT_WEIGHT = 1f

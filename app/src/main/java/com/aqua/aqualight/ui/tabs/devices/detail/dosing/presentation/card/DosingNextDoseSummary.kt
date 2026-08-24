package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingNextDoseSummary(
    state: DosingNextDoseUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clockTime = LocaleFormatter.formatTimeOfDay24Hour(
        context = context,
        minutesOfDay = (state.timeMillis / MILLIS_PER_MINUTE).toInt()
    )
    val displayTime = if (state.programDayOffset == NEXT_DAY_OFFSET) {
        stringResource(R.string.device_dosing_channel_next_day_time_format, clockTime)
    } else {
        clockTime
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NEXT_DOSE_TEXT_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_dosing_channel_next_dose_label),
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        BasicText(
            text = stringResource(
                R.string.device_dosing_channel_next_dose_value_format,
                displayTime,
                state.amountMl
            ),
            style = typography.micro.copy(color = colors.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val NEXT_DAY_OFFSET = 1
private val NEXT_DOSE_TEXT_GAP = 4.dp

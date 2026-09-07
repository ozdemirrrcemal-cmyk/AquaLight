package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import java.util.Locale

@Composable
internal fun coolingStatusBooleanText(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.device_cooling_system_status_yes
        false -> R.string.device_cooling_system_status_no
        null -> R.string.device_cooling_value_unavailable
    }
)

@Composable
internal fun coolingRuntimePercentText(value: Double?): String = value?.let { percent ->
    stringResource(R.string.device_cooling_system_status_percent_value_format, percent)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
internal fun coolingPowerText(value: Double?): String = value?.let { watts ->
    stringResource(R.string.device_cooling_system_status_power_value_format, watts)
} ?: stringResource(R.string.device_cooling_value_unavailable)

internal fun coolingMinuteOfDayText(minute: Int): String = String.format(
    Locale.getDefault(),
    "%02d:%02d",
    minute / MINUTES_PER_HOUR,
    minute % MINUTES_PER_HOUR
)

private const val MINUTES_PER_HOUR = 60

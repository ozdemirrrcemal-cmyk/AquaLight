package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R

@Composable
internal fun weekdayLabels(): List<String> = listOf(
    stringResource(R.string.device_light_library_day_monday),
    stringResource(R.string.device_light_library_day_tuesday),
    stringResource(R.string.device_light_library_day_wednesday),
    stringResource(R.string.device_light_library_day_thursday),
    stringResource(R.string.device_light_library_day_friday),
    stringResource(R.string.device_light_library_day_saturday),
    stringResource(R.string.device_light_library_day_sunday)
)

internal fun formatTime(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    val hour = totalMinutes / MINUTES_PER_HOUR
    val minute = totalMinutes % MINUTES_PER_HOUR
    return hour.toString().padStart(TIME_DIGITS, '0') + ":" +
        minute.toString().padStart(TIME_DIGITS, '0')
}

private const val MINUTES_PER_HOUR = 60
private const val TIME_DIGITS = 2

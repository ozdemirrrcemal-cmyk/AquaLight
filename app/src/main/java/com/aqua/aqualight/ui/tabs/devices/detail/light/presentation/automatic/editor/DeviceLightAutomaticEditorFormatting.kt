package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R

@Composable
internal fun automaticEditorTimeText(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    return stringResource(
        R.string.device_light_auto_time_format,
        (totalMinutes / MINUTES_PER_HOUR).toInt(),
        (totalMinutes % MINUTES_PER_HOUR).toInt()
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

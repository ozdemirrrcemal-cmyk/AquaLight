package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.compose.ui.graphics.Color
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.ceil

internal fun DeviceLightAdaptationUiState.introTitleRes(): Int = when (screenState) {
    DeviceLightAdaptationScreenState.SETUP -> R.string.device_light_adaptation_intro_title
    DeviceLightAdaptationScreenState.ACTIVE -> R.string.device_light_adaptation_active_title
    DeviceLightAdaptationScreenState.COMPLETED -> R.string.device_light_adaptation_completed_title
}

internal fun DeviceLightAdaptationUiState.informationRes(): Int = when {
    snapshot?.clockReady == false -> R.string.device_light_adaptation_clock_info
    screenState == DeviceLightAdaptationScreenState.ACTIVE ->
        R.string.device_light_adaptation_active_manual_info
    else -> R.string.device_light_adaptation_manual_info
}

internal fun DeviceLightAdaptationUiState.toStatusChip(
    visuals: DeviceLightAdaptationVisuals
): AdaptationStatusChip = when (screenState) {
    DeviceLightAdaptationScreenState.SETUP -> AdaptationStatusChip(
        R.string.device_light_adaptation_off_uppercase,
        visuals.colors.card.secondaryText
    )
    DeviceLightAdaptationScreenState.ACTIVE -> AdaptationStatusChip(
        R.string.device_light_active_uppercase,
        visuals.colors.action
    )
    DeviceLightAdaptationScreenState.COMPLETED -> AdaptationStatusChip(
        R.string.device_light_adaptation_completed_uppercase,
        visuals.colors.card.success
    )
}

internal fun DeviceLightAdaptationSnapshot.currentPercentValue(): String {
    val permille = currentPermille
    return if (permille == null) {
        "—"
    } else {
        val value = permille.toDouble() / DeviceLightAdaptationSpec.percentScale
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = if (
                permille % DeviceLightAdaptationSpec.percentScale == 0
            ) {
                0
            } else {
                1
            }
            maximumFractionDigits = 1
        }.format(value)
    }
}

internal fun DeviceLightAdaptationSnapshot.progressFraction(): Float {
    val start = startPercent * DeviceLightAdaptationSpec.percentScale
    val target = targetPercent * DeviceLightAdaptationSpec.percentScale
    val current = currentPermille ?: start
    return (current - start).toFloat() / (target - start).coerceAtLeast(1)
}

internal fun DeviceLightAdaptationSnapshot.remainingDays(): Int? = remainingSeconds?.let { seconds ->
    ceil(seconds.toDouble() / DeviceLightAdaptationSpec.secondsPerDay).toInt()
}

internal fun DeviceLightAdaptationSnapshot.elapsedDays(): Int? = remainingDays()?.let { remaining ->
    (durationDays - remaining).coerceIn(0, durationDays)
}

internal fun DeviceLightAdaptationSnapshot.endDateText(): String = endsAtEpochSeconds?.let { epoch ->
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epoch * MILLIS_PER_SECOND))
} ?: "—"

internal data class AdaptationStatusChip(val labelRes: Int, val color: Color)

private const val MILLIS_PER_SECOND = 1_000L

package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.validation

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.LightQuickSetupDraft

object LightQuickSetupValidator {

    fun validate(
        draft: LightQuickSetupDraft
    ): LightQuickSetupValidationResult {
        if (draft.selectedDays.isEmpty()) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_one_day_required
            )
        }

        if (draft.sunsetEndMinutes <= draft.sunriseStartMinutes) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_time_order
            )
        }

        if (draft.rampMinutes !in RAMP_MINUTES_MIN..RAMP_MINUTES_MAX) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_ramp_invalid
            )
        }

        if (draft.peakIntensityPercent !in PEAK_INTENSITY_MIN..PEAK_INTENSITY_MAX) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_peak_invalid
            )
        }

        val totalLightWindow =
            draft.sunsetEndMinutes - draft.sunriseStartMinutes

        val minimumRequiredWindow =
            (draft.rampMinutes * 2) + MINIMUM_PEAK_HOLD_MINUTES

        if (totalLightWindow < minimumRequiredWindow) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_time_range_too_short
            )
        }

        if (draft.peakEndMinutes <= draft.peakStartMinutes) {
            return LightQuickSetupValidationResult.Invalid(
                messageRes = R.string.light_quick_setup_error_curve_invalid
            )
        }

        return LightQuickSetupValidationResult.Valid
    }

    private const val RAMP_MINUTES_MIN = 15
    private const val RAMP_MINUTES_MAX = 120

    private const val PEAK_INTENSITY_MIN = 10
    private const val PEAK_INTENSITY_MAX = 100

    private const val MINIMUM_PEAK_HOLD_MINUTES = 30
}

sealed class LightQuickSetupValidationResult {

    data object Valid : LightQuickSetupValidationResult()

    data class Invalid(
        @StringRes val messageRes: Int
    ) : LightQuickSetupValidationResult()
}
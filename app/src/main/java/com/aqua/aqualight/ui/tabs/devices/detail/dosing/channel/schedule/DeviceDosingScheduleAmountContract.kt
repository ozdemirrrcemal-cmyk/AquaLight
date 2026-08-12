package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule

import com.aqua.aqualight.application.devices.dosing.DeviceDosingAmountDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleDraftLimits
import java.text.NumberFormat
import java.util.Locale

/** Locale-aware text adapter around the application-owned Dosing amount policy. */
internal object DeviceDosingScheduleAmountContract {
    const val MICROLITERS_PER_MILLILITER =
        DeviceDosingScheduleDraftLimits.MICROLITERS_PER_MILLILITER

    fun parseMicroliters(rawValue: String): Long? = rawValue
        .trim()
        .replace(',', '.')
        .takeIf(String::isNotBlank)
        ?.toBigDecimalOrNull()
        ?.let(DeviceDosingAmountDraftPolicy::exactMicroliters)

    fun milliliters(microliters: Long): Double =
        DeviceDosingAmountDraftPolicy.milliliters(microliters)

    fun formatInput(microliters: Long, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = MAX_INPUT_FRACTION_DIGITS
        }.format(DeviceDosingAmountDraftPolicy.milliliters(microliters))

    private const val MAX_INPUT_FRACTION_DIGITS = 3
}

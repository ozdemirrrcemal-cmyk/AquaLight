package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import java.text.NumberFormat
import java.util.Locale

internal object DosingDeviceCardFormatter {

    fun integer(
        value: Int,
        locale: Locale
    ): String {
        return NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
        }.format(value)
    }

    fun milliliters(
        microliters: Long,
        locale: Locale,
        fractionDigits: Int
    ): String {
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
            isGroupingUsed = false
        }
        return formatter.format(microliters.toDouble() / MICROLITERS_PER_MILLILITER)
    }

    fun time(
        timeMillis: Long,
        locale: Locale
    ): String {
        val totalMinutes = timeMillis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return String.format(locale, "%02d:%02d", hours, minutes)
    }

    private const val MICROLITERS_PER_MILLILITER = 1_000.0
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
}

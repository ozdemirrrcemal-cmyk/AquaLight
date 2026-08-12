package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Exact ml/µl conversion shared by Dosing schedule draft editors. */
internal object DeviceDosingScheduleAmountContract {
    const val MICROLITERS_PER_MILLILITER = 1_000L

    fun parseMicroliters(rawValue: String): Long? = rawValue
            .trim()
            .replace(',', '.')
            .takeIf(String::isNotBlank)
            ?.toBigDecimalOrNull()
            ?.takeIf { milliliters -> milliliters.signum() > 0 }
            ?.let { milliliters ->
                runCatching {
                    milliliters
                        .multiply(BigDecimal.valueOf(MICROLITERS_PER_MILLILITER))
                        .stripTrailingZeros()
                        .longValueExact()
                }.getOrNull()
            }
            ?.takeIf { microliters -> microliters > 0L }

    fun milliliters(microliters: Long): Double {
        require(microliters >= 0L) { "microliters must not be negative." }
        return microliters.toDouble() / MICROLITERS_PER_MILLILITER
    }

    fun formatInput(microliters: Long, locale: Locale): String {
        require(microliters >= 0L) { "microliters must not be negative." }
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = MAX_INPUT_FRACTION_DIGITS
        }.format(milliliters(microliters))
    }

    private const val MAX_INPUT_FRACTION_DIGITS = 3
}

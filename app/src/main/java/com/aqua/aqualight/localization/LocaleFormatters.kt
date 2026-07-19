package com.aqua.aqualight.localization

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Creates a fresh formatter per call because java.text formatters are not thread-safe. */
object LocaleFormatters {

    fun formatDate(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        style: Int = DateFormat.MEDIUM
    ): String = DateFormat.getDateInstance(style, locale).format(Date(epochMillis))

    fun formatTime(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        style: Int = DateFormat.SHORT
    ): String = DateFormat.getTimeInstance(style, locale).format(Date(epochMillis))

    fun formatDateTime(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        dateStyle: Int = DateFormat.MEDIUM,
        timeStyle: Int = DateFormat.SHORT
    ): String = DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale)
        .format(Date(epochMillis))

    fun formatInteger(
        value: Long,
        locale: Locale = Locale.getDefault()
    ): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun formatNumber(
        value: Number,
        locale: Locale = Locale.getDefault(),
        minimumFractionDigits: Int = 0,
        maximumFractionDigits: Int = 2
    ): String {
        require(minimumFractionDigits >= 0) { "minimumFractionDigits must be non-negative" }
        require(maximumFractionDigits >= minimumFractionDigits) {
            "maximumFractionDigits must be greater than or equal to minimumFractionDigits"
        }

        return NumberFormat.getNumberInstance(locale).apply {
            this.minimumFractionDigits = minimumFractionDigits
            this.maximumFractionDigits = maximumFractionDigits
            isGroupingUsed = true
        }.format(value)
    }

    /** The input is a fraction: 0.42 is rendered as 42%. */
    fun formatPercent(
        fraction: Number,
        locale: Locale = Locale.getDefault(),
        maximumFractionDigits: Int = 0
    ): String {
        require(maximumFractionDigits >= 0) { "maximumFractionDigits must be non-negative" }
        return NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits
        }.format(fraction)
    }
}

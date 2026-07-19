package com.aqua.aqualight.localization

import android.content.Context
import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Locale-safe presentation and user-input formatting boundary. */
object LocaleFormatters {

    fun currentLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    fun formatDate(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        style: Int = DateFormat.MEDIUM
    ): String = DateFormat.getDateInstance(style, locale).format(Date(epochMillis))

    fun formatDate(
        context: Context,
        epochMillis: Long,
        style: Int = DateFormat.MEDIUM
    ): String = formatDate(epochMillis, currentLocale(context), style)

    fun formatTime(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        style: Int = DateFormat.SHORT
    ): String = DateFormat.getTimeInstance(style, locale).format(Date(epochMillis))

    fun formatTime(
        context: Context,
        epochMillis: Long,
        style: Int = DateFormat.SHORT
    ): String = formatTime(epochMillis, currentLocale(context), style)

    fun formatDateTime(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        dateStyle: Int = DateFormat.MEDIUM,
        timeStyle: Int = DateFormat.SHORT
    ): String = DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale)
        .format(Date(epochMillis))

    fun formatDateTime(
        context: Context,
        epochMillis: Long,
        dateStyle: Int = DateFormat.MEDIUM,
        timeStyle: Int = DateFormat.SHORT
    ): String = formatDateTime(epochMillis, currentLocale(context), dateStyle, timeStyle)

    fun formatInteger(
        value: Long,
        locale: Locale = Locale.getDefault()
    ): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun formatInteger(context: Context, value: Long): String =
        formatInteger(value, currentLocale(context))

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

    fun formatNumber(
        context: Context,
        value: Number,
        minimumFractionDigits: Int = 0,
        maximumFractionDigits: Int = 2
    ): String = formatNumber(
        value = value,
        locale = currentLocale(context),
        minimumFractionDigits = minimumFractionDigits,
        maximumFractionDigits = maximumFractionDigits
    )

    /** Parses the complete user-entered value using the active locale; partial parses are rejected. */
    fun parseNumber(text: CharSequence?, locale: Locale = Locale.getDefault()): Number? {
        val normalized = text?.toString()?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val position = ParsePosition(0)
        val result = NumberFormat.getNumberInstance(locale).parse(normalized, position) ?: return null
        return result.takeIf { position.index == normalized.length && position.errorIndex < 0 }
    }

    fun parseNumber(context: Context, text: CharSequence?): Number? =
        parseNumber(text, currentLocale(context))

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

    fun formatPercent(
        context: Context,
        fraction: Number,
        maximumFractionDigits: Int = 0
    ): String = formatPercent(fraction, currentLocale(context), maximumFractionDigits)

    /** Stable, non-presentational key used only to group records by local calendar day. */
    fun localDayKey(epochMillis: Long): String = Calendar.getInstance().apply {
        timeInMillis = epochMillis
    }.let { calendar ->
        "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }
}

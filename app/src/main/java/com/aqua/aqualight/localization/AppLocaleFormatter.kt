package com.aqua.aqualight.localization

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import androidx.appcompat.app.AppCompatDelegate
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Single UI-facing locale formatter boundary.
 *
 * Device protocols, QR payloads, persistence keys and cryptographic formats must not use this
 * class; those machine contracts remain Locale.ROOT/fixed-format by design.
 */
object AppLocaleFormatter {

    fun currentLocale(context: Context): Locale {
        val applicationLocale = AppCompatDelegate.getApplicationLocales()[0]
        if (applicationLocale != null) {
            return applicationLocale
        }
        return context.resources.configuration.locales[0] ?: Locale.getDefault()
    }

    fun formatDate(
        context: Context,
        epochMillis: Long,
        style: Int = DateFormat.MEDIUM
    ): String {
        return formatDate(
            locale = currentLocale(context),
            epochMillis = epochMillis,
            style = style
        )
    }

    fun formatDate(
        locale: Locale,
        epochMillis: Long,
        style: Int = DateFormat.MEDIUM
    ): String {
        return DateFormat.getDateInstance(style, locale).format(Date(epochMillis))
    }

    fun formatTime(context: Context, epochMillis: Long): String {
        return AndroidDateFormat.getTimeFormat(context).format(Date(epochMillis))
    }

    fun formatDateTime(
        context: Context,
        epochMillis: Long,
        dateStyle: Int = DateFormat.MEDIUM
    ): String {
        return listOf(
            formatDate(context, epochMillis, dateStyle),
            formatTime(context, epochMillis)
        ).joinToString(separator = " ")
    }

    fun formatLocalDate(
        context: Context,
        value: LocalDate,
        style: FormatStyle = FormatStyle.MEDIUM
    ): String {
        return DateTimeFormatter.ofLocalizedDate(style)
            .withLocale(currentLocale(context))
            .format(value)
    }

    fun formatLocalDateTime(
        context: Context,
        value: LocalDateTime,
        dateStyle: FormatStyle = FormatStyle.MEDIUM,
        timeStyle: FormatStyle = FormatStyle.SHORT
    ): String {
        return DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)
            .withLocale(currentLocale(context))
            .format(value)
    }

    fun formatNumber(
        context: Context,
        value: Number,
        minimumFractionDigits: Int = 0,
        maximumFractionDigits: Int = 2
    ): String {
        return formatNumber(
            locale = currentLocale(context),
            value = value,
            minimumFractionDigits = minimumFractionDigits,
            maximumFractionDigits = maximumFractionDigits
        )
    }

    fun formatNumber(
        locale: Locale,
        value: Number,
        minimumFractionDigits: Int = 0,
        maximumFractionDigits: Int = 2
    ): String {
        require(minimumFractionDigits >= 0)
        require(maximumFractionDigits >= minimumFractionDigits)

        return NumberFormat.getNumberInstance(locale).apply {
            this.minimumFractionDigits = minimumFractionDigits
            this.maximumFractionDigits = maximumFractionDigits
            isGroupingUsed = true
        }.format(value)
    }

    fun formatPercent(
        context: Context,
        fraction: Number,
        maximumFractionDigits: Int = 0
    ): String {
        return formatPercent(
            locale = currentLocale(context),
            fraction = fraction,
            maximumFractionDigits = maximumFractionDigits
        )
    }

    fun formatPercent(
        locale: Locale,
        fraction: Number,
        maximumFractionDigits: Int = 0
    ): String {
        require(maximumFractionDigits >= 0)

        return NumberFormat.getPercentInstance(locale).apply {
            this.maximumFractionDigits = maximumFractionDigits
        }.format(fraction)
    }
}
